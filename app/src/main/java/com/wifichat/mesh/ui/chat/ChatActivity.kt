package com.wifichat.mesh.ui.chat

import android.os.Bundle
import androidx.lifecycle.asLiveData
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.wifichat.mesh.R
import com.wifichat.mesh.databinding.ActivityChatBinding
import com.wifichat.mesh.model.Message
import com.wifichat.mesh.ui.SimpleViewModelFactory
import com.wifichat.mesh.ui.container

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: MessageAdapter

    private val conversationId: String by lazy { intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty() }
    private val peerId: String by lazy { intent.getStringExtra(EXTRA_PEER_ID).orEmpty() }
    private val peerName: String by lazy { intent.getStringExtra(EXTRA_PEER_NAME).orEmpty() }

    private val viewModel: ChatViewModel by viewModels {
        SimpleViewModelFactory { ChatViewModel(container, conversationId, peerId, peerName) }
    }

    private var replyToId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.peerName.text = peerName
        binding.backButton.setOnClickListener { finish() }

        adapter = MessageAdapter(
            onRetry = { viewModel.retry(it) },
            onCopy = { copyToClipboard(it) },
            onReply = { startReply(it) },
            onDelete = { viewModel.delete(it) }
        )
        binding.messageList.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.messageList.adapter = adapter

        viewModel.messages.asLiveData().observe(this) { messages ->
            adapter.submit(messages)
            if (messages.isNotEmpty()) {
                binding.emptyState.visibility = android.view.View.GONE
                binding.messageList.scrollToPosition(messages.size - 1)
                val latestIncoming = messages.lastOrNull { !it.outgoing }
                if (latestIncoming != null) viewModel.markRead(latestIncoming.messageId)
            } else {
                binding.emptyState.visibility = android.view.View.VISIBLE
            }
        }

        viewModel.reachable.asLiveData().observe(this) { reachable ->
            binding.routeStatus.text = getString(
                if (reachable) R.string.chat_route_ready else R.string.chat_waiting_for_peer
            )
        }

        binding.sendButton.setOnClickListener { send() }
        binding.cancelReply.setOnClickListener { clearReply() }
    }

    private fun send() {
        val text = binding.messageInput.text?.toString().orEmpty()
        if (text.isBlank()) return
        viewModel.send(text, replyToId) { ok ->
            if (!ok) {
                Toast.makeText(this, R.string.chat_waiting_for_peer, Toast.LENGTH_SHORT).show()
            }
        }
        binding.messageInput.text?.clear()
        clearReply()
    }

    private fun startReply(message: Message) {
        replyToId = message.messageId
        binding.replyBar.visibility = android.view.View.VISIBLE
        binding.replyPreview.text = message.body.take(80)
    }

    private fun clearReply() {
        replyToId = null
        binding.replyBar.visibility = android.view.View.GONE
    }

    private fun copyToClipboard(message: Message) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("message", message.body))
        Toast.makeText(this, R.string.chat_copied, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_PEER_ID = "peer_id"
        const val EXTRA_PEER_NAME = "peer_name"

        fun intent(context: android.content.Context, conversationId: String, peerId: String, peerName: String) =
            android.content.Intent(context, ChatActivity::class.java).apply {
                putExtra(EXTRA_CONVERSATION_ID, conversationId)
                putExtra(EXTRA_PEER_ID, peerId)
                putExtra(EXTRA_PEER_NAME, peerName)
            }
    }
}
