package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: Bookmark)

    @Query("SELECT * FROM bookmarks WHERE pdfName = :pdfName ORDER BY pageNumber ASC")
    fun getBookmarksForPdf(pdfName: String): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<Bookmark>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE pdfName = :pdfName AND pageNumber = :pageNumber LIMIT 1)")
    fun isBookmarked(pdfName: String, pageNumber: Int): Flow<Boolean>

    @Query("DELETE FROM bookmarks WHERE pdfName = :pdfName AND pageNumber = :pageNumber")
    suspend fun removeBookmark(pdfName: String, pageNumber: Int)
}
