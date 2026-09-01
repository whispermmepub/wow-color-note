package com.whispermmepub.wowcolornote

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.whispermmepub.wowcolornote.data.NoteDb
import com.whispermmepub.wowcolornote.font.FontManager
import com.whispermmepub.wowcolornote.model.Note
import com.whispermmepub.wowcolornote.reminder.ReminderScheduler
import com.whispermmepub.wowcolornote.ui.WoWPalette
import com.whispermmepub.wowcolornote.ui.dp
import com.whispermmepub.wowcolornote.ui.rounded
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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
    private lateinit var typeLabel: TextView

    private var note = Note()
    private var loading = true
    private var previewMode = false
    private var saveTask: Runnable? = null
    private val palette = WoWPalette.NOTE_COLORS

    private val prefs by lazy { getSharedPreferences("wow_note_reader", MODE_PRIVATE) }
    private val security by lazy { getSharedPreferences("wow_note_security", MODE_PRIVATE) }
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
            note = Note(
                noteType = intent.getStringExtra("note_type") ?: "text",
                calendarDate = intent.getStringExtra("calendar_date") ?: "",
                color = WoWPalette.NOTE_COLORS.first()
            )
            loading = false
            applyNote()
        } else io.execute {
            val n = db.get(id)
            main.post {
                note = n ?: Note()
                loading = false
                applyNote()
                if (note.locked) promptUnlock()
            }
        }
    }

    private fun buildUi(): LinearLayout {
        val tf = FontManager.typeface(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(WoWPalette.BG)
            setPadding(dp(8), dp(7), dp(8), dp(8))
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(3), dp(4), dp(3))
            background = rounded(WoWPalette.SURFACE, dp(15).toFloat(), WoWPalette.LINE, dp(1))
        }
        fun btn(textValue: String, click: () -> Unit) = TextView(this).apply {
            text = textValue; textSize = 16f; setTextColor(WoWPalette.ACCENT); gravity = Gravity.CENTER
            setPadding(dp(10), dp(9), dp(10), dp(9)); setOnClickListener { click() }
        }
        bar.addView(btn("‹") { saveNow(); finish() })
        typeLabel = TextView(this).apply {
            textSize = 14f; setTextColor(WoWPalette.MUTED); setPadding(dp(8), 0, dp(8), 0)
        }
        bar.addView(typeLabel)
        val spacer = View(this)
        bar.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        pinBtn = btn("☆") {
            note = note.copy(pinned = !note.pinned)
            pinBtn.text = if (note.pinned) "★" else "☆"
            pinBtn.setTextColor(if (note.pinned) WoWPalette.GOLD else WoWPalette.ACCENT)
            scheduleSave()
        }
        colorBtn = btn("●") { showColorPicker() }
        modeBtn = btn("Preview") { togglePreview() }
        bar.addView(pinBtn); bar.addView(colorBtn); bar.addView(modeBtn); bar.addView(btn("⋮") { showActions() })

        val readerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(3), dp(6), dp(3))
        }
        fun mini(textValue: String, click: () -> Unit) = TextView(this).apply {
            text = textValue; textSize = 13f; gravity = Gravity.CENTER; setTextColor(WoWPalette.ACCENT)
            setPadding(dp(8), dp(6), dp(8), dp(6)); background = rounded(WoWPalette.CARD_ALT, dp(9).toFloat(), WoWPalette.LINE, dp(1))
            setOnClickListener { click() }
        }
        val gap = { View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(5), 1) } }
        sizeLabel = TextView(this).apply { textSize = 11f; setTextColor(WoWPalette.MUTED); gravity = Gravity.CENTER; setPadding(dp(6), 0, dp(6), 0) }
        lineLabel = TextView(this).apply { textSize = 11f; setTextColor(WoWPalette.MUTED); gravity = Gravity.CENTER; setPadding(dp(6), 0, dp(6), 0) }
        readerBar.addView(mini("A−") { changeReaderSize(-1f) }); readerBar.addView(gap()); readerBar.addView(sizeLabel); readerBar.addView(gap()); readerBar.addView(mini("A+") { changeReaderSize(1f) })
        readerBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        readerBar.addView(mini("↕−") { changeLineHeight(-0.05f) }); readerBar.addView(gap()); readerBar.addView(lineLabel); readerBar.addView(gap()); readerBar.addView(mini("↕+") { changeLineHeight(0.05f) })

        titleEdit = EditText(this).apply {
            hint = "Title"; textSize = 22f; setTextColor(WoWPalette.TEXT); setHintTextColor(WoWPalette.MUTED); background = null
            setPadding(dp(14), dp(9), dp(14), dp(7)); setSingleLine(true); typeface = Typeface.create(tf, Typeface.BOLD)
        }
        bodyEdit = EditText(this).apply {
            hint = "Write…"; gravity = Gravity.TOP or Gravity.START; setTextColor(WoWPalette.TEXT); setHintTextColor(WoWPalette.MUTED)
            background = rounded(WoWPalette.SURFACE, dp(13).toFloat(), WoWPalette.LINE, dp(1)); setPadding(dp(15), dp(14), dp(15), dp(28))
            typeface = tf; isVerticalScrollBarEnabled = true; isHorizontalScrollBarEnabled = false; setHorizontallyScrolling(false)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        preview = TextView(this).apply {
            setTextColor(WoWPalette.TEXT); setPadding(dp(15), dp(15), dp(15), dp(64)); typeface = tf; includeFontPadding = true
            gravity = Gravity.TOP or Gravity.START; isFocusable = false; isFocusableInTouchMode = false; setTextIsSelectable(true)
        }
        previewScroll = ScrollView(this).apply {
            visibility = View.GONE; isFillViewport = true; isVerticalScrollBarEnabled = true; clipToPadding = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS; background = rounded(WoWPalette.SURFACE, dp(13).toFloat(), WoWPalette.LINE, dp(1))
            addView(preview, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        root.addView(bar); root.addView(readerBar); root.addView(titleEdit)
        root.addView(bodyEdit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(dp(2), 0, dp(2), dp(2)) })
        root.addView(previewScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(dp(2), 0, dp(2), dp(2)) })

        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { if (!loading) scheduleSave() }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        }
        titleEdit.addTextChangedListener(watcher); bodyEdit.addTextChangedListener(watcher)
        return root
    }

    private fun applyReadingSettings() {
        bodyEdit.textSize = readerSize; preview.textSize = readerSize
        bodyEdit.setLineSpacing(dp(2).toFloat(), lineHeight); preview.setLineSpacing(dp(3).toFloat(), lineHeight)
        sizeLabel.text = readerSize.toInt().toString(); lineLabel.text = String.format(Locale.US, "%.2f", lineHeight)
    }

    private fun changeReaderSize(delta: Float) {
        readerSize = (readerSize + delta).coerceIn(12f, 34f); prefs.edit().putFloat("reader_size", readerSize).apply(); applyReadingSettings(); keepPreviewPosition()
    }

    private fun changeLineHeight(delta: Float) {
        lineHeight = (lineHeight + delta).coerceIn(1.0f, 2.0f); prefs.edit().putFloat("line_height", lineHeight).apply(); applyReadingSettings(); keepPreviewPosition()
    }

    private fun keepPreviewPosition() {
        if (previewMode) { val y = previewScroll.scrollY; previewScroll.post { previewScroll.scrollTo(0, y) } }
    }

    private fun applyNote() {
        loading = true
        titleEdit.setText(note.title); bodyEdit.setText(note.body)
        bodyEdit.hint = if (note.noteType == "checklist") "One item per line…" else "Write…"
        typeLabel.text = if (note.noteType == "checklist") "Checklist" else if (note.calendarDate.isNotBlank()) "Calendar note" else "Text note"
        val bodyTf = FontManager.typefaceForText(this, note.body); bodyEdit.typeface = bodyTf; preview.typeface = bodyTf
        pinBtn.text = if (note.pinned) "★" else "☆"; pinBtn.setTextColor(if (note.pinned) WoWPalette.GOLD else WoWPalette.ACCENT)
        colorBtn.setTextColor(note.color); bodyEdit.setSelection(0); bodyEdit.scrollTo(0, 0); loading = false
    }

    private fun previewText(): String {
        val raw = bodyEdit.text.toString()
        return if (note.noteType == "checklist") raw.lines().joinToString("\n") { line -> if (line.isBlank()) "" else "☐  $line" } else raw
    }

    private fun resetPreviewToTop() {
        previewScroll.clearFocus(); preview.clearFocus(); previewScroll.scrollTo(0, 0)
        previewScroll.post { previewScroll.scrollTo(0, 0); previewScroll.fullScroll(View.FOCUS_UP) }
        previewScroll.postDelayed({ previewScroll.scrollTo(0, 0) }, 90)
    }

    private fun togglePreview() {
        previewMode = !previewMode
        if (previewMode) {
            val text = previewText(); preview.typeface = FontManager.typefaceForText(this, text); preview.text = text
            bodyEdit.clearFocus(); titleEdit.clearFocus(); bodyEdit.visibility = View.GONE; previewScroll.visibility = View.VISIBLE; modeBtn.text = "Edit"; resetPreviewToTop()
        } else {
            previewScroll.visibility = View.GONE; bodyEdit.visibility = View.VISIBLE; modeBtn.text = "Preview"; bodyEdit.post { bodyEdit.scrollTo(0, 0) }
        }
    }

    private fun showActions() {
        val labels = arrayOf(
            if (note.noteType == "checklist") "Convert to text" else "Convert to checklist",
            "Copy all", "Send", "Find", "Reminder",
            if (note.locked) "Unlock" else "Lock",
            if (note.archived) "Unarchive" else "Archive", "Delete"
        )
        AlertDialog.Builder(this).setItems(labels) { _, which ->
            when (which) {
                0 -> { note = note.copy(noteType = if (note.noteType == "checklist") "text" else "checklist"); applyNote(); scheduleSave() }
                1 -> copyAll()
                2 -> sendNote()
                3 -> findText()
                4 -> chooseReminder()
                5 -> toggleLock()
                6 -> { note = note.copy(archived = !note.archived); saveNow(); Toast.makeText(this, if (note.archived) "Archived" else "Unarchived", Toast.LENGTH_SHORT).show(); finish() }
                7 -> confirmDelete()
            }
        }.show()
    }

    private fun showColorPicker() {
        val labels = palette.indices.map { "Color ${it + 1}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Color").setItems(labels) { _, which ->
            note = note.copy(color = palette[which]); colorBtn.setTextColor(note.color); scheduleSave()
        }.show()
    }

    private fun copyAll() {
        val text = buildString { if (titleEdit.text.isNotBlank()) append(titleEdit.text).append("\n\n"); append(bodyEdit.text) }
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("WoW Note", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun sendNote() {
        val text = buildString { if (titleEdit.text.isNotBlank()) append(titleEdit.text).append("\n\n"); append(bodyEdit.text) }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); putExtra(Intent.EXTRA_SUBJECT, titleEdit.text.toString()) }, "Send note"))
    }

    private fun findText() {
        val input = EditText(this).apply { hint = "Find"; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("Find").setView(input).setPositiveButton("Find") { _, _ ->
            val q = input.text.toString(); if (q.isBlank()) return@setPositiveButton
            val start = bodyEdit.text.toString().indexOf(q, ignoreCase = true)
            if (start >= 0) { if (previewMode) togglePreview(); bodyEdit.requestFocus(); bodyEdit.setSelection(start, start + q.length) }
            else Toast.makeText(this, "Not found", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun chooseReminder() {
        val c = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
        DatePickerDialog(this, { _, y, m, d ->
            TimePickerDialog(this, { _, h, min ->
                val target = Calendar.getInstance().apply { set(y, m, d, h, min, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                note = note.copy(reminderAt = target); saveNow()
                Toast.makeText(this, "Reminder: ${SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(target))}", Toast.LENGTH_LONG).show()
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun toggleLock() {
        if (note.locked) {
            promptPin("Unlock note") { ok -> if (ok) { note = note.copy(locked = false); scheduleSave(); Toast.makeText(this, "Unlocked", Toast.LENGTH_SHORT).show() } }
            return
        }
        val current = security.getString("master_pin", "").orEmpty()
        if (current.isBlank()) {
            val input = EditText(this).apply { hint = "New PIN"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
            AlertDialog.Builder(this).setTitle("Set master PIN").setView(input).setPositiveButton("Set") { _, _ ->
                val pin = input.text.toString(); if (pin.length >= 4) { security.edit().putString("master_pin", pin).apply(); note = note.copy(locked = true); scheduleSave() } else Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
        } else {
            promptPin("Lock note") { ok -> if (ok) { note = note.copy(locked = true); scheduleSave(); Toast.makeText(this, "Locked", Toast.LENGTH_SHORT).show() } }
        }
    }

    private fun promptUnlock() {
        promptPin("Locked note") { ok -> if (!ok) finish() }
    }

    private fun promptPin(title: String, result: (Boolean) -> Unit) {
        val input = EditText(this).apply { hint = "Master PIN"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        AlertDialog.Builder(this).setTitle(title).setView(input).setCancelable(false).setPositiveButton("OK") { _, _ ->
            val ok = input.text.toString() == security.getString("master_pin", "").orEmpty()
            if (!ok) Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show(); result(ok)
        }.setNegativeButton("Cancel") { _, _ -> result(false) }.show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this).setMessage("Delete this note?").setPositiveButton("Delete") { _, _ ->
            val id = note.id; if (id > 0) io.execute { ReminderScheduler.cancel(this, id); db.delete(id); main.post { finish() } } else finish()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun scheduleSave() {
        saveTask?.let(main::removeCallbacks); saveTask = Runnable { saveNow() }; main.postDelayed(saveTask!!, 300)
    }

    private fun saveNow() {
        if (loading) return
        saveTask?.let(main::removeCallbacks)
        val snapshot = note.copy(title = titleEdit.text.toString(), body = bodyEdit.text.toString())
        note = snapshot
        io.execute {
            runCatching { db.save(snapshot) }.onSuccess { id ->
                if (snapshot.reminderAt > 0) ReminderScheduler.schedule(this, id, snapshot.reminderAt, snapshot.title.ifBlank { "WoW Note" })
                if (snapshot.id == 0L) main.post { note = note.copy(id = id, createdAt = System.currentTimeMillis()) }
            }
        }
    }

    override fun onPause() { saveNow(); super.onPause() }
    override fun onBackPressed() { saveNow(); super.onBackPressed() }
    override fun onDestroy() { main.removeCallbacksAndMessages(null); io.shutdown(); db.close(); super.onDestroy() }
}
