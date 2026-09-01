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
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.GridLayout
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
    private lateinit var readerScroll: ScrollView
    private lateinit var readerTitle: TextView
    private lateinit var readerBody: TextView
    private lateinit var modeBtn: TextView
    private lateinit var pinBtn: TextView
    private lateinit var colorBtn: TextView
    private lateinit var typeLabel: TextView

    private var note = Note()
    private var loading = true
    private var editing = false
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
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        readerSize = prefs.getFloat("reader_size", 18f).coerceIn(12f, 34f)
        lineHeight = prefs.getFloat("line_height", 1.24f).coerceIn(1.0f, 2.0f)
        db = NoteDb(this)
        setContentView(buildUi())

        val id = intent.getLongExtra("note_id", 0L)
        if (id == 0L) {
            note = Note(
                noteType = intent.getStringExtra("note_type") ?: "text",
                calendarDate = intent.getStringExtra("calendar_date") ?: "",
                color = WoWPalette.NOTE_COLORS.first()
            )
            loading = false
            applyNoteToViews()
            setEditing(true, focusBody = true)
        } else {
            io.execute {
                val loaded = db.get(id)
                main.post {
                    note = loaded ?: Note()
                    loading = false
                    if (loaded == null) {
                        finish()
                    } else if (note.locked) {
                        showLockedPlaceholder()
                        promptPin("Locked note") { ok ->
                            if (ok) {
                                applyNoteToViews()
                                setEditing(false)
                            } else finish()
                        }
                    } else {
                        applyNoteToViews()
                        setEditing(false)
                    }
                }
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
            text = textValue
            textSize = 17f
            setTextColor(WoWPalette.ACCENT)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(9), dp(10), dp(9))
            setOnClickListener { click() }
        }
        bar.addView(btn("‹") { saveNow(); finish() })
        typeLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(WoWPalette.MUTED)
            setPadding(dp(8), 0, dp(8), 0)
        }
        bar.addView(typeLabel)
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        pinBtn = btn("☆") {
            note = note.copy(pinned = !note.pinned)
            updateToolbarState()
            scheduleSave()
        }
        colorBtn = btn("●") { showColorPicker() }
        modeBtn = btn("✎") {
            if (editing) {
                saveNow()
                syncNoteFromEditor()
                refreshReader()
                setEditing(false)
            } else {
                setEditing(true, focusBody = true)
            }
        }
        bar.addView(btn("Aa") { showReaderSettings() })
        bar.addView(pinBtn)
        bar.addView(colorBtn)
        bar.addView(modeBtn)
        bar.addView(btn("⋮") { showActions() })

        titleEdit = EditText(this).apply {
            hint = "Title"
            textSize = 22f
            setTextColor(WoWPalette.TEXT)
            setHintTextColor(WoWPalette.MUTED)
            background = null
            setPadding(dp(14), dp(9), dp(14), dp(7))
            setSingleLine(true)
            typeface = Typeface.create(tf, Typeface.BOLD)
            visibility = View.GONE
        }
        bodyEdit = EditText(this).apply {
            hint = "Write…"
            gravity = Gravity.TOP or Gravity.START
            setTextColor(WoWPalette.TEXT)
            setHintTextColor(WoWPalette.MUTED)
            background = rounded(WoWPalette.SURFACE, dp(13).toFloat(), WoWPalette.LINE, dp(1))
            setPadding(dp(15), dp(14), dp(15), dp(32))
            typeface = tf
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false
            setHorizontallyScrolling(false)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            visibility = View.GONE
        }

        val readerContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(56))
        }
        readerTitle = TextView(this).apply {
            textSize = 23f
            setTextColor(WoWPalette.TEXT)
            typeface = Typeface.create(tf, Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
            setTextIsSelectable(true)
        }
        readerBody = TextView(this).apply {
            setTextColor(WoWPalette.TEXT)
            typeface = tf
            includeFontPadding = true
            gravity = Gravity.TOP or Gravity.START
            setTextIsSelectable(true)
        }
        readerContent.addView(readerTitle)
        readerContent.addView(readerBody, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        readerScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            clipToPadding = false
            background = rounded(WoWPalette.SURFACE, dp(13).toFloat(), WoWPalette.LINE, dp(1))
            addView(readerContent, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        root.addView(bar)
        root.addView(titleEdit)
        root.addView(bodyEdit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            setMargins(dp(2), 0, dp(2), dp(2))
        })
        root.addView(readerScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            setMargins(dp(2), dp(8), dp(2), dp(2))
        })

        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!loading && editing) scheduleSave()
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        }
        titleEdit.addTextChangedListener(watcher)
        bodyEdit.addTextChangedListener(watcher)
        applyReadingSettings()
        return root
    }

    private fun showLockedPlaceholder() {
        readerTitle.text = "Locked note"
        readerBody.text = "Enter your master PIN to view this note."
        titleEdit.visibility = View.GONE
        bodyEdit.visibility = View.GONE
        readerScroll.visibility = View.VISIBLE
        modeBtn.visibility = View.GONE
    }

    private fun applyNoteToViews() {
        loading = true
        titleEdit.setText(note.title)
        bodyEdit.setText(note.body)
        bodyEdit.hint = if (note.noteType == "checklist") "One item per line…" else "Write…"
        val tf = FontManager.typefaceForText(this, note.body)
        bodyEdit.typeface = tf
        readerBody.typeface = tf
        refreshReader()
        updateToolbarState()
        loading = false
    }

    private fun updateToolbarState() {
        typeLabel.text = when {
            note.noteType == "checklist" -> "Checklist"
            note.calendarDate.isNotBlank() -> "Calendar note"
            else -> "Text note"
        }
        pinBtn.text = if (note.pinned) "★" else "☆"
        pinBtn.setTextColor(if (note.pinned) WoWPalette.GOLD else WoWPalette.ACCENT)
        colorBtn.setTextColor(note.color)
        modeBtn.visibility = View.VISIBLE
        modeBtn.text = if (editing) "✓" else "✎"
    }

    private fun displayBody(raw: String): String {
        if (note.noteType != "checklist") return raw
        return raw.lines().joinToString("\n") { line ->
            if (line.isBlank()) "" else if (line.startsWith("☑") || line.startsWith("☐")) line else "☐  $line"
        }
    }

    private fun refreshReader() {
        val title = currentTitle()
        val body = currentBody()
        readerTitle.text = title.ifBlank { "Untitled" }
        readerTitle.typeface = Typeface.create(FontManager.typefaceForText(this, title), Typeface.BOLD)
        readerBody.text = displayBody(body)
        readerBody.typeface = FontManager.typefaceForText(this, body)
        applyReadingSettings()
        resetReaderToTop()
    }

    private fun setEditing(value: Boolean, focusBody: Boolean = false) {
        editing = value
        if (value) {
            readerScroll.visibility = View.GONE
            titleEdit.visibility = View.VISIBLE
            bodyEdit.visibility = View.VISIBLE
            modeBtn.text = "✓"
            if (focusBody) {
                bodyEdit.post {
                    bodyEdit.requestFocus()
                    bodyEdit.setSelection(bodyEdit.text.length)
                }
            }
        } else {
            syncNoteFromEditor()
            titleEdit.clearFocus()
            bodyEdit.clearFocus()
            titleEdit.visibility = View.GONE
            bodyEdit.visibility = View.GONE
            readerScroll.visibility = View.VISIBLE
            modeBtn.text = "✎"
            refreshReader()
        }
    }

    private fun syncNoteFromEditor() {
        note = note.copy(title = titleEdit.text.toString(), body = bodyEdit.text.toString())
    }

    private fun currentTitle(): String = if (editing) titleEdit.text.toString() else note.title
    private fun currentBody(): String = if (editing) bodyEdit.text.toString() else note.body

    private fun resetReaderToTop() {
        readerScroll.clearFocus()
        readerScroll.scrollTo(0, 0)
        readerScroll.post { readerScroll.scrollTo(0, 0) }
        readerScroll.postDelayed({ readerScroll.scrollTo(0, 0) }, 80)
    }

    private fun applyReadingSettings() {
        bodyEdit.textSize = readerSize
        readerBody.textSize = readerSize
        bodyEdit.setLineSpacing(dp(2).toFloat(), lineHeight)
        readerBody.setLineSpacing(dp(2).toFloat(), lineHeight)
    }

    private fun showReaderSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(14))
        }
        val sizeText = TextView(this).apply {
            textSize = 16f
            setTextColor(WoWPalette.TEXT)
            gravity = Gravity.CENTER
        }
        val lineText = TextView(this).apply {
            textSize = 16f
            setTextColor(WoWPalette.TEXT)
            gravity = Gravity.CENTER
        }
        fun row(label: TextView, minus: () -> Unit, plus: () -> Unit): LinearLayout = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
            val m = TextView(this@EditorActivity).apply {
                text = "−"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(WoWPalette.ACCENT); setPadding(dp(18), dp(8), dp(18), dp(8)); setOnClickListener { minus() }
            }
            val p = TextView(this@EditorActivity).apply {
                text = "+"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(WoWPalette.ACCENT); setPadding(dp(18), dp(8), dp(18), dp(8)); setOnClickListener { plus() }
            }
            addView(m)
            addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(p)
        }
        fun updateLabels() {
            sizeText.text = "Font size  ${readerSize.toInt()}"
            lineText.text = "Line height  ${String.format(Locale.US, "%.2f", lineHeight)}"
        }
        updateLabels()
        box.addView(row(sizeText, {
            readerSize = (readerSize - 1f).coerceIn(12f, 34f)
            prefs.edit().putFloat("reader_size", readerSize).apply()
            applyReadingSettings(); updateLabels()
        }, {
            readerSize = (readerSize + 1f).coerceIn(12f, 34f)
            prefs.edit().putFloat("reader_size", readerSize).apply()
            applyReadingSettings(); updateLabels()
        }))
        box.addView(row(lineText, {
            lineHeight = (lineHeight - 0.05f).coerceIn(1.0f, 2.0f)
            prefs.edit().putFloat("line_height", lineHeight).apply()
            applyReadingSettings(); updateLabels()
        }, {
            lineHeight = (lineHeight + 0.05f).coerceIn(1.0f, 2.0f)
            prefs.edit().putFloat("line_height", lineHeight).apply()
            applyReadingSettings(); updateLabels()
        }))
        AlertDialog.Builder(this).setTitle("Reading settings").setView(box).setPositiveButton("Done", null).show()
    }

    private fun showActions() {
        val labels = arrayOf(
            if (note.noteType == "checklist") "Convert to text" else "Check",
            "Copy all",
            "Send",
            "Reminder",
            "Find",
            if (note.locked) "Unlock" else "Lock",
            if (note.archived) "Unarchive" else "Archive",
            "Delete"
        )
        AlertDialog.Builder(this).setItems(labels) { _, which ->
            when (which) {
                0 -> {
                    syncNoteFromEditor()
                    note = note.copy(noteType = if (note.noteType == "checklist") "text" else "checklist")
                    bodyEdit.hint = if (note.noteType == "checklist") "One item per line…" else "Write…"
                    updateToolbarState(); refreshReader(); scheduleSave()
                }
                1 -> copyAll()
                2 -> sendNote()
                3 -> chooseReminder()
                4 -> findText()
                5 -> toggleLock()
                6 -> {
                    syncNoteFromEditor()
                    note = note.copy(archived = !note.archived)
                    saveNow()
                    Toast.makeText(this, if (note.archived) "Archived" else "Unarchived", Toast.LENGTH_SHORT).show()
                    finish()
                }
                7 -> confirmDelete()
            }
        }.show()
    }

    private fun showColorPicker() {
        val grid = GridLayout(this).apply {
            columnCount = 2
            setPadding(dp(16), dp(10), dp(16), dp(16))
        }
        val dialog = AlertDialog.Builder(this).setTitle("Color").setView(grid).setNegativeButton("Cancel", null).create()
        palette.forEach { color ->
            val swatch = View(this).apply {
                background = rounded(color, dp(8).toFloat(), if (note.color == color) WoWPalette.TEXT else WoWPalette.LINE, dp(if (note.color == color) 2 else 1))
                setOnClickListener {
                    note = note.copy(color = color)
                    updateToolbarState()
                    scheduleSave()
                    dialog.dismiss()
                }
            }
            grid.addView(swatch, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(60)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(5), dp(5), dp(5), dp(5))
            })
        }
        dialog.show()
    }

    private fun copyAll() {
        val text = buildString {
            val title = currentTitle()
            if (title.isNotBlank()) append(title).append("\n\n")
            append(currentBody())
        }
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("WoW Note", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun sendNote() {
        val text = buildString {
            val title = currentTitle()
            if (title.isNotBlank()) append(title).append("\n\n")
            append(currentBody())
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, currentTitle())
        }, "Send note"))
    }

    private fun findText() {
        val input = EditText(this).apply { hint = "Find"; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("Find").setView(input).setPositiveButton("Find") { _, _ ->
            val q = input.text.toString()
            if (q.isBlank()) return@setPositiveButton
            val shown = displayBody(currentBody())
            val start = shown.indexOf(q, ignoreCase = true)
            if (start < 0) {
                Toast.makeText(this, "Not found", Toast.LENGTH_SHORT).show()
            } else {
                if (editing) setEditing(false)
                val span = SpannableString(shown)
                span.setSpan(BackgroundColorSpan(WoWPalette.GOLD), start, start + q.length, 0)
                span.setSpan(ForegroundColorSpan(WoWPalette.BG), start, start + q.length, 0)
                readerBody.text = span
                readerScroll.post { readerScroll.scrollTo(0, 0) }
            }
        }.setNegativeButton("Cancel", null).show()
    }

    private fun chooseReminder() {
        val c = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
        DatePickerDialog(this, { _, y, m, d ->
            TimePickerDialog(this, { _, h, min ->
                val target = Calendar.getInstance().apply {
                    set(y, m, d, h, min, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                syncNoteFromEditor()
                note = note.copy(reminderAt = target)
                saveNow()
                Toast.makeText(this, "Reminder: ${SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(target))}", Toast.LENGTH_LONG).show()
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun toggleLock() {
        if (note.locked) {
            promptPin("Unlock note") { ok ->
                if (ok) {
                    note = note.copy(locked = false)
                    scheduleSave()
                    Toast.makeText(this, "Unlocked", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        val current = security.getString("master_pin", "").orEmpty()
        if (current.isBlank()) {
            val input = EditText(this).apply {
                hint = "New PIN"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            }
            AlertDialog.Builder(this).setTitle("Set master PIN").setView(input).setPositiveButton("Set") { _, _ ->
                val pin = input.text.toString()
                if (pin.length >= 4) {
                    security.edit().putString("master_pin", pin).apply()
                    note = note.copy(locked = true)
                    scheduleSave()
                    Toast.makeText(this, "Locked", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
        } else {
            promptPin("Lock note") { ok ->
                if (ok) {
                    note = note.copy(locked = true)
                    scheduleSave()
                    Toast.makeText(this, "Locked", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun promptPin(title: String, result: (Boolean) -> Unit) {
        val input = EditText(this).apply {
            hint = "Master PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this).setTitle(title).setView(input).setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                val ok = input.text.toString() == security.getString("master_pin", "").orEmpty()
                if (!ok) Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
                result(ok)
            }
            .setNegativeButton("Cancel") { _, _ -> result(false) }
            .show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this).setMessage("Delete this note?").setPositiveButton("Delete") { _, _ ->
            val id = note.id
            if (id > 0) io.execute {
                ReminderScheduler.cancel(this, id)
                db.delete(id)
                main.post { finish() }
            } else finish()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun scheduleSave() {
        saveTask?.let(main::removeCallbacks)
        saveTask = Runnable { saveNow() }
        main.postDelayed(saveTask!!, 300)
    }

    private fun saveNow() {
        if (loading) return
        saveTask?.let(main::removeCallbacks)
        if (editing) syncNoteFromEditor()
        val snapshot = note
        io.execute {
            runCatching { db.save(snapshot) }.onSuccess { id ->
                if (snapshot.reminderAt > 0) {
                    ReminderScheduler.schedule(this, id, snapshot.reminderAt, snapshot.title.ifBlank { "WoW Note" })
                }
                if (snapshot.id == 0L) main.post {
                    note = note.copy(id = id, createdAt = System.currentTimeMillis())
                }
            }
        }
    }

    override fun onPause() {
        saveNow()
        super.onPause()
    }

    override fun onBackPressed() {
        saveNow()
        super.onBackPressed()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        io.shutdown()
        db.close()
        super.onDestroy()
    }
}
