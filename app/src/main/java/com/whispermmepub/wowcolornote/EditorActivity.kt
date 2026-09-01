package com.whispermmepub.wowcolornote

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.whispermmepub.wowcolornote.data.NoteDb
import com.whispermmepub.wowcolornote.font.FontManager
import com.whispermmepub.wowcolornote.model.Note
import com.whispermmepub.wowcolornote.ui.WoWPalette
import com.whispermmepub.wowcolornote.ui.dp
import com.whispermmepub.wowcolornote.ui.rounded
import java.util.concurrent.Executors

class EditorActivity : AppCompatActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var db: NoteDb
    private lateinit var titleEdit: EditText
    private lateinit var bodyEdit: EditText
    private lateinit var preview: TextView
    private lateinit var previewScroll: ScrollView
    private lateinit var modeBtn: TextView
    private lateinit var pinBtn: TextView
    private lateinit var colorBtn: TextView
    private lateinit var sizeLabel: TextView
    private lateinit var lineLabel: TextView

    private var note = Note()
    private var loading = true
    private var previewMode = false
    private var saveTask: Runnable? = null
    private val palette = WoWPalette.NOTE_COLORS

    private val prefs by lazy { getSharedPreferences("wow_note_reader", MODE_PRIVATE) }
    private var readerSize = 18f
    private var lineHeight = 1.24f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = WoWPalette.BG
        window.navigationBarColor = WoWPalette.BG
        readerSize = prefs.getFloat("reader_size", 18f).coerceIn(12f, 34f)
        lineHeight = prefs.getFloat("line_height", 1.24f).coerceIn(1.0f, 2.0f)
        db = NoteDb(this)
        setContentView(buildUi())
        applyReadingSettings()

        val id = intent.getLongExtra("note_id", 0L)
        if (id == 0L) {
            loading = false
            applyNote()
        } else io.execute {
            val n = db.get(id)
            main.post {
                note = n ?: Note()
                loading = false
                applyNote()
            }
        }
    }

    private fun buildUi(): LinearLayout {
        val tf = FontManager.typeface(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(WoWPalette.BG)
            setPadding(dp(10), dp(8), dp(10), dp(10))
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(5), dp(6), dp(5))
            background = rounded(WoWPalette.SURFACE, dp(18).toFloat(), WoWPalette.LINE, dp(1))
        }
        fun btn(textValue: String, click: () -> Unit) = TextView(this).apply {
            text = textValue
            textSize = 16f
            setTextColor(WoWPalette.ACCENT)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(9), dp(10), dp(9))
            setOnClickListener { click() }
        }
        bar.addView(btn("‹") { saveNow(); finish() })
        pinBtn = btn("☆") {
            note = note.copy(pinned = !note.pinned)
            pinBtn.text = if (note.pinned) "★" else "☆"
            pinBtn.setTextColor(if (note.pinned) WoWPalette.GOLD else WoWPalette.ACCENT)
            scheduleSave()
        }
        colorBtn = btn("●") {
            val i = palette.indexOf(note.color).takeIf { it >= 0 } ?: 0
            note = note.copy(color = palette[(i + 1) % palette.size])
            colorBtn.setTextColor(note.color)
            scheduleSave()
        }
        modeBtn = btn("Preview") { togglePreview() }
        val spacer = View(this)
        bar.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        bar.addView(pinBtn)
        bar.addView(colorBtn)
        bar.addView(modeBtn)

        val readerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        fun mini(textValue: String, click: () -> Unit) = TextView(this).apply {
            text = textValue
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(WoWPalette.ACCENT)
            setPadding(dp(9), dp(7), dp(9), dp(7))
            background = rounded(WoWPalette.SURFACE_ALT, dp(11).toFloat(), WoWPalette.LINE, dp(1))
            setOnClickListener { click() }
        }
        val gap = { View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(6), 1) } }
        sizeLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(WoWPalette.MUTED)
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), 0)
        }
        lineLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(WoWPalette.MUTED)
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), 0)
        }
        readerBar.addView(mini("A−") { changeReaderSize(-1f) })
        readerBar.addView(gap())
        readerBar.addView(sizeLabel)
        readerBar.addView(gap())
        readerBar.addView(mini("A+") { changeReaderSize(1f) })
        readerBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        readerBar.addView(mini("↕−") { changeLineHeight(-0.05f) })
        readerBar.addView(gap())
        readerBar.addView(lineLabel)
        readerBar.addView(gap())
        readerBar.addView(mini("↕+") { changeLineHeight(0.05f) })

        titleEdit = EditText(this).apply {
            hint = "Title"
            textSize = 23f
            setTextColor(WoWPalette.TEXT)
            setHintTextColor(WoWPalette.MUTED)
            background = null
            setPadding(dp(18), dp(10), dp(18), dp(8))
            setSingleLine(true)
            typeface = Typeface.create(tf, Typeface.BOLD)
        }

        bodyEdit = EditText(this).apply {
            hint = "Write…"
            gravity = Gravity.TOP or Gravity.START
            setTextColor(WoWPalette.TEXT)
            setHintTextColor(WoWPalette.MUTED)
            background = rounded(WoWPalette.SURFACE, dp(18).toFloat(), WoWPalette.LINE, dp(1))
            setPadding(dp(18), dp(16), dp(18), dp(32))
            typeface = tf
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false
            setHorizontallyScrolling(false)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }

        preview = TextView(this).apply {
            setTextColor(WoWPalette.TEXT)
            setPadding(dp(18), dp(18), dp(18), dp(72))
            typeface = tf
            includeFontPadding = true
            gravity = Gravity.TOP or Gravity.START
            isFocusable = false
            isFocusableInTouchMode = false
            setTextIsSelectable(false)
        }
        previewScroll = ScrollView(this).apply {
            visibility = View.GONE
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            clipToPadding = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setPadding(0, 0, 0, dp(12))
            background = rounded(WoWPalette.SURFACE, dp(18).toFloat(), WoWPalette.LINE, dp(1))
            addView(preview, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        root.addView(bar)
        root.addView(readerBar)
        root.addView(titleEdit)
        val contentMargin = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            setMargins(dp(4), 0, dp(4), dp(4))
        }
        root.addView(bodyEdit, contentMargin)
        root.addView(previewScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            setMargins(dp(4), 0, dp(4), dp(4))
        })

        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!loading) scheduleSave()
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        }
        titleEdit.addTextChangedListener(watcher)
        bodyEdit.addTextChangedListener(watcher)
        return root
    }

    private fun applyReadingSettings() {
        bodyEdit.textSize = readerSize
        preview.textSize = readerSize
        bodyEdit.setLineSpacing(dp(2).toFloat(), lineHeight)
        preview.setLineSpacing(dp(3).toFloat(), lineHeight)
        sizeLabel.text = readerSize.toInt().toString()
        lineLabel.text = String.format("%.2f", lineHeight)
    }

    private fun changeReaderSize(delta: Float) {
        readerSize = (readerSize + delta).coerceIn(12f, 34f)
        prefs.edit().putFloat("reader_size", readerSize).apply()
        applyReadingSettings()
        keepPreviewPosition()
    }

    private fun changeLineHeight(delta: Float) {
        lineHeight = (lineHeight + delta).coerceIn(1.0f, 2.0f)
        prefs.edit().putFloat("line_height", lineHeight).apply()
        applyReadingSettings()
        keepPreviewPosition()
    }

    private fun keepPreviewPosition() {
        if (previewMode) {
            val y = previewScroll.scrollY
            previewScroll.post { previewScroll.scrollTo(0, y) }
        }
    }

    private fun applyNote() {
        loading = true
        titleEdit.setText(note.title)
        bodyEdit.setText(note.body)
        val bodyTf = FontManager.typefaceForText(this, note.body)
        bodyEdit.typeface = bodyTf
        preview.typeface = bodyTf
        pinBtn.text = if (note.pinned) "★" else "☆"
        pinBtn.setTextColor(if (note.pinned) WoWPalette.GOLD else WoWPalette.ACCENT)
        colorBtn.setTextColor(note.color)
        bodyEdit.setSelection(0)
        bodyEdit.scrollTo(0, 0)
        loading = false
    }

    private fun resetPreviewToTop() {
        previewScroll.clearFocus()
        preview.clearFocus()
        previewScroll.scrollTo(0, 0)
        previewScroll.post {
            previewScroll.scrollTo(0, 0)
            previewScroll.fullScroll(View.FOCUS_UP)
        }
        previewScroll.postDelayed({
            previewScroll.scrollTo(0, 0)
        }, 80)
    }

    private fun togglePreview() {
        previewMode = !previewMode
        if (previewMode) {
            val text = bodyEdit.text.toString()
            preview.typeface = FontManager.typefaceForText(this, text)
            preview.text = text
            bodyEdit.clearFocus()
            titleEdit.clearFocus()
            bodyEdit.visibility = View.GONE
            previewScroll.visibility = View.VISIBLE
            modeBtn.text = "Edit"
            resetPreviewToTop()
        } else {
            previewScroll.visibility = View.GONE
            bodyEdit.visibility = View.VISIBLE
            modeBtn.text = "Preview"
            bodyEdit.post { bodyEdit.scrollTo(0, 0) }
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
            runCatching { db.save(snapshot) }.onSuccess { id ->
                if (snapshot.id == 0L) main.post {
                    note = note.copy(id = id, createdAt = System.currentTimeMillis())
                }
            }
        }
    }

    override fun onPause() { saveNow(); super.onPause() }
    override fun onBackPressed() { saveNow(); super.onBackPressed() }
    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        io.shutdown()
        db.close()
        super.onDestroy()
    }
}
