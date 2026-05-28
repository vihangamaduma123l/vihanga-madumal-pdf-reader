package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pdfName: String,
    val pageNumber: Int,
    val note: String,
    val timestamp: Long = System.currentTimeMillis()
)
