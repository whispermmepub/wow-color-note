package com.whispermmepub.wowcolornote.ui

import android.content.Context
import android.graphics.Color
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
    const val BG = 0xFFFFFDF7.toInt()
    const val SURFACE = 0xFFFFFFFF.toInt()
    const val TEXT = 0xFF25231F.toInt()
    const val MUTED = 0xFF756F67.toInt()
    const val ACCENT = 0xFFA46B2A.toInt()
    const val LINE = 0xFFE9E2D7.toInt()
    val NOTE_COLORS = intArrayOf(
        0xFFFFF4B8.toInt(), 0xFFFFD9C9.toInt(), 0xFFDCEFD8.toInt(),
        0xFFDDEBFA.toInt(), 0xFFE9DDF6.toInt(), 0xFFFFE4A8.toInt(), 0xFFF1EEE8.toInt()
    )
}
