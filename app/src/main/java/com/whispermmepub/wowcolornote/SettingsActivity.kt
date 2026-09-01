package com.whispermmepub.wowcolornote

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.whispermmepub.wowcolornote.data.NoteDb
import com.whispermmepub.wowcolornote.font.FontManager
import com.whispermmepub.wowcolornote.model.Note
import com.whispermmepub.wowcolornote.ui.WoWPalette
import com.whispermmepub.wowcolornote.ui.dp
import com.whispermmepub.wowcolornote.ui.rounded
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

class SettingsActivity : AppCompatActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private val homePrefs by lazy { getSharedPreferences("wow_note_home", MODE_PRIVATE) }
    private val readerPrefs by lazy { getSharedPreferences("wow_note_reader", MODE_PRIVATE) }
    private val securityPrefs by lazy { getSharedPreferences("wow_note_security", MODE_PRIVATE) }

    private val fontPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) io.execute {
            val ok = FontManager.import(this, uri)
            runOnUiThread {
                Toast.makeText(this, if (ok) "Custom font applied" else "Font file မဖတ်နိုင်ပါ", Toast.LENGTH_SHORT).show()
                if (ok) render()
            }
        }
    }

    private val backupWriter = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) io.execute {
            runCatching {
                val db = NoteDb(this)
                val arr = JSONArray()
                db.allNotes().forEach { n -> arr.put(noteToJson(n)) }
                db.close()
                val root = JSONObject().put("format", "wow-note-backup-v1").put("notes", arr)
                contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(root.toString()) }
            }.onSuccess {
                runOnUiThread { Toast.makeText(this, "Backup completed", Toast.LENGTH_SHORT).show() }
            }.onFailure {
                runOnUiThread { Toast.makeText(this, "Backup failed", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private val backupReader = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) io.execute {
            runCatching {
                val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("Empty backup")
                val root = JSONObject(text)
                val arr = root.getJSONArray("notes")
                val notes = ArrayList<Note>(arr.length())
                for (i in 0 until arr.length()) notes += jsonToNote(arr.getJSONObject(i))
                val db = NoteDb(this)
                db.restoreAll(notes)
                db.close()
                notes.size
            }.onSuccess { count ->
                runOnUiThread { Toast.makeText(this, "Restored $count notes", Toast.LENGTH_LONG).show() }
            }.onFailure {
                runOnUiThread { Toast.makeText(this, "Restore failed", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = WoWPalette.BG
        window.navigationBarColor = WoWPalette.BG
        render()
    }

    private fun render() {
        val scroll = ScrollView(this).apply { setBackgroundColor(WoWPalette.BG); clipToPadding = false }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(28))
        }

        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(2), 0, dp(2), dp(8)) }
        head.addView(TextView(this).apply {
            text = "‹"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(WoWPalette.ACCENT)
            setPadding(dp(8), dp(5), dp(14), dp(5))
            setOnClickListener { finish() }
        })
        head.addView(TextView(this).apply {
            text = "Settings"
            textSize = 27f
            setTextColor(WoWPalette.TEXT)
            setTypeface(FontManager.typeface(this@SettingsActivity), Typeface.BOLD)
        })
        root.addView(head)

        section(root, "Display")
        row(root, "Default Screen", when (homePrefs.getString("default_screen", "notes")) { "calendar" -> "Calendar"; else -> "Notes" }) {
            choice("Default Screen", arrayOf("Notes", "Calendar"), if (homePrefs.getString("default_screen", "notes") == "calendar") 1 else 0) { i ->
                homePrefs.edit().putString("default_screen", if (i == 1) "calendar" else "notes").apply(); render()
            }
        }
        row(root, "Default View", viewLabel(homePrefs.getString("view", "details") ?: "details")) {
            val values = arrayOf("list", "details", "grid", "large_grid")
            val labels = arrayOf("List", "Details", "Grid", "Large grid")
            choice("Default View", labels, values.indexOf(homePrefs.getString("view", "details"))) { i ->
                homePrefs.edit().putString("view", values[i]).apply(); render()
            }
        }
        row(root, "Default Notes Sort Order", sortLabel(homePrefs.getString("sort", "modified") ?: "modified")) {
            val values = arrayOf("modified", "created", "title")
            val labels = arrayOf("Modified time", "Created time", "Title")
            choice("Sort Order", labels, values.indexOf(homePrefs.getString("sort", "modified"))) { i ->
                homePrefs.edit().putString("sort", values[i]).apply(); render()
            }
        }

        section(root, "Reading")
        row(root, "Default Font Size", "${readerPrefs.getFloat("reader_size", 18f).toInt()} sp") {
            val sizes = intArrayOf(14, 16, 18, 20, 22, 24, 28, 32)
            val current = readerPrefs.getFloat("reader_size", 18f).toInt()
            choice("Default Font Size", sizes.map { "$it sp" }.toTypedArray(), sizes.indexOf(current).let { if (it < 0) 2 else it }) { i ->
                readerPrefs.edit().putFloat("reader_size", sizes[i].toFloat()).apply(); render()
            }
        }
        row(root, "Line Height", String.format(java.util.Locale.US, "%.2f", readerPrefs.getFloat("line_height", 1.24f))) {
            val vals = floatArrayOf(1.0f, 1.12f, 1.24f, 1.36f, 1.5f, 1.7f, 2.0f)
            val current = readerPrefs.getFloat("line_height", 1.24f)
            var selected = vals.indices.minByOrNull { kotlin.math.abs(vals[it] - current) } ?: 2
            choice("Line Height", vals.map { String.format(java.util.Locale.US, "%.2f", it) }.toTypedArray(), selected) { i ->
                readerPrefs.edit().putFloat("line_height", vals[i]).apply(); render()
            }
        }
        row(root, "Custom Font", "Import TTF / OTF") {
            fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream"))
        }

        section(root, "Security")
        row(root, "Master Password", if (securityPrefs.getString("master_pin", "").isNullOrBlank()) "Not set" else "Set") { setMasterPin() }
        if (!securityPrefs.getString("master_pin", "").isNullOrBlank()) {
            row(root, "Clear Master Password", "Locked notes will remain locked until reopened") { clearMasterPin() }
        }

        section(root, "Backup")
        row(root, "Backup Notes", "Save all notes to a JSON backup") { backupWriter.launch("WoW-Note-backup.json") }
        row(root, "Restore Notes", "Replace current notes from a backup file") {
            AlertDialog.Builder(this).setTitle("Restore Notes")
                .setMessage("Current notes will be replaced by the backup. Continue?")
                .setPositiveButton("Restore") { _, _ -> backupReader.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                .setNegativeButton("Cancel", null).show()
        }

        section(root, "About")
        row(root, "WoW Note", "ColorNote-style Myanmar note app • Premium Navy") { }

        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(scroll)
    }

    private fun section(root: LinearLayout, textValue: String) {
        root.addView(TextView(this).apply {
            text = textValue.uppercase()
            textSize = 12f
            setTextColor(WoWPalette.ACCENT)
            setPadding(dp(12), dp(18), dp(12), dp(6))
        })
    }

    private fun row(root: LinearLayout, title: String, subtitle: String, click: () -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(13))
            background = rounded(WoWPalette.CARD, dp(14).toFloat(), WoWPalette.LINE, dp(1))
            setOnClickListener { click() }
        }
        box.addView(TextView(this).apply { text = title; textSize = 17f; setTextColor(WoWPalette.TEXT) })
        box.addView(TextView(this).apply { text = subtitle; textSize = 13f; setTextColor(WoWPalette.MUTED); setPadding(0, dp(3), 0, 0) })
        root.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(4), 0, dp(4))
        })
    }

    private fun choice(title: String, labels: Array<String>, selected: Int, onSelected: (Int) -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels, selected.coerceAtLeast(0)) { d, which ->
            onSelected(which); d.dismiss()
        }.show()
    }

    private fun setMasterPin() {
        val existing = securityPrefs.getString("master_pin", "").orEmpty()
        val input = EditText(this).apply {
            hint = if (existing.isBlank()) "New PIN (4+ digits)" else "New PIN (4+ digits)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this).setTitle(if (existing.isBlank()) "Set Master Password" else "Change Master Password")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val pin = input.text.toString()
                if (pin.length >= 4) { securityPrefs.edit().putString("master_pin", pin).apply(); Toast.makeText(this, "Master password saved", Toast.LENGTH_SHORT).show(); render() }
                else Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun clearMasterPin() {
        AlertDialog.Builder(this).setTitle("Clear Master Password")
            .setMessage("Remove the saved master password?")
            .setPositiveButton("Clear") { _, _ -> securityPrefs.edit().remove("master_pin").apply(); render() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun viewLabel(v: String) = when (v) { "list" -> "List"; "grid" -> "Grid"; "large_grid" -> "Large grid"; else -> "Details" }
    private fun sortLabel(v: String) = when (v) { "created" -> "Created time"; "title" -> "Title"; else -> "Modified time" }

    private fun noteToJson(n: Note) = JSONObject()
        .put("id", n.id).put("title", n.title).put("body", n.body).put("color", n.color)
        .put("pinned", n.pinned).put("noteType", n.noteType).put("archived", n.archived)
        .put("locked", n.locked).put("calendarDate", n.calendarDate).put("reminderAt", n.reminderAt)
        .put("createdAt", n.createdAt).put("updatedAt", n.updatedAt)

    private fun jsonToNote(o: JSONObject) = Note(
        id = o.optLong("id", 0L),
        title = o.optString("title", ""),
        body = o.optString("body", ""),
        color = o.optInt("color", WoWPalette.NOTE_COLORS.first()),
        pinned = o.optBoolean("pinned", false),
        noteType = o.optString("noteType", "text"),
        archived = o.optBoolean("archived", false),
        locked = o.optBoolean("locked", false),
        calendarDate = o.optString("calendarDate", ""),
        reminderAt = o.optLong("reminderAt", 0L),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
    )

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }
}
