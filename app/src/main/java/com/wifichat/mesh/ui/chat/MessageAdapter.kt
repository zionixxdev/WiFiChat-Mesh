package com.wifichat.mesh.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.wifichat.mesh.R
import com.wifichat.mesh.databinding.ItemMessageBinding
import com.wifichat.mesh.model.DeliveryState
import com.wifichat.mesh.model.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders message bubbles. Sent vs received styling is distinct, timestamps are
 * relative, and the delivery tick reflects the REAL [DeliveryState] — never a
 * fabricated "delivered" state.
 */
class MessageAdapter(
    private val onRetry: (Message) -> Unit,
    private val onCopy: (Message) -> Unit,
    private val onReply: (Message) -> Unit,
    private val onDelete: (Message) -> Unit
) : RecyclerView.Adapter<MessageAdapter.VH>() {

    private val items = mutableListOf<Message>()
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun submit(list: List<Message>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(o: Int, n: Int) = items[o].messageId == list[n].messageId
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == list[n]
        })
        items.clear()
        items.addAll(list)
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class VH(private val b: ItemMessageBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(m: Message) {
            b.messageBody.text = m.body
            b.messageTime.text = timeFmt.format(Date(m.createdAt))

            val isOut = m.outgoing
            b.bubble.setBackgroundResource(
                if (isOut) R.drawable.bg_bubble_sent else R.drawable.bg_bubble_received
            )
            // Align sent bubbles to the right, received to the left. The root
            // view carries no generated binding field; b.root is the LinearLayout.
            b.root.gravity = if (isOut) {
                android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            } else {
                android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            }
            b.messageBody.setTextColor(
                b.root.context.getColor(
                    if (isOut) R.color.bubble_sent_text else R.color.bubble_received_text
                )
            )

            // Delivery indicator: only shown for outgoing messages.
            b.statusIcon.visibility = if (isOut) View.VISIBLE else View.GONE
            b.statusIcon.setImageResource(statusIcon(m.status))
            b.statusIcon.contentDescription = b.root.context.getString(statusLabel(m.status))

            b.retryButton.visibility =
                if (m.status == DeliveryState.FAILED) View.VISIBLE else View.GONE
            b.retryButton.setOnClickListener { onRetry(m) }

            if (m.replyToId != null) {
                b.replyIndicator.visibility = View.VISIBLE
            } else {
                b.replyIndicator.visibility = View.GONE
            }

            b.bubble.setOnLongClickListener {
                showMenu(it, m)
                true
            }
        }

        private fun showMenu(anchor: View, m: Message) {
            val ctx = anchor.context
            val popup = android.widget.PopupMenu(ctx, anchor)
            popup.menu.add(0, 1, 0, R.string.chat_action_copy)
            popup.menu.add(0, 2, 1, R.string.chat_action_reply)
            popup.menu.add(0, 3, 2, R.string.chat_action_delete)
            popup.setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> onCopy(m)
                    2 -> onReply(m)
                    3 -> onDelete(m)
                }
                true
            }
            popup.show()
        }

        private fun statusIcon(state: DeliveryState): Int = when (state) {
            DeliveryState.READ -> R.drawable.ic_status_read
            DeliveryState.DELIVERED_TO_DEVICE -> R.drawable.ic_status_delivered
            DeliveryState.TRANSMITTED -> R.drawable.ic_status_sent
            DeliveryState.QUEUED, DeliveryState.PENDING -> R.drawable.ic_status_pending
            DeliveryState.FAILED, DeliveryState.EXPIRED -> R.drawable.ic_status_failed
        }

        private fun statusLabel(state: DeliveryState): Int = when (state) {
            DeliveryState.READ -> R.string.status_read
            DeliveryState.DELIVERED_TO_DEVICE -> R.string.status_delivered
            DeliveryState.TRANSMITTED -> R.string.status_sent
            DeliveryState.QUEUED -> R.string.status_queued
            DeliveryState.PENDING -> R.string.status_pending
            DeliveryState.FAILED -> R.string.status_failed
            DeliveryState.EXPIRED -> R.string.status_expired
        }
    }
}
