package com.whispermmepub.wowcolornote.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun rounded(color: Int, radius: Float, strokeColor: Int? = null, strokeWidth: Int = 0): GradientDrawable = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
    if (strokeColor != null && strokeWidth > 0) setStroke(strokeWidth, strokeColor)
}

fun View.pad(all: Int) = setPadding(all, all, all, all)

object WoWPalette {
    // Premium dark navy family shared across WoW tools.
    const val BG = 0xFF07111F.toInt()
    const val SURFACE = 0xFF0D1A2B.toInt()
    const val CARD = 0xFF12233A.toInt()
    const val CARD_ALT = 0xFF172A45.toInt()
    const val TEXT = 0xFFF3F6FB.toInt()
    const val MUTED = 0xFFA8B4C7.toInt()
    const val ACCENT = 0xFF5EDBD6.toInt()
    const val GOLD = 0xFFE6C46A.toInt()
    const val LINE = 0xFF263B57.toInt()
    const val DANGER = 0xFFFF7D87.toInt()
    const val TODAY = 0xFF203B5D.toInt()

    val NOTE_COLORS = intArrayOf(
        0xFFE6C46A.toInt(),
        0xFF5EDBD6.toInt(),
        0xFF7DA4FF.toInt(),
        0xFF9A82FF.toInt(),
        0xFFFF8FA3.toInt(),
        0xFF7FD39A.toInt(),
        0xFFFFA75E.toInt()
    )
}
