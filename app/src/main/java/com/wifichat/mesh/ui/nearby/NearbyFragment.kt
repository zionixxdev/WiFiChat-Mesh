package com.wifichat.mesh.ui.nearby

import android.os.Bundle
import androidx.lifecycle.asLiveData
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.wifichat.mesh.R
import com.wifichat.mesh.databinding.FragmentNearbyBinding
import com.wifichat.mesh.ui.SimpleViewModelFactory
import com.wifichat.mesh.ui.container

class NearbyFragment : Fragment() {

    private var _binding: FragmentNearbyBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NearbyViewModel by viewModels {
        SimpleViewModelFactory { NearbyViewModel(container) }
    }

    private lateinit var adapter: NearbyAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* discovery state is re-read below */ }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNearbyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = NearbyAdapter(
            onConnect = { viewModel.connect(it) },
            onMessage = { peerId, name -> openChat(peerId, name) }
        )
        binding.nearbyList.layoutManager = LinearLayoutManager(requireContext())
        binding.nearbyList.adapter = adapter

        binding.scanToggle.setOnClickListener {
            if (viewModel.scanning.value) viewModel.stopScan()
            else {
                permissionLauncher.launch(viewModel.requiredPermissions().toTypedArray())
                viewModel.startScan()
            }
        }

        viewModel.scanning.asLiveData().observe(viewLifecycleOwner) { scanning ->
            binding.scanToggle.setText(if (scanning) R.string.stop_discovery else R.string.start_discovery)
            binding.pulseIndicator.visibility = if (scanning) View.VISIBLE else View.INVISIBLE
            binding.discoveryStatus.setText(if (scanning) R.string.discovery_active else R.string.discovery_idle)
        }

        combineLatest()

        // Kick off a scan when the screen opens, if allowed.
        viewModel.startScan()
    }

    private fun combineLatest() {
        var members: List<com.wifichat.mesh.model.Peer> = emptyList()
        var devices: List<com.wifichat.mesh.network.DiscoveredDevice> = emptyList()

        fun refresh() {
            val rows = buildList {
                members.forEach { add(NearbyRow.Member(it)) }
                devices
                    .filter { d -> members.none { it.peerId == d.address } }
                    .forEach { add(NearbyRow.Device(it)) }
            }
            adapter.submit(rows)
            binding.emptyState.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.peers.asLiveData().observe(viewLifecycleOwner) { members = it; refresh() }
        viewModel.devices.asLiveData().observe(viewLifecycleOwner) { devices = it; refresh() }
    }

    private fun openChat(peerId: String, name: String) {
        viewModel.openConversation(peerId, name) { conversationId ->
            startActivity(
                com.wifichat.mesh.ui.chat.ChatActivity.intent(
                    requireContext(), conversationId, peerId, name
                )
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
