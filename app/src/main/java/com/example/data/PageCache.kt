package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "page_cache", primaryKeys = ["pdfName", "pageNumber"])
data class PageCache(
    val pdfName: String,
    val pageNumber: Int,
    val language: String, // "English" or "Sinhala"
    val extractedText: String,
    val note: String? = null // General page note
)
