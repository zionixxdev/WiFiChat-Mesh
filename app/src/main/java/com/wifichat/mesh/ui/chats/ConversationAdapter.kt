package com.wifichat.mesh.ui.chats

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.wifichat.mesh.R
import com.wifichat.mesh.databinding.ItemConversationBinding
import com.wifichat.mesh.model.Conversation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationAdapter(
    private val onClick: (Conversation) -> Unit,
    private val onTogglePin: (Conversation) -> Unit,
    private val onDelete: (Conversation) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.VH>() {

    private val items = mutableListOf<Conversation>()
    private val timeFmt = SimpleDateFormat("dd MMM", Locale.getDefault())

    fun submit(list: List<Conversation>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(o: Int, n: Int) =
                items[o].conversationId == list[n].conversationId
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == list[n]
        })
        items.clear(); items.addAll(list)
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class VH(private val b: ItemConversationBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(c: Conversation) {
            b.title.text = c.title
            b.preview.text = c.lastMessagePreview ?: b.root.context.getString(R.string.no_messages_yet)
            b.avatar.text = initials(c.title)
            b.time.text = if (c.lastMessageAt > 0) timeFmt.format(Date(c.lastMessageAt)) else ""

            if (c.unreadCount > 0) {
                b.unreadBadge.visibility = View.VISIBLE
                b.unreadBadge.text = if (c.unreadCount > 99) "99+" else c.unreadCount.toString()
            } else {
                b.unreadBadge.visibility = View.GONE
            }
            b.pinIcon.visibility = if (c.pinned) View.VISIBLE else View.GONE

            b.root.setOnClickListener { onClick(c) }
            b.root.setOnLongClickListener {
                val popup = android.widget.PopupMenu(it.context, it)
                popup.menu.add(0, 1, 0, if (c.pinned) R.string.unpin else R.string.pin)
                popup.menu.add(0, 2, 1, R.string.delete_conversation)
                popup.setOnMenuItemClickListener { mi ->
                    when (mi.itemId) {
                        1 -> onTogglePin(c)
                        2 -> onDelete(c)
                    }
                    true
                }
                popup.show()
                true
            }
        }

        private fun initials(name: String): String {
            val parts = name.trim().split(" ").filter { it.isNotEmpty() }
            return when {
                parts.isEmpty() -> "?"
                parts.size == 1 -> parts[0].take(1).uppercase()
                else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
            }
        }
    }
}
