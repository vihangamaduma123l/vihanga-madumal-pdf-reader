package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PageCacheDao {
    @Query("SELECT * FROM page_cache WHERE pdfName = :pdfName AND pageNumber = :pageNumber LIMIT 1")
    suspend fun getPageCache(pdfName: String, pageNumber: Int): PageCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPageCache(pageCache: PageCache)

    @Query("SELECT * FROM page_cache WHERE pdfName = :pdfName")
    fun getPagesForPdf(pdfName: String): Flow<List<PageCache>>

    @Query("SELECT * FROM page_cache WHERE pdfName = :pdfName AND extractedText LIKE '%' || :query || '%'")
    suspend fun searchPdfText(pdfName: String, query: String): List<PageCache>

    @Query("DELETE FROM page_cache")
    suspend fun clearAllCache()
}
