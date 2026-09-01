package com.whispermmepub.wowcolornote

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.whispermmepub.wowcolornote.data.NoteDb
import com.whispermmepub.wowcolornote.font.FontManager
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

    private val fontPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            io.execute {
                val ok = FontManager.import(this, uri)
                main.post {
                    if (ok) {
                        adapter.setTypeface(FontManager.typeface(this))
                        Toast.makeText(this, "Custom font applied", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(this, "Font file မဖတ်နိုင်ပါ", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = WoWPalette.BG
        window.navigationBarColor = WoWPalette.BG
        db = NoteDb(this)
        adapter = NoteAdapter(FontManager.typeface(this), ::openNote, ::noteMenu)
        setContentView(buildUi())
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
            setPadding(dp(11), dp(8), dp(11), dp(8))
            setOnClickListener { click() }
        }
        top.addView(action("Aa") { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream")) })
        top.addView(action("▦") { startActivity(Intent(this, CalendarActivity::class.java)) })
        top.addView(action("＋") { openNote(null) })

        search = EditText(this).apply {
            hint = "Search notes"
            textSize = 16f
            setSingleLine(true)
            setTextColor(WoWPalette.TEXT)
            setHintTextColor(WoWPalette.MUTED)
            background = rounded(WoWPalette.CARD, dp(18).toFloat(), WoWPalette.LINE, dp(1))
            setPadding(dp(16), dp(11), dp(16), dp(11))
        }
        val searchParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(4), dp(10), dp(4), dp(6))
        }
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

        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            setHasFixedSize(false)
            itemAnimator = null
            setBackgroundColor(WoWPalette.BG)
            clipToPadding = false
            setPadding(0, dp(2), 0, dp(16))
        }
        root.addView(top)
        root.addView(search, searchParams)
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun loadNotes(q: String) {
        io.execute {
            val notes = db.list(q)
            main.post { adapter.submit(notes) }
        }
    }

    private fun openNote(note: com.whispermmepub.wowcolornote.model.Note?) {
        startActivity(Intent(this, EditorActivity::class.java).putExtra("note_id", note?.id ?: 0L))
    }

    private fun noteMenu(note: com.whispermmepub.wowcolornote.model.Note) {
        AlertDialog.Builder(this)
            .setTitle(note.title.ifBlank { "Untitled" })
            .setItems(arrayOf("Open", "Delete")) { _, which ->
                if (which == 0) openNote(note)
                else AlertDialog.Builder(this)
                    .setMessage("Delete this note?")
                    .setPositiveButton("Delete") { _, _ ->
                        io.execute { db.delete(note.id); main.post { loadNotes(search.text.toString()) } }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        io.shutdown()
        db.close()
    }
}
