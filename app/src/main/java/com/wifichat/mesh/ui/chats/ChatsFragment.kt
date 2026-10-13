package com.wifichat.mesh.ui.chats

import android.os.Bundle
import androidx.lifecycle.asLiveData
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.wifichat.mesh.R
import com.wifichat.mesh.databinding.FragmentChatsBinding
import com.wifichat.mesh.ui.SimpleViewModelFactory
import com.wifichat.mesh.ui.container

class ChatsFragment : Fragment() {

    private var _binding: FragmentChatsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatsViewModel by viewModels {
        SimpleViewModelFactory { ChatsViewModel(container) }
    }

    private lateinit var adapter: ConversationAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ConversationAdapter(
            onClick = { openChat(it.conversationId, it.peerId, it.title) },
            onTogglePin = { viewModel.togglePinned(it) },
            onDelete = { viewModel.deleteConversation(it.conversationId) }
        )
        binding.conversationList.layoutManager = LinearLayoutManager(requireContext())
        binding.conversationList.adapter = adapter

        binding.searchField.addTextChangedListener(
            com.wifichat.mesh.ui.util.SimpleTextWatcher { viewModel.setQuery(it) }
        )

        binding.newChatFab.setOnClickListener {
            findNavController().navigate(R.id.action_chats_to_nearby)
        }

        viewModel.conversations.asLiveData().observe(viewLifecycleOwner) { list ->
            adapter.submit(list)
            binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.status.asLiveData().observe(viewLifecycleOwner) { s ->
            binding.networkStatus.text = getString(
                R.string.network_status_format,
                if (s.bluetoothEnabled) getString(R.string.net_online) else getString(R.string.net_offline),
                s.connectedPeerCount
            )
        }
    }

    private fun openChat(conversationId: String, peerId: String?, title: String) {
        if (peerId == null) return
        startActivity(com.wifichat.mesh.ui.chat.ChatActivity.intent(requireContext(), conversationId, peerId, title))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
