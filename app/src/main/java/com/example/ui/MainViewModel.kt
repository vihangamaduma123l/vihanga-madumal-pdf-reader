package com.example.ui

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiExtractionResult
import com.example.api.GeminiRepository
import com.example.data.AppDatabase
import com.example.data.Bookmark
import com.example.data.PageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

enum class AppScreen {
    Agreement,
    Main
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageSender {
    User,
    AI
}

class MainViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val context = application.applicationContext
    private val database = AppDatabase.getDatabase(context)
    private val bookmarkDao = database.bookmarkDao()
    private val pageCacheDao = database.pageCacheDao()
    private val geminiRepository = GeminiRepository()

    // --- State Variables ---
    var currentScreen by mutableStateOf(AppScreen.Agreement)
        private set

    var darkThemeEnabled by mutableStateOf(true) // Default to high-contrast Dark theme for visually impaired efficiency

    // Document States
    var pdfUri by mutableStateOf<Uri?>(null)
        private set
    var pdfName by mutableStateOf("")
        private set
    var totalPages by mutableStateOf(0)
        private set
    var currentPageIndex by mutableStateOf(0)
        private set
    var currentPageBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var zoomLevel by mutableStateOf(1f)
        private set

    // Text & AI States
    var extractedText by mutableStateOf("")
        private set
    var detectedLanguage by mutableStateOf("English")
        private set
    var isLoadingAnalysis by mutableStateOf(false)
        private set

    // TTS Control States
    var isTtsInitialized by mutableStateOf(false)
        private set
    var isSpeaking by mutableStateOf(false)
        private set
    var isPaused by mutableStateOf(false)
        private set
    var currentSpeakCharIndex by mutableStateOf(0)
        private set
    private var baseTtsOffset = 0
    var speechRate by mutableStateOf(1.0f) // 0.75f (Slow), 1.0f (Normal), 1.5f (Fast)

    // Chat States
    var isChatOpen by mutableStateOf(false)
    val chatMessages = androidx.compose.runtime.mutableStateListOf<ChatMessage>()
    var chatInputText by mutableStateOf("")
    var isAiThinking by mutableStateOf(false)
        private set

    // Search Query
    var searchQuery by mutableStateOf("")
    var searchResults = mutableStateListOf<PageCache>()

    // Bookmarks List
    val bookmarksFlow: Flow<List<Bookmark>> = pdfUri?.let {
        bookmarkDao.getBookmarksForPdf(it.lastPathSegment ?: "unknown")
    } ?: bookmarkDao.getAllBookmarks()

    val isCurrentPageBookmarked: Flow<Boolean> = pdfUri?.let { uri ->
        bookmarkDao.isBookmarked(uri.lastPathSegment ?: "unknown", currentPageIndex + 1)
    } ?: flowOf(false)

    // Internal Pdf Renderer refs
    private var pdfRenderer: PdfRenderer? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var ttsEngine: TextToSpeech? = null

    init {
        // Load preferences for onboarding agreement
        val prefs = context.getSharedPreferences("kih_pdf_reader_prefs", Context.MODE_PRIVATE)
        val hasAgreed = prefs.getBoolean("has_agreed_licence", false)
        if (hasAgreed) {
            currentScreen = AppScreen.Main
        }

        // Initialize Text To Speech
        ttsEngine = TextToSpeech(context, this)
    }

    // --- Agreement Onboarding Logic ---
    fun acceptAgreement() {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("kih_pdf_reader_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("has_agreed_licence", true).apply()
            currentScreen = AppScreen.Main
            announceToUser("Agreement accepted. Welcome to Vihanga Madumal PDF Reader.")
        }
    }

    // --- Accessbility Voice feedback & Screen Reader Announcement ---
    fun announceToUser(message: String) {
        // 1. Accessibility Event for active TalkBack integration
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (accessibilityManager.isEnabled) {
            val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT).apply {
                className = MainViewModel::class.java.name
                packageName = context.packageName
                text.add(message)
            }
            accessibilityManager.sendAccessibilityEvent(event)
        }

        // 2. Supplemetary spoken TTS announcement if safe
        if (isTtsInitialized && !isSpeaking) {
            ttsEngine?.speak(message, TextToSpeech.QUEUE_ADD, null, "announcement_id")
        }
    }

    // --- TTS Engine Callbacks ---
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsEngine?.language = Locale.US
            isTtsInitialized = true
            setupTtsProgressListener()
            Log.d("MainViewModel", "TTS initialized successfully")
        } else {
            Log.e("MainViewModel", "TTS Engine failed to initialize")
        }
    }

    private fun setupTtsProgressListener() {
        ttsEngine?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                viewModelScope.launch(Dispatchers.Main) {
                    isSpeaking = true
                }
            }

            override fun onDone(utteranceId: String?) {
                viewModelScope.launch(Dispatchers.Main) {
                    isSpeaking = false
                    isPaused = false
                    currentSpeakCharIndex = 0
                    baseTtsOffset = 0
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                viewModelScope.launch(Dispatchers.Main) {
                    isSpeaking = false
                    isPaused = false
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                viewModelScope.launch(Dispatchers.Main) {
                    currentSpeakCharIndex = baseTtsOffset + start
                }
            }
        })
    }

    fun startSpeakingCurrentPage() {
        if (!isTtsInitialized) {
            announceToUser("Speech engine is not ready yet.")
            return
        }
        if (extractedText.isEmpty() || extractedText.startsWith("Error")) {
            announceToUser("No readable text found for this page. Please run visual analysis first.")
            return
        }

        currentSpeakCharIndex = 0
        baseTtsOffset = 0
        isPaused = false
        isSpeaking = true

        // Configure speech rate
        ttsEngine?.setSpeechRate(speechRate)

        // Auto-select TTS locale based on detected language
        if (detectedLanguage.equals("Sinhala", ignoreCase = true)) {
            val sinhalaLocale = Locale("si", "LK")
            val result = ttsEngine?.setLanguage(sinhalaLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsEngine?.language = Locale.getDefault()
                Log.w("MainViewModel", "Sinhala locale missing or not supported on this synthesizer.")
            }
        } else {
            ttsEngine?.language = Locale.US
        }

        announceToUser("Starting continuous reading.")
        
        val utteranceId = "pdf_reader_page_${currentPageIndex}"
        ttsEngine?.speak(extractedText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun pauseSpeaking() {
        if (isSpeaking && !isPaused) {
            ttsEngine?.stop()
            isSpeaking = false
            isPaused = true
            announceToUser("Paused.")
        }
    }

    fun resumeSpeaking() {
        if (!isTtsInitialized) return
        if (extractedText.isEmpty() || extractedText.startsWith("Error")) return

        isPaused = false
        isSpeaking = true

        if (currentSpeakCharIndex >= extractedText.length) {
            currentSpeakCharIndex = 0
        }
        baseTtsOffset = currentSpeakCharIndex
        announceToUser("Resuming reading.")

        val remainingText = extractedText.substring(baseTtsOffset)
        ttsEngine?.setSpeechRate(speechRate)

        if (detectedLanguage.equals("Sinhala", ignoreCase = true)) {
            val sinhalaLocale = Locale("si", "LK")
            ttsEngine?.setLanguage(sinhalaLocale)
        } else {
            ttsEngine?.language = Locale.US
        }

        val utteranceId = "pdf_reader_page_${currentPageIndex}"
        ttsEngine?.speak(remainingText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun fastForwardTts() {
        if (extractedText.isEmpty() || extractedText.startsWith("Error")) return

        val charsToSkip = (10 * 18 * speechRate).toInt()
        var target = currentSpeakCharIndex + charsToSkip
        if (target > extractedText.length) {
            target = extractedText.length
        }

        currentSpeakCharIndex = target

        if (isSpeaking) {
            baseTtsOffset = currentSpeakCharIndex
            val remaining = extractedText.substring(baseTtsOffset)
            ttsEngine?.setSpeechRate(speechRate)

            if (detectedLanguage.equals("Sinhala", ignoreCase = true)) {
                ttsEngine?.setLanguage(Locale("si", "LK"))
            } else {
                ttsEngine?.language = Locale.US
            }
            val utteranceId = "pdf_reader_page_${currentPageIndex}"
            ttsEngine?.speak(remaining, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            announceToUser("Fast forward 10 seconds.")
        } else {
            announceToUser("Fast forward 10 seconds. Press resume when ready.")
        }
    }

    fun rewindTts() {
        if (extractedText.isEmpty() || extractedText.startsWith("Error")) return

        val charsToSkip = (10 * 18 * speechRate).toInt()
        var target = currentSpeakCharIndex - charsToSkip
        if (target < 0) {
            target = 0
        }

        currentSpeakCharIndex = target

        if (isSpeaking) {
            baseTtsOffset = currentSpeakCharIndex
            val remaining = extractedText.substring(baseTtsOffset)
            ttsEngine?.setSpeechRate(speechRate)

            if (detectedLanguage.equals("Sinhala", ignoreCase = true)) {
                ttsEngine?.setLanguage(Locale("si", "LK"))
            } else {
                ttsEngine?.language = Locale.US
            }
            val utteranceId = "pdf_reader_page_${currentPageIndex}"
            ttsEngine?.speak(remaining, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            announceToUser("Rewind 10 seconds.")
        } else {
            announceToUser("Rewind 10 seconds. Press resume when ready.")
        }
    }

    fun stopSpeaking() {
        if (isSpeaking || isPaused) {
            ttsEngine?.stop()
            isSpeaking = false
            isPaused = false
            currentSpeakCharIndex = 0
            baseTtsOffset = 0
            announceToUser("Reading stopped.")
        }
    }

    fun toggleSpeechRate() {
        speechRate = when (speechRate) {
            1.0f -> 1.5f
            1.5f -> 0.75f
            else -> 1.0f
        }
        val rateText = when (speechRate) {
            1.5f -> "Fast"
            0.75f -> "Slow"
            else -> "Normal"
        }
        announceToUser("Reading speed set to $rateText.")
        if (isSpeaking) {
            startSpeakingCurrentPage() // reload TTS with new rate
        }
    }

    // --- PDF Processing ---
    fun loadSelectedPdf(uri: Uri) {
        viewModelScope.launch {
            try {
                stopSpeaking()
                pdfUri = uri
                zoomLevel = 1.0f

                // Query Display Name
                pdfName = getFileNameFromUri(uri)
                announceToUser("File selected successfully: $pdfName.")

                // Initialize PdfRenderer in background
                withContext(Dispatchers.IO) {
                    closeCurrentRenderer()

                    // Copy URI content to local Cache file to generate Descriptor
                    val tempFile = File(context.cacheDir, "loaded_document.pdf")
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        FileOutputStream(tempFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    parcelFileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    parcelFileDescriptor?.let {
                        pdfRenderer = PdfRenderer(it)
                        totalPages = pdfRenderer?.pageCount ?: 0
                    }
                }

                if (totalPages > 0) {
                    currentPageIndex = 0
                    renderAndAnalyzePage(0)
                } else {
                    announceToUser("Error parsing selected file. It may be corrupted or empty.")
                }

            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to render PDF", e)
                announceToUser("Failed to load PDF file: ${e.localizedMessage}")
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "Document.pdf"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            val lastSegment = uri.lastPathSegment
            if (lastSegment != null) name = lastSegment
        }
        return name
    }

    fun nextPage() {
        if (currentPageIndex < totalPages - 1) {
            currentPageIndex++
            renderAndAnalyzePage(currentPageIndex)
        } else {
            announceToUser("Reached the last page.")
        }
    }

    fun previousPage() {
        if (currentPageIndex > 0) {
            currentPageIndex--
            renderAndAnalyzePage(currentPageIndex)
        } else {
            announceToUser("Reached the first page.")
        }
    }

    fun zoomIn() {
        if (zoomLevel < 3.0f) {
            zoomLevel += 0.25f
            announceToUser("Zoom increased to ${ (zoomLevel * 100).toInt() } percent.")
        } else {
            announceToUser("Maximum zoom reach.")
        }
    }

    fun zoomOut() {
        if (zoomLevel > 0.75f) {
            zoomLevel -= 0.25f
            announceToUser("Zoom decreased to ${ (zoomLevel * 100).toInt() } percent.")
        } else {
            announceToUser("Minimum zoom reach.")
        }
    }

    // --- Page OCR Render & Analyze Flow ---
    private fun renderAndAnalyzePage(pageIndex: Int) {
        viewModelScope.launch {
            try {
                stopSpeaking()
                extractedText = ""
                isLoadingAnalysis = true
                announceToUser("Opening Page ${pageIndex + 1} of $totalPages.")

                // Render Bitmap
                val renderedBitmap = withContext(Dispatchers.IO) {
                    val renderer = pdfRenderer ?: return@withContext null
                    if (pageIndex < 0 || pageIndex >= totalPages) return@withContext null

                    val page = renderer.openPage(pageIndex)
                    
                    // Create bitmap targeting around 1080px width for efficient text visibility
                    val targetWidth = 1080
                    val aspectRatio = page.height.toFloat() / page.width.toFloat()
                    val targetHeight = (targetWidth * aspectRatio).toInt()

                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmap
                }

                currentPageBitmap = renderedBitmap

                if (renderedBitmap != null) {
                    val currentPdfName = pdfName
                    val pageNumber = pageIndex + 1

                    // Check Room Cache database first to prevent duplicate API calls
                    val cachedPage = withContext(Dispatchers.IO) {
                        pageCacheDao.getPageCache(currentPdfName, pageNumber)
                    }

                    if (cachedPage != null) {
                        val cleaned = geminiRepository.cleanOcrResult(cachedPage.extractedText, cachedPage.language)
                        extractedText = cleaned.extractedText
                        detectedLanguage = cleaned.language
                        isLoadingAnalysis = false
                        announceToUser("Analysis loaded from local memory. Language is $detectedLanguage. Text is ready.")
                    } else {
                        // Call Gemini 3.5 Flash Visual OCR API
                        announceToUser("Running high precision artificial intelligence layout read. Please hold.")
                        val result = geminiRepository.extractTextAndLanguageFromPage(renderedBitmap)
                        
                        extractedText = result.extractedText
                        detectedLanguage = result.language
                        isLoadingAnalysis = false

                        // Persist to database cache
                        withContext(Dispatchers.IO) {
                            pageCacheDao.insertPageCache(
                                PageCache(
                                    pdfName = currentPdfName,
                                    pageNumber = pageNumber,
                                    language = result.language,
                                    extractedText = result.extractedText
                                )
                            )
                        }
                        announceToUser("Analysis complete. Language identified as $detectedLanguage. Reading is ready.")
                    }
                } else {
                    isLoadingAnalysis = false
                    extractedText = "Error: Failed to obtain a view snapshot for this document page."
                    announceToUser("Failed to render page layout.")
                }

            } catch (e: Exception) {
                Log.e("MainViewModel", "Error analyzing page", e)
                isLoadingAnalysis = false
                extractedText = "Error analyzing page: ${e.localizedMessage}"
                announceToUser("An error occurred during page analysis. Please try again.")
            }
        }
    }

    // --- Bookmarks and Highlights ---
    fun toggleBookmarkCurrentPage() {
        val currentPdf = pdfName
        if (currentPdf.isEmpty()) {
            announceToUser("No active PDF loaded to bookmark.")
            return
        }

        viewModelScope.launch {
            val pageNum = currentPageIndex + 1
            val isBookmarkedCurrently = withContext(Dispatchers.IO) {
                bookmarkDao.isBookmarked(currentPdf, pageNum).first()
            }

            if (isBookmarkedCurrently) {
                withContext(Dispatchers.IO) {
                    bookmarkDao.removeBookmark(currentPdf, pageNum)
                }
                announceToUser("Bookmark removed for page $pageNum.")
            } else {
                val bookmark = Bookmark(
                    pdfName = currentPdf,
                    pageNumber = pageNum,
                    note = "Page $pageNum Bookmark"
                )
                withContext(Dispatchers.IO) {
                    bookmarkDao.insertBookmark(bookmark)
                }
                announceToUser("Bookmark added successfully for page $pageNum.")
            }
        }
    }

    fun navigateToPage(pageNum: Int) {
        if (pageNum in 1..totalPages) {
            currentPageIndex = pageNum - 1
            renderAndAnalyzePage(currentPageIndex)
        } else {
            announceToUser("Invalid target page number $pageNum.")
        }
    }

    // --- Search Logic ---
    fun executeSearch() {
        val currentPdf = pdfName
        if (currentPdf.isEmpty()) {
            announceToUser("Please load a PDF document before searching.")
            return
        }
        if (searchQuery.trim().isEmpty()) {
            announceToUser("Please write a term to search.")
            return
        }

        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                pageCacheDao.searchPdfText(currentPdf, searchQuery.trim())
            }

            searchResults.clear()
            searchResults.addAll(results)

            if (results.isNotEmpty()) {
                announceToUser("Found ${results.size} matches for term: $searchQuery. Check matching pages list.")
            } else {
                announceToUser("No matches found for term: $searchQuery. Remember to analyze pages first, as search works over scanned pages.")
            }
        }
    }

    // --- Copy and Share Utility ---
    fun copyCurrentText() {
        if (extractedText.isEmpty() || extractedText.startsWith("Error")) {
            announceToUser("No content is available to copy.")
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = android.content.ClipData.newPlainText("Vihanga PDF Extract", extractedText)
        clipboard.setPrimaryClip(clip)
        announceToUser("Page text copied to clipboard. Text length is ${extractedText.length} characters.")
    }

    fun shareCurrentText() {
        if (extractedText.isEmpty() || extractedText.startsWith("Error")) {
            announceToUser("No text content is available to share.")
            return
        }
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, extractedText)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Extracted Text").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(shareIntent)
        announceToUser("Sharing options panel opened.")
    }

    // --- Contact Developer Logic ---
    fun contactDeveloper() {
        val whatsAppUrl = "https://wa.me//+94789174664"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(whatsAppUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            announceToUser("Opening WhatsApp to contact Vihanga Madumal.")
        } catch (e: Exception) {
            announceToUser("WhatsApp could not be opened. Opening browser link instead.")
        }
    }

    // --- Chat with AI Logic ---
    fun sendChatMessage(messageText: String) {
        if (messageText.trim().isEmpty()) return

        val userMsg = ChatMessage(sender = MessageSender.User, text = messageText)
        chatMessages.add(userMsg)
        chatInputText = ""
        isAiThinking = true
        announceToUser("Sending question to Vihanga Madumal AI.")

        viewModelScope.launch {
            try {
                // Check intercept rule first for absolute guarantee
                val normalized = messageText.trim().lowercase().removeSuffix("?").removeSuffix(".")
                if (normalized == "what is your name" || normalized.contains("what is your name")) {
                    val aiReply = "I am Vihanga Madumal AI"
                    chatMessages.add(ChatMessage(sender = MessageSender.AI, text = aiReply))
                    announceToUser("AI reply received: $aiReply")
                    isAiThinking = false
                    return@launch
                }

                val hasPdf = pdfUri != null
                val pdfContext = if (hasPdf && extractedText.isNotEmpty()) {
                    "Below is the extracted context from the current page of the PDF:\n--- START EXTRACT ---\n$extractedText\n--- END EXTRACT ---\n\nUse this context to answer the user's question. If the user's question is general, you can answer from general knowledge."
                } else {
                    ""
                }

                val prompt = if (pdfContext.isNotEmpty()) {
                    "$pdfContext\n\nUser Question: $messageText animate and reply clearly."
                } else {
                    messageText
                }

                val aiResult = geminiRepository.askAiGeneralOrWithContext(prompt)
                chatMessages.add(ChatMessage(sender = MessageSender.AI, text = aiResult))
                announceToUser("AI reply received.")
            } catch (e: Exception) {
                chatMessages.add(ChatMessage(sender = MessageSender.AI, text = "Sorry, I could not process your query at this moment: ${e.localizedMessage}"))
                announceToUser("AI failed to reply.")
            } finally {
                isAiThinking = false
            }
        }
    }

    // --- Cleanup ---
    private fun closeCurrentRenderer() {
        try {
            pdfRenderer?.close()
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error closing renderer", e)
        }
        pdfRenderer = null

        try {
            parcelFileDescriptor?.close()
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error closing descriptor", e)
        }
        parcelFileDescriptor = null
    }

    override fun onCleared() {
        super.onCleared()
        closeCurrentRenderer()
        ttsEngine?.stop()
        ttsEngine?.shutdown()
    }
}

// Custom simple list extension for Compose State management
fun <T> mutableStateListOf() = androidx.compose.runtime.mutableStateListOf<T>()
