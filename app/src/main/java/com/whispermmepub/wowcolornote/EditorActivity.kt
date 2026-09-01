package com.whispermmepub.wowcolornote

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.whispermmepub.wowcolornote.data.NoteDb
import com.whispermmepub.wowcolornote.font.FontManager
import com.whispermmepub.wowcolornote.model.Note
import com.whispermmepub.wowcolornote.ui.WoWPalette
import com.whispermmepub.wowcolornote.ui.dp
import java.util.concurrent.Executors

class EditorActivity : AppCompatActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var db: NoteDb
    private lateinit var titleEdit: EditText
    private lateinit var bodyEdit: EditText
    private lateinit var preview: TextView
    private lateinit var modeBtn: TextView
    private lateinit var pinBtn: TextView
    private lateinit var colorBtn: TextView
    private var note = Note()
    private var loading = true
    private var previewMode = false
    private var saveTask: Runnable? = null
    private val palette = WoWPalette.NOTE_COLORS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = NoteDb(this)
        setContentView(buildUi())
        val id = intent.getLongExtra("note_id", 0L)
        if (id == 0L) { loading = false; applyNote() } else io.execute { val n = db.get(id); main.post { note = n ?: Note(); loading = false; applyNote() } }
    }

    private fun buildUi(): LinearLayout {
        val tf = FontManager.typeface(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(WoWPalette.BG) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(4)) }
        fun btn(textValue: String, click: () -> Unit) = TextView(this).apply { text = textValue; textSize = 16f; setTextColor(WoWPalette.ACCENT); gravity = Gravity.CENTER; setPadding(dp(10), dp(9), dp(10), dp(9)); setOnClickListener { click() } }
        bar.addView(btn("‹") { saveNow(); finish() })
        pinBtn = btn("☆") { note = note.copy(pinned = !note.pinned); pinBtn.text = if (note.pinned) "★" else "☆"; scheduleSave() }
        colorBtn = btn("●") { val i = (palette.indexOf(note.color).takeIf { it >= 0 } ?: 0); note = note.copy(color = palette[(i + 1) % palette.size]); colorBtn.setTextColor(note.color); scheduleSave() }
        modeBtn = btn("Preview") { togglePreview() }
        val spacer = View(this)
        bar.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f)); bar.addView(pinBtn); bar.addView(colorBtn); bar.addView(modeBtn)

        titleEdit = EditText(this).apply { hint = "Title"; textSize = 23f; setTextColor(WoWPalette.TEXT); setHintTextColor(WoWPalette.MUTED); background = null; setPadding(dp(18), dp(4), dp(18), dp(4)); setSingleLine(true); typeface = Typeface.create(tf, Typeface.BOLD) }
        bodyEdit = EditText(this).apply {
            hint = "Write…"; gravity = Gravity.TOP or Gravity.START; textSize = 18f; setTextColor(WoWPalette.TEXT); setHintTextColor(WoWPalette.MUTED); background = null
            setPadding(dp(18), dp(8), dp(18), dp(24)); typeface = tf; setLineSpacing(0f, 1.18f); isVerticalScrollBarEnabled = true
        }
        preview = TextView(this).apply { visibility = View.GONE; textSize = 18f; setTextColor(WoWPalette.TEXT); setPadding(dp(18), dp(10), dp(18), dp(40)); typeface = tf; setLineSpacing(0f, 1.22f); setTextIsSelectable(true) }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(preview, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        root.addView(bar); root.addView(titleEdit); root.addView(bodyEdit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)); root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { if (!loading) scheduleSave() }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        }
        titleEdit.addTextChangedListener(watcher); bodyEdit.addTextChangedListener(watcher)
        return root
    }

    private fun applyNote() {
        loading = true
        titleEdit.setText(note.title); bodyEdit.setText(note.body); pinBtn.text = if (note.pinned) "★" else "☆"; colorBtn.setTextColor(note.color)
        bodyEdit.setSelection(0.coerceAtMost(bodyEdit.length()))
        loading = false
    }

    private fun togglePreview() {
        previewMode = !previewMode
        if (previewMode) {
            preview.text = bodyEdit.text.toString(); bodyEdit.visibility = View.GONE; preview.parent.let { (it as View).visibility = View.VISIBLE }; titleEdit.clearFocus(); modeBtn.text = "Edit"
        } else {
            (preview.parent as View).visibility = View.GONE; bodyEdit.visibility = View.VISIBLE; modeBtn.text = "Preview"
        }
    }

    private fun scheduleSave() {
        saveTask?.let(main::removeCallbacks)
        saveTask = Runnable { saveNow() }
        main.postDelayed(saveTask!!, 350)
    }

    private fun saveNow() {
        if (loading) return
        saveTask?.let(main::removeCallbacks)
        val snapshot = note.copy(title = titleEdit.text.toString(), body = bodyEdit.text.toString())
        note = snapshot
        io.execute {
            runCatching { db.save(snapshot) }.onSuccess { id -> if (snapshot.id == 0L) main.post { note = note.copy(id = id, createdAt = System.currentTimeMillis()) } }
        }
    }

    override fun onPause() { saveNow(); super.onPause() }
    override fun onBackPressed() { saveNow(); super.onBackPressed() }
    override fun onDestroy() { main.removeCallbacksAndMessages(null); io.shutdown(); db.close(); super.onDestroy() }
}
