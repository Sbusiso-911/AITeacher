package com.playstudio.aiteacher

data class EmailMessage(
    val subject: String,
    val body: String,
    val from: String?, // Sender's address
    val to: List<String>? // Recipient addresses (can be null if not extracted)
)