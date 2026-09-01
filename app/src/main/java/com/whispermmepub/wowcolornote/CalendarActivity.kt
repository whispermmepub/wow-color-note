package com.whispermmepub.wowcolornote

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.whispermmepub.wowcolornote.calendar.MyanmarCalendarUtil
import com.whispermmepub.wowcolornote.font.FontManager
import com.whispermmepub.wowcolornote.ui.WoWPalette
import com.whispermmepub.wowcolornote.ui.dp
import com.whispermmepub.wowcolornote.ui.rounded
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

class CalendarActivity : AppCompatActivity() {
    private var month = YearMonth.now()
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }

    private fun render() {
        val tf = FontManager.typeface(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(WoWPalette.BG); setPadding(dp(10), dp(8), dp(10), dp(12)) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun nav(t: String, click: () -> Unit) = TextView(this).apply { text = t; textSize = 24f; gravity = Gravity.CENTER; setTextColor(WoWPalette.ACCENT); setPadding(dp(14), dp(10), dp(14), dp(10)); setOnClickListener { click() } }
        val monthTitle = TextView(this).apply { text = "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(WoWPalette.TEXT); typeface = tf }
        header.addView(nav("‹") { month = month.minusMonths(1); render() }); header.addView(monthTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); header.addView(nav("›") { month = month.plusMonths(1); render() })
        root.addView(header)

        val dow = GridLayout(this).apply { columnCount = 7 }
        listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun").forEach { d -> dow.addView(TextView(this).apply { text = d; gravity = Gravity.CENTER; textSize = 11f; setTextColor(WoWPalette.MUTED) }, GridLayout.LayoutParams().apply { width = 0; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) }) }
        root.addView(dow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)))

        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply { columnCount = 7; rowCount = 6; alignmentMode = GridLayout.ALIGN_BOUNDS }
        val first = month.atDay(1)
        val offset = first.dayOfWeek.value - 1
        repeat(offset) { grid.addView(TextView(this), cellParams()) }
        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val info = MyanmarCalendarUtil.info(date)
            val isToday = date == LocalDate.now()
            val cell = TextView(this).apply {
                text = buildString { append(day); append('\n'); append(info.monthName); append(' '); append(info.moonPhase); if (info.fortnightDay.isNotBlank()) { append('\n'); append(info.fortnightDay) } }
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; textSize = 10.5f; setTextColor(WoWPalette.TEXT); typeface = tf; setPadding(dp(2), dp(5), dp(2), dp(3))
                background = rounded(if (isToday) 0xFFFFE8B7.toInt() else WoWPalette.SURFACE, dp(10).toFloat(), WoWPalette.LINE, dp(1))
            }
            grid.addView(cell, cellParams())
        }
        val remaining = (42 - offset - month.lengthOfMonth()).coerceAtLeast(0)
        repeat(remaining) { grid.addView(TextView(this), cellParams()) }
        scroll.addView(grid, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun cellParams() = GridLayout.LayoutParams().apply {
        width = 0; height = dp(86); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(2), dp(2), dp(2), dp(2))
    }
}
