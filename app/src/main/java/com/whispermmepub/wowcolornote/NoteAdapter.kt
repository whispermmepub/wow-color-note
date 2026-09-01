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
    private val dateFmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    fun submit(notes: List<Note>) { items.clear(); items.addAll(notes); notifyDataSetChanged() }
    fun setTypeface(tf: Typeface) { font = tf; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val c = parent.context
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(c.dp(13), c.dp(12), c.dp(13), c.dp(12))
            elevation = c.dp(2).toFloat()
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(c.dp(4), c.dp(6), c.dp(4), c.dp(6))
            }
        }
        val strip = android.view.View(c).apply {
            layoutParams = LinearLayout.LayoutParams(c.dp(5), ViewGroup.LayoutParams.MATCH_PARENT)
            background = rounded(WoWPalette.ACCENT, c.dp(4).toFloat())
        }
        val textWrap = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(c.dp(13), 0, 0, 0)
        }
        val title = TextView(c).apply {
            setTextColor(WoWPalette.TEXT)
            textSize = 17f
            maxLines = 1
            setTypeface(font, Typeface.BOLD)
        }
        val body = TextView(c).apply {
            setTextColor(WoWPalette.MUTED)
            textSize = 14f
            maxLines = 2
            typeface = font
            setLineSpacing(0f, 1.12f)
        }
        val meta = TextView(c).apply {
            setTextColor(WoWPalette.MUTED)
            textSize = 11f
            setPadding(0, c.dp(6), 0, 0)
        }
        textWrap.addView(title)
        textWrap.addView(body)
        textWrap.addView(meta)
        root.addView(strip)
        root.addView(textWrap, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return Holder(root, strip, title, body, meta)
    }

    override fun onBindViewHolder(h: Holder, position: Int) {
        val n = items[position]
        val cardColor = if (n.pinned) WoWPalette.CARD_ALT else WoWPalette.CARD
        h.root.background = rounded(cardColor, h.root.context.dp(18).toFloat(), WoWPalette.LINE, h.root.context.dp(1))
        h.strip.background = rounded(n.color, h.root.context.dp(4).toFloat())
        h.title.text = (if (n.pinned) "★  " else "") + n.title.ifBlank { "Untitled" }
        h.body.text = n.body.replace('\n', ' ').trim().ifBlank { "Empty note" }
        h.meta.text = dateFmt.format(Date(n.updatedAt))
        h.title.typeface = Typeface.create(font, Typeface.BOLD)
        h.body.typeface = font
        h.root.setOnClickListener { onClick(n) }
        h.root.setOnLongClickListener { onLongClick(n); true }
    }

    override fun getItemCount() = items.size

    class Holder(
        val root: LinearLayout,
        val strip: android.view.View,
        val title: TextView,
        val body: TextView,
        val meta: TextView
    ) : RecyclerView.ViewHolder(root)
}
