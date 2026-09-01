package com.whispermmepub.wowcolornote.model

data class Note(
    val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val color: Int = 0xFF5EDBD6.toInt(),
    val pinned: Boolean = false,
    val noteType: String = "text",
    val archived: Boolean = false,
    val locked: Boolean = false,
    val calendarDate: String = "",
    val reminderAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
