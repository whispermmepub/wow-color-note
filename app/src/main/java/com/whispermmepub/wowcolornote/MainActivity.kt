package com.whispermmepub.wowcolornote

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.whispermmepub.wowcolornote.data.NoteDb
import com.whispermmepub.wowcolornote.font.FontManager
import com.whispermmepub.wowcolornote.model.Note
import com.whispermmepub.wowcolornote.ui.WoWPalette
import com.whispermmepub.wowcolornote.ui.dp
import com.whispermmepub.wowcolornote.ui.rounded
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var db: NoteDb
    private lateinit var adapter: NoteAdapter
    private lateinit var search: EditText
    private lateinit var list: RecyclerView
    private lateinit var sortBar: TextView
    private lateinit var title: TextView
    private val prefs by lazy { getSharedPreferences("wow_note_home", MODE_PRIVATE) }
    private var sortMode = "modified"
    private var viewMode = "details"
    private var archivedMode = false
    private var pinnedOnly = false
    private var lastNotes: List<Note> = emptyList()

    private val fontPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) io.execute {
            val ok = FontManager.import(this, uri)
            main.post {
                if (ok) { adapter.setTypeface(FontManager.typeface(this)); Toast.makeText(this, "Custom font applied", Toast.LENGTH_SHORT).show() }
                else Toast.makeText(this, "Font file မဖတ်နိုင်ပါ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = WoWPalette.BG
        window.navigationBarColor = WoWPalette.BG
        sortMode = prefs.getString("sort", "modified") ?: "modified"
        viewMode = prefs.getString("view", "details") ?: "details"
        pinnedOnly = intent.getBooleanExtra("filter_pinned", false)
        db = NoteDb(this)
        adapter = NoteAdapter(FontManager.typeface(this), ::openNote, ::noteMenu).apply { setViewMode(viewMode) }
        setContentView(buildUi())
        applyViewMode(); updateHeader(); updateSortBar(); loadNotes("")
        if (intent.getBooleanExtra("focus_search", false)) search.post { focusSearch() }
    }

    override fun onResume() { super.onResume(); if (::search.isInitialized) loadNotes(search.text.toString()) }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(WoWPalette.BG); setPadding(dp(8), dp(7), dp(8), 0) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(7), dp(5), dp(7)) }
        title = TextView(this).apply {
            text = "WoW Note"; textSize = 27f; setTextColor(WoWPalette.TEXT); setTypeface(FontManager.typeface(this@MainActivity), Typeface.BOLD)
        }
        top.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        fun action(label: String, click: () -> Unit) = TextView(this).apply {
            text = label; textSize = 18f; gravity = Gravity.CENTER; setTextColor(WoWPalette.ACCENT); setPadding(dp(10), dp(8), dp(10), dp(8)); setOnClickListener { click() }
        }
        top.addView(action("Aa") { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream")) })
        top.addView(action("▣") { showViewDialog() })
        top.addView(action("＋") { chooseNewNote() })
        root.addView(top)

        sortBar = TextView(this).apply {
            textSize = 15f; gravity = Gravity.CENTER; setTextColor(WoWPalette.MUTED)
            background = rounded(WoWPalette.SURFACE, dp(8).toFloat(), WoWPalette.LINE, dp(1)); setPadding(dp(12), dp(7), dp(12), dp(7)); setOnClickListener { showSortDialog() }
        }
        root.addView(sortBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(3), 0, dp(5)) })

        search = EditText(this).apply {
            hint = "Search notes"; textSize = 15f; setSingleLine(true); setTextColor(WoWPalette.TEXT); setHintTextColor(WoWPalette.MUTED)
            background = rounded(WoWPalette.CARD, dp(10).toFloat(), WoWPalette.LINE, dp(1)); setPadding(dp(14), dp(9), dp(14), dp(9))
        }
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(4)) })
        search.addTextChangedListener(object : android.text.TextWatcher {
            private var task: Runnable? = null
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                task?.let(main::removeCallbacks); task = Runnable { loadNotes(s?.toString().orEmpty()) }; main.postDelayed(task!!, 90)
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        list = RecyclerView(this).apply {
            adapter = this@MainActivity.adapter; setHasFixedSize(false); itemAnimator = null; setBackgroundColor(WoWPalette.BG); clipToPadding = false; setPadding(0, 0, 0, dp(6))
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNav())
        return root
    }

    private fun bottomNav(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(4)); background = rounded(WoWPalette.SURFACE, 0f, WoWPalette.LINE, dp(1))
        }
        fun item(icon: String, active: Boolean = false, action: () -> Unit) = TextView(this).apply {
            text = icon; textSize = 24f; gravity = Gravity.CENTER; setTextColor(if (active) WoWPalette.ACCENT else WoWPalette.MUTED); setPadding(0, dp(7), 0, dp(7)); setOnClickListener { action() }
        }
        bar.addView(item("▤", !pinnedOnly && !archivedMode) { pinnedOnly = false; archivedMode = false; updateHeader(); loadNotes(search.text.toString()) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(item("▦") { startActivity(Intent(this, CalendarActivity::class.java)) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(item("★", pinnedOnly) { pinnedOnly = true; archivedMode = false; updateHeader(); loadNotes(search.text.toString()) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(item("⌕") { focusSearch() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(item("☰", archivedMode) { showMainMenu() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return bar
    }

    private fun focusSearch() {
        search.requestFocus(); search.setSelection(search.length()); (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun chooseNewNote() {
        AlertDialog.Builder(this).setTitle("Add note").setItems(arrayOf("Text note", "Checklist")) { _, which ->
            startActivity(Intent(this, EditorActivity::class.java).putExtra("note_id", 0L).putExtra("note_type", if (which == 1) "checklist" else "text"))
        }.show()
    }

    private fun showMainMenu() {
        AlertDialog.Builder(this).setItems(arrayOf("View", "Archive", "Normal notes", "Calendar", "Custom font")) { _, which ->
            when (which) {
                0 -> showViewDialog()
                1 -> { archivedMode = true; pinnedOnly = false; updateHeader(); loadNotes(search.text.toString()) }
                2 -> { archivedMode = false; pinnedOnly = false; updateHeader(); loadNotes(search.text.toString()) }
                3 -> startActivity(Intent(this, CalendarActivity::class.java))
                4 -> fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream"))
            }
        }.show()
    }

    private fun showSortDialog() {
        val labels = arrayOf("Modified time", "Created time", "Title")
        val values = arrayOf("modified", "created", "title")
        AlertDialog.Builder(this).setTitle("Sort by").setSingleChoiceItems(labels, values.indexOf(sortMode)) { d, which ->
            sortMode = values[which]; prefs.edit().putString("sort", sortMode).apply(); updateSortBar(); loadNotes(search.text.toString()); d.dismiss()
        }.show()
    }

    private fun showViewDialog() {
        val labels = arrayOf("☰  List", "▤  Details", "▦  Grid", "▧  Large grid")
        val values = arrayOf("list", "details", "grid", "large_grid")
        AlertDialog.Builder(this).setTitle("View").setSingleChoiceItems(labels, values.indexOf(viewMode)) { d, which ->
            viewMode = values[which]; prefs.edit().putString("view", viewMode).apply(); applyViewMode(); d.dismiss()
        }.show()
    }

    private fun updateHeader() { title.text = when { archivedMode -> "Archive"; pinnedOnly -> "Pinned"; else -> "WoW Note" } }

    private fun updateSortBar() {
        sortBar.text = when (sortMode) { "created" -> "Sort by created time  ▼"; "title" -> "Sort by title  ▼"; else -> "Sort by modified time  ▼" }
    }

    private fun applyViewMode() {
        adapter.setViewMode(viewMode)
        list.layoutManager = when (viewMode) { "grid", "large_grid" -> GridLayoutManager(this, 2); else -> LinearLayoutManager(this) }
    }

    private fun loadNotes(q: String) {
        io.execute {
            val all = db.list(q, sortMode, archivedMode)
            val notes = if (pinnedOnly) all.filter { it.pinned } else all
            main.post { lastNotes = notes; adapter.submit(notes) }
        }
    }

    private fun openNote(note: Note?) { startActivity(Intent(this, EditorActivity::class.java).putExtra("note_id", note?.id ?: 0L)) }

    private fun noteMenu(note: Note) {
        AlertDialog.Builder(this).setTitle(note.title.ifBlank { "Untitled" }).setItems(arrayOf("Open", "Color", if (note.pinned) "Unpin" else "Pin", if (note.archived) "Unarchive" else "Archive", "Delete")) { _, which ->
            when (which) {
                0 -> openNote(note)
                1 -> showColorPicker(note)
                2 -> io.execute { db.save(note.copy(pinned = !note.pinned)); main.post { loadNotes(search.text.toString()) } }
                3 -> io.execute { db.save(note.copy(archived = !note.archived)); main.post { loadNotes(search.text.toString()) } }
                4 -> AlertDialog.Builder(this).setMessage("Delete this note?").setPositiveButton("Delete") { _, _ -> io.execute { db.delete(note.id); main.post { loadNotes(search.text.toString()) } } }.setNegativeButton("Cancel", null).show()
            }
        }.show()
    }

    private fun showColorPicker(note: Note) {
        val box = GridLayout(this).apply { columnCount = 2; setPadding(dp(14), dp(8), dp(14), dp(12)) }
        val dialog = AlertDialog.Builder(this).setTitle("Color").setView(box).setNegativeButton("Cancel", null).create()
        WoWPalette.NOTE_COLORS.forEach { color ->
            val swatch = View(this).apply {
                background = rounded(color, dp(6).toFloat(), if (note.color == color) WoWPalette.TEXT else WoWPalette.LINE, dp(if (note.color == color) 2 else 1))
                setOnClickListener { io.execute { db.save(note.copy(color = color)); main.post { loadNotes(search.text.toString()); dialog.dismiss() } } }
            }
            box.addView(swatch, GridLayout.LayoutParams().apply { width = 0; height = dp(54); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
        dialog.show()
    }

    override fun onDestroy() { main.removeCallbacksAndMessages(null); io.shutdown(); db.close(); super.onDestroy() }
}
