package com.whispermmepub.wowcolornote

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
    private val prefs by lazy { getSharedPreferences("wow_note_home", MODE_PRIVATE) }
    private var sortMode = "modified"
    private var viewMode = "details"

    private val fontPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) io.execute {
            val ok = FontManager.import(this, uri)
            main.post {
                if (ok) {
                    adapter.setTypeface(FontManager.typeface(this))
                    Toast.makeText(this, "Custom font applied", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this, "Font file မဖတ်နိုင်ပါ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = WoWPalette.BG
        window.navigationBarColor = WoWPalette.BG
        sortMode = prefs.getString("sort", "modified") ?: "modified"
        viewMode = prefs.getString("view", "details") ?: "details"
        db = NoteDb(this)
        adapter = NoteAdapter(FontManager.typeface(this), ::openNote, ::noteMenu).apply { setViewMode(viewMode) }
        setContentView(buildUi())
        applyViewMode()
        updateSortBar()
        loadNotes("")
    }

    override fun onResume() {
        super.onResume()
        if (::search.isInitialized) loadNotes(search.text.toString())
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(WoWPalette.BG)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(8), dp(10))
            background = rounded(WoWPalette.SURFACE, dp(20).toFloat(), WoWPalette.LINE, dp(1))
        }
        val title = TextView(this).apply {
            text = "WoW Note"
            textSize = 25f
            setTextColor(WoWPalette.TEXT)
            setTypeface(FontManager.typeface(this@MainActivity), Typeface.BOLD)
        }
        top.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        fun action(label: String, click: () -> Unit) = TextView(this).apply {
            text = label
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(WoWPalette.ACCENT)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { click() }
        }
        top.addView(action("Aa") { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream")) })
        top.addView(action("▣") { showViewDialog() })
        top.addView(action("▦") { startActivity(Intent(this, CalendarActivity::class.java)) })
        top.addView(action("＋") { openNote(null) })

        sortBar = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(WoWPalette.MUTED)
            background = rounded(WoWPalette.SURFACE, dp(14).toFloat(), WoWPalette.LINE, dp(1))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { showSortDialog() }
        }
        root.addView(top)
        root.addView(sortBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(4), dp(9), dp(4), dp(6))
        })

        search = EditText(this).apply {
            hint = "Search notes"
            textSize = 16f
            setSingleLine(true)
            setTextColor(WoWPalette.TEXT)
            setHintTextColor(WoWPalette.MUTED)
            background = rounded(WoWPalette.CARD, dp(18).toFloat(), WoWPalette.LINE, dp(1))
            setPadding(dp(16), dp(11), dp(16), dp(11))
        }
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(4), 0, dp(4), dp(6))
        })
        search.addTextChangedListener(object : android.text.TextWatcher {
            private var task: Runnable? = null
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                task?.let(main::removeCallbacks)
                task = Runnable { loadNotes(s?.toString().orEmpty()) }
                main.postDelayed(task!!, 120)
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        list = RecyclerView(this).apply {
            adapter = this@MainActivity.adapter
            setHasFixedSize(false)
            itemAnimator = null
            setBackgroundColor(WoWPalette.BG)
            clipToPadding = false
            setPadding(0, dp(2), 0, dp(18))
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun showSortDialog() {
        val labels = arrayOf("Modified time", "Created time", "Title")
        val values = arrayOf("modified", "created", "title")
        AlertDialog.Builder(this).setTitle("Sort by").setSingleChoiceItems(labels, values.indexOf(sortMode)) { d, which ->
            sortMode = values[which]
            prefs.edit().putString("sort", sortMode).apply()
            updateSortBar()
            loadNotes(search.text.toString())
            d.dismiss()
        }.show()
    }

    private fun showViewDialog() {
        val labels = arrayOf("☰  List", "▤  Details", "▦  Grid", "▧  Large grid")
        val values = arrayOf("list", "details", "grid", "large_grid")
        AlertDialog.Builder(this).setTitle("View").setSingleChoiceItems(labels, values.indexOf(viewMode)) { d, which ->
            viewMode = values[which]
            prefs.edit().putString("view", viewMode).apply()
            applyViewMode()
            d.dismiss()
        }.show()
    }

    private fun updateSortBar() {
        sortBar.text = when (sortMode) {
            "created" -> "Sort by created time  ▼"
            "title" -> "Sort by title  ▼"
            else -> "Sort by modified time  ▼"
        }
    }

    private fun applyViewMode() {
        adapter.setViewMode(viewMode)
        list.layoutManager = when (viewMode) {
            "grid" -> GridLayoutManager(this, 2)
            "large_grid" -> GridLayoutManager(this, 2)
            else -> LinearLayoutManager(this)
        }
    }

    private fun loadNotes(q: String) {
        io.execute {
            val notes = db.list(q, sortMode)
            main.post { adapter.submit(notes) }
        }
    }

    private fun openNote(note: Note?) {
        startActivity(Intent(this, EditorActivity::class.java).putExtra("note_id", note?.id ?: 0L))
    }

    private fun noteMenu(note: Note) {
        AlertDialog.Builder(this)
            .setTitle(note.title.ifBlank { "Untitled" })
            .setItems(arrayOf("Open", "Color", if (note.pinned) "Unpin" else "Pin", "Delete")) { _, which ->
                when (which) {
                    0 -> openNote(note)
                    1 -> showColorPicker(note)
                    2 -> io.execute { db.save(note.copy(pinned = !note.pinned)); main.post { loadNotes(search.text.toString()) } }
                    3 -> AlertDialog.Builder(this).setMessage("Delete this note?")
                        .setPositiveButton("Delete") { _, _ -> io.execute { db.delete(note.id); main.post { loadNotes(search.text.toString()) } } }
                        .setNegativeButton("Cancel", null).show()
                }
            }.show()
    }

    private fun showColorPicker(note: Note) {
        val box = GridLayout(this).apply {
            columnCount = 2
            setPadding(dp(18), dp(12), dp(18), dp(18))
        }
        val dialog = AlertDialog.Builder(this).setTitle("Color").setView(box).setNegativeButton("Cancel", null).create()
        WoWPalette.NOTE_COLORS.forEach { color ->
            val swatch = View(this).apply {
                background = rounded(color, dp(10).toFloat(), if (note.color == color) WoWPalette.TEXT else WoWPalette.LINE, dp(if (note.color == color) 2 else 1))
                setOnClickListener {
                    io.execute { db.save(note.copy(color = color)); main.post { loadNotes(search.text.toString()); dialog.dismiss() } }
                }
            }
            box.addView(swatch, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(64)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(5), dp(5), dp(5), dp(5))
            })
        }
        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        io.shutdown()
        db.close()
    }
}
