package com.whispermmepub.wowcolornote

import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.whispermmepub.wowcolornote.model.Note
import com.whispermmepub.wowcolornote.ui.WoWPalette
import com.whispermmepub.wowcolornote.ui.dp
import com.whispermmepub.wowcolornote.ui.rounded
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteAdapter(
    private var font: Typeface,
    private val onClick: (Note) -> Unit,
    private val onLongClick: (Note) -> Unit
) : RecyclerView.Adapter<NoteAdapter.Holder>() {
    private val items = ArrayList<Note>()
    private val dateFmt = SimpleDateFormat("MMM d", Locale.getDefault())
    private var viewMode = "details"

    fun submit(notes: List<Note>) { items.clear(); items.addAll(notes); notifyDataSetChanged() }
    fun setTypeface(tf: Typeface) { font = tf; notifyDataSetChanged() }
    fun setViewMode(mode: String) { viewMode = mode; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val c = parent.context
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(c.dp(13), c.dp(12), c.dp(13), c.dp(12))
            elevation = c.dp(2).toFloat()
        }
        val strip = android.view.View(c)
        val textWrap = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(c).apply { setTextColor(WoWPalette.TEXT); setTypeface(font, Typeface.BOLD) }
        val body = TextView(c).apply { setTextColor(WoWPalette.MUTED); typeface = font; setLineSpacing(0f, 1.12f) }
        val meta = TextView(c).apply { setTextColor(WoWPalette.MUTED) }
        textWrap.addView(title)
        textWrap.addView(body)
        textWrap.addView(meta)
        root.addView(strip)
        root.addView(textWrap, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return Holder(root, strip, textWrap, title, body, meta)
    }

    override fun onBindViewHolder(h: Holder, position: Int) {
        val n = items[position]
        val c = h.root.context
        val grid = viewMode == "grid" || viewMode == "large_grid"
        val compact = viewMode == "list"

        h.root.orientation = if (grid) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        h.root.gravity = if (grid) Gravity.START else Gravity.CENTER_VERTICAL
        h.root.setPadding(c.dp(13), c.dp(if (grid) 14 else 10), c.dp(13), c.dp(if (grid) 14 else 10))
        h.root.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(c.dp(5), c.dp(5), c.dp(5), c.dp(5))
        }
        h.root.background = rounded(if (n.pinned) WoWPalette.CARD_ALT else WoWPalette.CARD, c.dp(18).toFloat(), WoWPalette.LINE, c.dp(1))

        h.strip.background = rounded(n.color, c.dp(4).toFloat())
        h.strip.layoutParams = if (grid) {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, c.dp(5)).apply { setMargins(0, 0, 0, c.dp(10)) }
        } else {
            LinearLayout.LayoutParams(c.dp(5), if (compact) c.dp(42) else ViewGroup.LayoutParams.MATCH_PARENT).apply { setMargins(0, 0, c.dp(12), 0) }
        }
        h.textWrap.setPadding(0, 0, 0, 0)

        h.title.text = (if (n.pinned) "★  " else "") + n.title.ifBlank { "Untitled" }
        h.title.textSize = if (grid) 16f else 17f
        h.title.maxLines = if (grid) 2 else 1
        h.title.typeface = Typeface.create(font, Typeface.BOLD)

        h.body.text = n.body.replace('\n', ' ').trim().ifBlank { "Empty note" }
        h.body.textSize = if (grid) 13f else 14f
        h.body.maxLines = when (viewMode) { "list" -> 0; "large_grid" -> 6; "grid" -> 4; else -> 2 }
        h.body.visibility = if (compact) android.view.View.GONE else android.view.View.VISIBLE
        h.body.typeface = font

        h.meta.text = dateFmt.format(Date(n.updatedAt))
        h.meta.textSize = 11f
        h.meta.setPadding(0, c.dp(6), 0, 0)

        h.root.setOnClickListener { onClick(n) }
        h.root.setOnLongClickListener { onLongClick(n); true }
    }

    override fun getItemCount() = items.size

    class Holder(
        val root: LinearLayout,
        val strip: android.view.View,
        val textWrap: LinearLayout,
        val title: TextView,
        val body: TextView,
        val meta: TextView
    ) : RecyclerView.ViewHolder(root)
}
