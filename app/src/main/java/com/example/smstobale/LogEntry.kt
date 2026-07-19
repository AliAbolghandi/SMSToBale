package com.example.smstobale

data class LogEntry(
    val id: Long = 0,
    val timestamp: Long,
    val sender: String,
    val message: String,
    val status: String,
    val detail: String
)
