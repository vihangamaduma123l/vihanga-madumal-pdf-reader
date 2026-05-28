        @file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.GeminiExtractionResult
import com.example.data.Bookmark
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.testTag
import com.example.ui.MessageSender
import com.example.ui.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibleReaderApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val currentScreen = viewModel.currentScreen
    val isDarkTheme = viewModel.darkThemeEnabled

    // Master Theme Provider
    MyApplicationTheme(darkTheme = isDarkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                when (screen) {
                    AppScreen.Agreement -> AgreementScreen(
                        onAgree = { viewModel.acceptAgreement() },
                        isDark = isDarkTheme,
                        onToggleTheme = { viewModel.darkThemeEnabled = !viewModel.darkThemeEnabled },
                        viewModel = viewModel
                    )
                    AppScreen.Main -> MainReaderScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun AgreementScreen(
    onAgree: () -> Unit,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current

    // Launcher for older permissions if needed, fallback to moving forward
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.announceToUser("Storage access granted.")
        } else {
            viewModel.announceToUser("Storage permission is not granted. You can still use the system file picker safely.")
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Vihanga Madumal PDF Reader",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            onToggleTheme()
                            viewModel.announceToUser("Theme toggled to ${if (!isDark) "Dark" else "Light"} high-contrast mode")
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .semantics {
                                contentDescription = "Toggle high-contrast theme. Currently ${if (isDark) "Dark" else "Light"} mode"
                            }
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Brand Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(60.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "High Contrast AI Reader",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "A specialized mobile solution designed mathematically to render, analyze, and read PDFs aloud seamlessly in English and Sinhala scripts.",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // Central Agreement and Creator Info
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "IMPORTANT NOTICE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "1. To read printed materials, this application converts PDF pages to visual layouts and optimizes translation securely through artificial intelligence.\n" +
                               "2. High-volume Text-to-Speech (TTS) synthesizer will guide you through English and Sinhala languages completely.\n" +
                               "3. Standard screen reader functions (TalkBack) are fully integrated to announce actions.",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "This app is created by Vihanga Madumal Rashmika.",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "Created by Vihanga Madumal Rashmika"
                            }
                    )
                }
            }

            // Permissions Actions & Consent Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Request Permissions Button (Optional supplementary accessibility)
                OutlinedButton(
                    onClick = {
                        try {
                            permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        } catch (e: Exception) {
                            viewModel.announceToUser("Permissions capability loaded.")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .semantics {
                            contentDescription = "Review and Grant Storage Permissions for old system compatibility"
                        },
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Shield, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Grant Sample Permissions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Explicit Agree and Continue Button
                Button(
                    onClick = onAgree,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .semantics {
                            contentDescription = "Agree with licence terms and enter application. Spreads creator's signature: This app is created by Vihanga Madumal Rashmika."
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        "AGREE & CONTINUE",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MainReaderScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val bookmarks by viewModel.bookmarksFlow.collectAsState(initial = emptyList())
    val isCurrentBookmarked by viewModel.isCurrentPageBookmarked.collectAsState(initial = false)

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.loadSelectedPdf(uri)
        }
    }

    var selectedTab by remember { mutableStateOf(0) } // 0: Reader, 1: Bookmarks, 2: Search, 3: Creator

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        bottomBar = {
            Column {
                Divider(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    thickness = 1.dp
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        viewModel.announceToUser("Reader tab selected.")
                    },
                    icon = { Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(28.dp)) },
                    label = { Text("Reader", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    modifier = Modifier.semantics {
                        contentDescription = "Reader screen, view page text visual OCR"
                    }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        viewModel.announceToUser("Bookmarks list tab selected.")
                    },
                    icon = { Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(28.dp)) },
                    label = { Text("Bookmarks", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    modifier = Modifier.semantics {
                        contentDescription = "Stored Bookmarks tab"
                    }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        viewModel.announceToUser("Search keywords tab selected.")
                    },
                    icon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(28.dp)) },
                    label = { Text("Search", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    modifier = Modifier.semantics {
                        contentDescription = "Search PDF texts tab"
                    }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = {
                        selectedTab = 3
                        viewModel.announceToUser("About developer contact tab selected.")
                    },
                    icon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(28.dp)) },
                    label = { Text("About", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    modifier = Modifier.semantics {
                        contentDescription = "Developer profile and WhatsApp link"
                    }
                )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ReaderTab(
                    viewModel = viewModel,
                    isBookmarked = isCurrentBookmarked,
                    onPickPdf = { pdfPickerLauncher.launch("application/pdf") }
                )
                1 -> BookmarksTab(
                    viewModel = viewModel,
                    bookmarksList = bookmarks
                )
                2 -> SearchTab(
                    viewModel = viewModel
                )
                3 -> AboutTab(
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun ReaderTab(
    viewModel: MainViewModel,
    isBookmarked: Boolean,
    onPickPdf: () -> Unit
) {
    val context = LocalContext.current
    val isLoading = viewModel.isLoadingAnalysis
    val pdfUri = viewModel.pdfUri
    val currentPageBitmap = viewModel.currentPageBitmap
    val zoom = viewModel.zoomLevel

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // PDF UPLOADER CONTROL
        Button(
            onClick = onPickPdf,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .semantics {
                    contentDescription = "Upload PDF file selector trigger button"
                },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.UploadFile,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (viewModel.pdfUri == null) "CHOOSE PDF DOCUMENT" else "CHOOSE DIFFERENT PDF",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
        }

        // Ask AI Button / Chat with Vihanga Madumal AI (Requirement 3)
        val hasPdf = viewModel.pdfUri != null
        Button(
            onClick = { viewModel.isChatOpen = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .testTag("ai_chat_button")
                .semantics {
                    contentDescription = if (hasPdf) "Ask AI with this PDF" else "Chat with Vihanga Madumal AI"
                },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (hasPdf) "Ask AI with this PDF" else "Chat with Vihanga Madumal AI",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }

        if (pdfUri == null) {
            // EMPTY STATE SCREEN
            Spacer(modifier = Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FileCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(60.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No PDF File Loaded",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Press the large button above to load a document.\nGemini AI will automatically do direct OCR and text extraction.",
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        } else {
            // FILE LOADED STATE
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "ACTIVE FILE: ${viewModel.pdfName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // PDF VIEWER ZOOMABLE Snapshot
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentPageBitmap != null) {
                        Image(
                            bitmap = currentPageBitmap.asImageBitmap(),
                            contentDescription = "Visual illustration layout of page ${viewModel.currentPageIndex + 1}",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = zoom,
                                    scaleY = zoom
                                )
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // CONTROLS: Next, Previous, Zoom In, Zoom Out
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Next / Prev Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.previousPage() },
                        enabled = viewModel.currentPageIndex > 0,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .semantics {
                                contentDescription = "Previous Page button. Takes you to page ${viewModel.currentPageIndex}"
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PREV PAGE", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Button(
                        onClick = { viewModel.nextPage() },
                        enabled = viewModel.currentPageIndex < viewModel.totalPages - 1,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .semantics {
                                contentDescription = "Next Page button. Takes you to page ${viewModel.currentPageIndex + 2}"
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("NEXT PAGE", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null)
                    }
                }

                // Zoom Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.zoomOut() },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .semantics {
                                contentDescription = "Zoom Out page illustration"
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ZoomOut, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ZOOM OUT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = { viewModel.zoomIn() },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .semantics {
                                contentDescription = "Zoom In page illustration"
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ZoomIn, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ZOOM IN", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // PAGE INDICATOR & ACCESSIBILITY READOUTS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Current index indicator
                Text(
                    text = "Page ${viewModel.currentPageIndex + 1} of ${viewModel.totalPages}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics {
                        contentDescription = "Currently on page ${viewModel.currentPageIndex + 1} of ${viewModel.totalPages}"
                    }
                )

                // Language Detector
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "DETECTED: ${viewModel.detectedLanguage.uppercase()}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics {
                            contentDescription = "Language detected on page is ${viewModel.detectedLanguage}"
                        }
                    )
                }
            }

            // BOOKMARK & ACTION ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Bookmarking
                Button(
                    onClick = { viewModel.toggleBookmarkCurrentPage() },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .semantics {
                            contentDescription = if (isBookmarked) "Page is currently bookmarked. Press to remove bookmark." else "Press to add bookmark for this page."
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (isBookmarked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.BookmarkAdded else Icons.Default.BookmarkAdd,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBookmarked) "BOOKMARKED" else "BOOKMARK",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // Copy Text
                Button(
                    onClick = { viewModel.copyCurrentText() },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .semantics {
                            contentDescription = "Copy page extracted text to system clipboard"
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("COPY TEXT", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // TTS CORE AUDIOPLAYBACK PANEL
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "SPEECH CONTROLS (TTS)",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (viewModel.isSpeaking || viewModel.isPaused) {
                            // Skip Backward
                            FilledIconButton(
                                onClick = { viewModel.rewindTts() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .semantics { contentDescription = "Rewind 10 seconds" },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Pause / Resume Button
                            Button(
                                onClick = {
                                    if (viewModel.isSpeaking) {
                                        viewModel.pauseSpeaking()
                                    } else {
                                        viewModel.resumeSpeaking()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(48.dp)
                                    .semantics {
                                        contentDescription = if (viewModel.isSpeaking) "Pause reading" else "Resume reading"
                                    },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (viewModel.isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (viewModel.isSpeaking) "PAUSE" else "RESUME",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                )
                            }

                            // Skip Forward
                            FilledIconButton(
                                onClick = { viewModel.fastForwardTts() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .semantics { contentDescription = "Fast forward 10 seconds" },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Stop Button
                            FilledIconButton(
                                onClick = { viewModel.stopSpeaking() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .semantics { contentDescription = "Stop reading" },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.Red,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            // Primary Speak PDF button
                            Button(
                                onClick = { viewModel.startSpeakingCurrentPage() },
                                modifier = Modifier
                                    .weight(2f)
                                    .height(60.dp)
                                    .semantics {
                                        contentDescription = "Speak this PDF page button. Click to start voice reading aloud."
                                    },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SPEAK THIS PDF",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        // Speech Speed Adjuster
                        Button(
                            onClick = { viewModel.toggleSpeechRate() },
                            modifier = Modifier
                                .weight(1f)
                                .height(if (viewModel.isSpeaking || viewModel.isPaused) 48.dp else 60.dp)
                                .semantics {
                                    val currentRateLabel = when (viewModel.speechRate) {
                                        1.5f -> "Fast speed"
                                        0.75f -> "Slow speed"
                                        else -> "Normal speed"
                                    }
                                    contentDescription = "Adjust TTS speech rate. Currently set to $currentRateLabel."
                                },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = when (viewModel.speechRate) {
                                    1.5f -> "⚡ FAST"
                                    0.75f -> "🐢 SLOW"
                                    else -> "▶️ NORMAL"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // TEXT DISPLAY PANEL
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EXTRACTED PLAYBACK TEXT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { heading() }
                        )

                        // Share current text button
                        IconButton(
                            onClick = { viewModel.shareCurrentText() },
                            modifier = Modifier.semantics {
                                contentDescription = "Share extracted page text options"
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "AI analyzing layout...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else {
                        SelectionContainer {
                            Text(
                                text = if (viewModel.extractedText.isNotEmpty()) viewModel.extractedText else "No text extracted. Select choosing PDF or trigger layout read.",
                                fontSize = 18.sp, // Large and beautiful text size for weak sights!
                                lineHeight = 26.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        contentDescription = "Extracted material text: " + viewModel.extractedText
                                    }
                            )
                        }
                    }
                }
            }
        }
    }

    // AI CHAT DIALOG OVERLAY (Requirement 3)
    if (viewModel.isChatOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.isChatOpen = false },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 550.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { viewModel.isChatOpen = false }
                ) {
                    Text("CLOSE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "AI Assistant Icon",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (viewModel.pdfUri != null) "Ask AI: ${viewModel.pdfName}" else "Vihanga Madumal AI",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Divider()
                    
                    // Messages LazyColumn
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        if (viewModel.chatMessages.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (viewModel.pdfUri != null) 
                                        "Ask me anything about the extracted text of the current PDF page!" 
                                        else "Hello! Ask me any questions, or say 'What is your name?' to meet me.",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                            LaunchedEffect(viewModel.chatMessages.size) {
                                if (viewModel.chatMessages.isNotEmpty()) {
                                    listState.animateScrollToItem(viewModel.chatMessages.size - 1)
                                }
                            }
                            androidx.compose.foundation.lazy.LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(viewModel.chatMessages) { message ->
                                    val isUser = message.sender == MessageSender.User
                                    val alignment = if (isUser) Alignment.End else Alignment.Start
                                    val containerColor = if (isUser) 
                                        MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    val contentColor = if (isUser) 
                                        MaterialTheme.colorScheme.onPrimary 
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = alignment
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = containerColor,
                                                    shape = RoundedCornerShape(
                                                        topStart = 12.dp,
                                                        topEnd = 12.dp,
                                                        bottomStart = if (isUser) 12.dp else 0.dp,
                                                        bottomEnd = if (isUser) 0.dp else 12.dp
                                                    )
                                                )
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                                .widthIn(max = 240.dp)
                                        ) {
                                            Text(
                                                text = message.text,
                                                color = contentColor,
                                                fontSize = 15.sp,
                                                lineHeight = 20.sp
                                            )
                                        }
                                        Text(
                                            text = if (isUser) "You" else "AI",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                
                                if (viewModel.isAiThinking) {
                                    item {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                "Vihanga Madumal AI is thinking...",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Send bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = viewModel.chatInputText,
                            onValueChange = { viewModel.chatInputText = it },
                            modifier = Modifier
                                .weight(1f)
                                .semantics {
                                    contentDescription = "Type your query for AI"
                                },
                            placeholder = { Text("Ask here...", fontSize = 14.sp) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            maxLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        IconButton(
                            onClick = {
                                if (viewModel.chatInputText.trim().isNotEmpty() && !viewModel.isAiThinking) {
                                    viewModel.sendChatMessage(viewModel.chatInputText)
                                }
                            },
                            enabled = viewModel.chatInputText.trim().isNotEmpty() && !viewModel.isAiThinking,
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = if (viewModel.chatInputText.trim().isNotEmpty() && !viewModel.isAiThinking) 
                                        MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .semantics {
                                    contentDescription = "Send message to AI button"
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = if (viewModel.chatInputText.trim().isNotEmpty() && !viewModel.isAiThinking) 
                                    MaterialTheme.colorScheme.onPrimary 
                                    else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun BookmarksTab(
    viewModel: MainViewModel,
    bookmarksList: List<Bookmark>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "STORED PDF BOOKMARKS",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() }
        )

        if (bookmarksList.isEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.Bookmarks,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No saved bookmarks in this document",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Text(
                text = "Bookmark pages in the Reader tab to list them here.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.weight(1.5f))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(bookmarksList) { bookmark ->
                    Card(
                        onClick = {
                            viewModel.navigateToPage(bookmark.pageNumber)
                            viewModel.announceToUser("Navigating to Page ${bookmark.pageNumber} from bookmark.")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                            .semantics {
                                contentDescription = "Bookmark for page ${bookmark.pageNumber} of PDF file: ${bookmark.pdfName}. Double click to show page."
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PAGE ${bookmark.pageNumber}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Source: ${bookmark.pdfName}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ArrowForwardIos,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchTab(
    viewModel: MainViewModel
) {
    val searchResults = viewModel.searchResults
    val query = viewModel.searchQuery

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "SEARCH EXTRACTED LAYOUTS",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() }
        )

        // Accessible Form Search Bar
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Search text input field. Enter keyword here."
                },
            placeholder = { Text("Enter term (e.g., invoice, Sinhala text...)") },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Button(
            onClick = { viewModel.executeSearch() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics {
                    contentDescription = "Execute keyword search button"
                },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("EXECUTE SEARCH", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), thickness = 2.dp)

        if (searchResults.isEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "No matching search matches in index",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.weight(1.5f))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(searchResults) { result ->
                    Card(
                        onClick = {
                            viewModel.navigateToPage(result.pageNumber)
                            viewModel.announceToUser("Jumping to Page ${result.pageNumber}")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                            .semantics {
                                contentDescription = "Page ${result.pageNumber} holds match. Text snippet is ${result.extractedText.take(100)}. Double click to jump there."
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "PAGE ${result.pageNumber}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = result.extractedText,
                                fontSize = 14.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutTab(
    viewModel: MainViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Identity Header
        Box(
            modifier = Modifier
                .size(90.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp)
                )
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(50.dp)
            )
        }

        Text(
            text = "Vihanga Madumal PDF Reader",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Version 1.0.0 (High Contrast AI-Powered)",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), thickness = 2.dp)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "CREATOR BIO",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )

                Text(
                    text = "This app is created by Vihanga Madumal Rashmika.",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Dedicated to making documents and text-based layouts fully accessible to visually impaired and blind users in Sri Lanka and globally.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // CONTACT DEVELOPER THROUGH WHATSAPP
        Button(
            onClick = { viewModel.contactDeveloper() },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .semantics {
                    contentDescription = "Contact Developer button. Opens WhatsApp to chat with Vihanga Madumal"
                },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF25D366), // Standard WhatsApp green color for high visibility!
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "CONTACT DEVELOPER",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}
