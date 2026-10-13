package com.wifichat.mesh.ui.nearby

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.wifichat.mesh.R
import com.wifichat.mesh.databinding.ItemPeerBinding
import com.wifichat.mesh.model.Peer
import com.wifichat.mesh.network.DiscoveredDevice
import com.wifichat.mesh.util.Ids

/** One row on the Nearby screen: either a confirmed mesh member or a raw device. */
sealed class NearbyRow {
    data class Member(val peer: Peer) : NearbyRow()
    data class Device(val device: DiscoveredDevice) : NearbyRow()
}

class NearbyAdapter(
    private val onConnect: (String) -> Unit,
    private val onMessage: (String, String) -> Unit
) : RecyclerView.Adapter<NearbyAdapter.VH>() {

    private val items = mutableListOf<NearbyRow>()

    fun submit(list: List<NearbyRow>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(o: Int, n: Int) = key(items[o]) == key(list[n])
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == list[n]
        })
        items.clear(); items.addAll(list)
        diff.dispatchUpdatesTo(this)
    }

    private fun key(row: NearbyRow): String = when (row) {
        is NearbyRow.Member -> "m:${row.peer.peerId}"
        is NearbyRow.Device -> "d:${row.device.address}"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemPeerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class VH(private val b: ItemPeerBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: NearbyRow) {
            val ctx = b.root.context
            when (row) {
                is NearbyRow.Member -> {
                    val p = row.peer
                    b.name.text = p.displayName
                    b.deviceTag.text = ctx.getString(R.string.peer_tag, Ids.shortTag(p.peerId))
                    b.connectionState.setText(R.string.state_member)
                    b.avatar.text = p.displayName.take(1).uppercase()
                    b.actionButton.setText(R.string.message)
                    b.actionButton.isEnabled = true
                    b.actionButton.setOnClickListener { onMessage(p.peerId, p.displayName) }
                }
                is NearbyRow.Device -> {
                    val d = row.device
                    b.name.text = d.name ?: ctx.getString(R.string.unknown_device)
                    b.deviceTag.text = d.address
                    b.connectionState.setText(
                        if (d.bonded) R.string.state_paired else R.string.state_found
                    )
                    b.avatar.text = (d.name ?: "?").take(1).uppercase()
                    b.actionButton.setText(R.string.connect)
                    b.actionButton.isEnabled = true
                    b.actionButton.setOnClickListener { onConnect(d.address) }
                }
            }
        }
    }
}
