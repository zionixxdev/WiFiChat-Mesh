package com.wifichat.mesh.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifichat.mesh.AppContainer
import com.wifichat.mesh.model.Message
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the 1:1 chat screen. Sending returns null when the recipient has not
 * announced yet (no key), so the UI can say "waiting for peer" instead of
 * showing a fake delivered tick.
 */
class ChatViewModel(
    private val container: AppContainer,
    private val conversationId: String,
    private val peerId: String,
    private val peerName: String
) : ViewModel() {

    val messages: StateFlow<List<Message>> =
        container.messages.observeConversation(conversationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True once we hold the peer's key, i.e. we can actually encrypt to them. */
    val reachable: StateFlow<Boolean> =
        container.router.knownPeers
            .map { it.contains(peerId) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), container.router.canReach(peerId))

    fun send(text: String, replyToId: String? = null, onResult: (Boolean) -> Unit = {}) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val id = container.router.sendMessage(peerId, peerName, text.trim(), replyToId)
            onResult(id != null)
        }
    }

    fun markRead(latestIncomingId: String?) {
        viewModelScope.launch {
            container.conversations.clearUnread(conversationId)
            if (latestIncomingId != null) {
                container.router.sendReadReceipt(peerId, latestIncomingId)
            }
        }
    }

    fun retry(message: Message) {
        viewModelScope.launch { container.router.retry(message.messageId) }
    }

    fun delete(message: Message) {
        viewModelScope.launch { container.messages.delete(message.messageId) }
    }
}
