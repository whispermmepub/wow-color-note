package com.whispermmepub.wowcolornote.font

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import java.io.File

object FontManager {
    private const val PREF = "wow_note_prefs"
    private const val KEY_FONT = "custom_font"

    fun import(context: Context, uri: Uri): Boolean = runCatching {
        val dir = File(context.filesDir, "fonts").apply { mkdirs() }
        val target = File(dir, "user_font")
        context.contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
        Typeface.createFromFile(target)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_FONT, target.absolutePath).apply()
        true
    }.getOrDefault(false)

    fun typeface(context: Context): Typeface {
        val path = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_FONT, null)
        return if (path != null) runCatching { Typeface.createFromFile(path) }.getOrDefault(Typeface.DEFAULT) else Typeface.DEFAULT
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY_FONT).apply()
    }
}
