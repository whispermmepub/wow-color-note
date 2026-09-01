package com.whispermmepub.wowcolornote

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.whispermmepub.wowcolornote.calendar.MyanmarCalendarUtil
import com.whispermmepub.wowcolornote.data.NoteDb
import com.whispermmepub.wowcolornote.font.FontManager
import com.whispermmepub.wowcolornote.ui.WoWPalette
import com.whispermmepub.wowcolornote.ui.dp
import com.whispermmepub.wowcolornote.ui.rounded
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.Executors

class CalendarActivity : AppCompatActivity() {
    private var month = YearMonth.now()
    private lateinit var root: LinearLayout
    private lateinit var db: NoteDb
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = WoWPalette.BG
        window.navigationBarColor = WoWPalette.BG
        db = NoteDb(this)
        render()
    }

    private fun render() {
        val tf = FontManager.typeface(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(WoWPalette.BG)
            setPadding(dp(8), dp(8), dp(8), 0)
        }
        val titleBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(8), dp(6))
        }
        titleBar.addView(TextView(this).apply {
            text = "Calendar"
            textSize = 26f
            setTextColor(WoWPalette.TEXT)
            typeface = android.graphics.Typeface.create(tf, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        titleBar.addView(TextView(this).apply {
            text = "Today"
            textSize = 14f
            setTextColor(WoWPalette.ACCENT)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { month = YearMonth.now(); render() }
        })
        root.addView(titleBar)

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
            background = rounded(WoWPalette.SURFACE, dp(12).toFloat(), WoWPalette.LINE, dp(1))
        }
        fun nav(t: String, click: () -> Unit) = TextView(this).apply {
            text = t; textSize = 22f; gravity = Gravity.CENTER; setTextColor(WoWPalette.ACCENT)
            setPadding(dp(14), dp(8), dp(14), dp(8)); setOnClickListener { click() }
        }
        val monthTitle = TextView(this).apply {
            text = "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}"
            textSize = 18f; gravity = Gravity.CENTER; setTextColor(WoWPalette.TEXT); typeface = tf
        }
        header.addView(nav("▲") { month = month.minusMonths(1); render() })
        header.addView(monthTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(nav("▼") { month = month.plusMonths(1); render() })
        root.addView(header)

        val dow = GridLayout(this).apply { columnCount = 7; setPadding(0, dp(5), 0, dp(2)) }
        listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN").forEachIndexed { i, d ->
            dow.addView(TextView(this).apply {
                text = d; gravity = Gravity.CENTER; textSize = 10f
                setTextColor(if (i >= 5) WoWPalette.ACCENT else WoWPalette.MUTED)
            }, GridLayout.LayoutParams().apply { width = 0; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) })
        }
        root.addView(dow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)))

        val scroll = ScrollView(this).apply { clipToPadding = false }
        val grid = GridLayout(this).apply { columnCount = 7; rowCount = 6; alignmentMode = GridLayout.ALIGN_BOUNDS }
        val first = month.atDay(1)
        val offset = first.dayOfWeek.value - 1
        repeat(offset) { grid.addView(TextView(this), cellParams()) }
        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val info = MyanmarCalendarUtil.info(date)
            val isToday = date == LocalDate.now()
            val textValue = buildString {
                append(day)
                append('\n')
                append(info.monthName)
                if (info.moonPhase.isNotBlank()) { append(' '); append(info.moonPhase) }
                if (info.fortnightDay.isNotBlank()) { append('\n'); append(info.fortnightDay) }
            }
            val cell = TextView(this).apply {
                text = textValue
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                textSize = 9.6f
                setTextColor(WoWPalette.TEXT)
                typeface = FontManager.typefaceForText(this@CalendarActivity, textValue)
                setPadding(dp(2), dp(5), dp(2), dp(3))
                includeFontPadding = true
                background = rounded(if (isToday) WoWPalette.TODAY else WoWPalette.CARD, dp(7).toFloat(), if (isToday) WoWPalette.ACCENT else WoWPalette.LINE, dp(1))
                setOnClickListener { showDate(date) }
            }
            grid.addView(cell, cellParams())
        }
        val remaining = (42 - offset - month.lengthOfMonth()).coerceAtLeast(0)
        repeat(remaining) { grid.addView(TextView(this), cellParams()) }
        scroll.addView(grid, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNav())
        setContentView(root)
    }

    private fun showDate(date: LocalDate) {
        val key = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        io.execute {
            val notes = db.listForDate(key)
            main.post {
                val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(8)) }
                notes.forEach { n ->
                    box.addView(TextView(this).apply {
                        text = (if (n.noteType == "checklist") "☑  " else "") + n.title.ifBlank { "Untitled" }
                        textSize = 16f; setTextColor(WoWPalette.TEXT); setPadding(dp(8), dp(10), dp(8), dp(10))
                        setOnClickListener { startActivity(Intent(this@CalendarActivity, EditorActivity::class.java).putExtra("note_id", n.id)) }
                    })
                }
                if (notes.isEmpty()) box.addView(TextView(this).apply {
                    text = "ဒီနေ့အတွက် note မရှိသေးပါ"; setTextColor(WoWPalette.MUTED); textSize = 14f; setPadding(dp(8), dp(16), dp(8), dp(16))
                })
                AlertDialog.Builder(this)
                    .setTitle(date.format(DateTimeFormatter.ofPattern("EEE, d MMMM")))
                    .setView(box)
                    .setPositiveButton("Add") { _, _ -> chooseNoteType(key) }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }
    }

    private fun chooseNoteType(date: String) {
        AlertDialog.Builder(this).setTitle("Add note").setItems(arrayOf("Text note", "Checklist")) { _, which ->
            startActivity(Intent(this, EditorActivity::class.java)
                .putExtra("note_id", 0L)
                .putExtra("note_type", if (which == 1) "checklist" else "text")
                .putExtra("calendar_date", date))
        }.show()
    }

    private fun bottomNav(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(5), 0, dp(5))
            background = rounded(WoWPalette.SURFACE, 0f, WoWPalette.LINE, dp(1))
        }
        fun item(icon: String, active: Boolean = false, action: () -> Unit) = TextView(this).apply {
            text = icon; textSize = 24f; gravity = Gravity.CENTER
            setTextColor(if (active) WoWPalette.ACCENT else WoWPalette.MUTED)
            setPadding(0, dp(7), 0, dp(7)); setOnClickListener { action() }
        }
        bar.addView(item("▤") { finish() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(item("▦", true) {}, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(item("★") { startActivity(Intent(this, MainActivity::class.java).putExtra("filter_pinned", true)); finish() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(item("⌕") { startActivity(Intent(this, MainActivity::class.java).putExtra("focus_search", true)); finish() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(item("☰") { AlertDialog.Builder(this).setItems(arrayOf("Today", "Go to notes")) { _, w -> if (w == 0) { month = YearMonth.now(); render() } else finish() }.show() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return bar
    }

    private fun cellParams() = GridLayout.LayoutParams().apply {
        width = 0; height = dp(78); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(1), dp(1), dp(1), dp(1))
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null); io.shutdown(); db.close(); super.onDestroy()
    }
}
