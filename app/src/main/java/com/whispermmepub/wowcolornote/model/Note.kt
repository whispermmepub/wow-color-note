package com.whispermmepub.wowcolornote.model

data class Note(
    val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val color: Int = 0xFFFFF4B8.toInt(),
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
