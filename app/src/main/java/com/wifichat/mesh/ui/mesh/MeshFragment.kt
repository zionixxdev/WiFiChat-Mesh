package com.wifichat.mesh.ui.mesh

import android.os.Bundle
import androidx.lifecycle.asLiveData
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.wifichat.mesh.R
import com.wifichat.mesh.databinding.FragmentMeshBinding
import com.wifichat.mesh.ui.SimpleViewModelFactory
import com.wifichat.mesh.ui.container

class MeshFragment : Fragment() {

    private var _binding: FragmentMeshBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MeshViewModel by viewModels {
        SimpleViewModelFactory { MeshViewModel(container) }
    }

    private lateinit var eventAdapter: EventLogAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMeshBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        eventAdapter = EventLogAdapter()
        binding.eventList.layoutManager = LinearLayoutManager(requireContext())
        binding.eventList.adapter = eventAdapter

        binding.clearLogButton.setOnClickListener { viewModel.clearLog() }

        viewModel.status.asLiveData().observe(viewLifecycleOwner) { s ->
            binding.connectedCount.text = s.connectedPeerCount.toString()
            binding.knownCount.text = s.knownPeerCount.toString()
            binding.relayedCount.text = s.messagesRelayed.toString()
            binding.pendingCount.text = s.pendingMessages.toString()
            binding.failedCount.text = s.failedDeliveries.toString()
            binding.discoveryState.text = getString(
                if (s.scanning) R.string.discovery_active else R.string.discovery_idle
            )
        }

        viewModel.links.asLiveData().observe(viewLifecycleOwner) { links ->
            binding.meshGraph.setData(
                getString(R.string.mesh_self),
                links,
                viewModel.knownPeers.value
            )
            binding.linkCount.text = links.size.toString()
        }

        viewModel.knownPeers.asLiveData().observe(viewLifecycleOwner) { peers ->
            binding.meshGraph.setData(
                getString(R.string.mesh_self),
                viewModel.links.value,
                peers
            )
        }

        viewModel.events.asLiveData().observe(viewLifecycleOwner) { events ->
            eventAdapter.submit(events)
            binding.eventEmpty.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
