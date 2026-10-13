package com.wifichat.mesh.ui.mesh

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.wifichat.mesh.databinding.ItemEventBinding
import com.wifichat.mesh.model.MeshEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventLogAdapter : RecyclerView.Adapter<EventLogAdapter.VH>() {

    private val items = mutableListOf<MeshEvent>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun submit(list: List<MeshEvent>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(o: Int, n: Int) = items[o].id == list[n].id
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == list[n]
        })
        items.clear(); items.addAll(list)
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class VH(private val b: ItemEventBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(e: MeshEvent) {
            b.eventType.text = e.type.name.replace('_', ' ')
            b.eventDetail.text = e.detail
            b.eventTime.text = fmt.format(Date(e.timestamp))
        }
    }
}
