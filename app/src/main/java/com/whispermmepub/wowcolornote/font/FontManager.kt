package com.whispermmepub.wowcolornote.font

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import java.io.File

object FontManager {
    private const val PREF = "wow_note_prefs"
    private const val KEY_FONT = "custom_font"

    fun import(context: Context, uri: Uri): Boolean = runCatching {
        val dir = File(context.filesDir, "fonts").apply { mkdirs() }
        val target = File(dir, "user_font")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        Typeface.createFromFile(target)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_FONT, target.absolutePath).apply()
        true
    }.getOrDefault(false)

    fun typeface(context: Context): Typeface {
        val path = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_FONT, null)
        return if (path != null) {
            runCatching { Typeface.createFromFile(path) }.getOrDefault(Typeface.DEFAULT)
        } else Typeface.DEFAULT
    }

    /**
     * Prevent tofu/missing-glyph boxes when a user font does not contain the
     * Myanmar characters present in the note. The whole view falls back to
     * the system font only when the selected font cannot render the text.
     */
    fun typefaceForText(context: Context, text: CharSequence): Typeface {
        val selected = typeface(context)
        if (selected == Typeface.DEFAULT || text.isEmpty()) return selected
        val paint = Paint().apply { typeface = selected; textSize = 32f }
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            if (!Character.isWhitespace(cp) && !Character.isISOControl(cp)) {
                val sample = String(Character.toChars(cp))
                if (!paint.hasGlyph(sample)) return Typeface.DEFAULT
            }
            i += Character.charCount(cp)
        }
        return selected
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY_FONT).apply()
    }
}
