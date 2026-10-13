package com.wifichat.mesh.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifichat.mesh.AppContainer
import com.wifichat.mesh.model.Conversation
import com.wifichat.mesh.model.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatsViewModel(private val container: AppContainer) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val status: StateFlow<NetworkStatus> = container.status.state

    val conversations: StateFlow<List<Conversation>> =
        combine(container.conversations.observeAll(), _query) { list, q ->
            if (q.isBlank()) list
            else list.filter {
                it.title.contains(q, ignoreCase = true) ||
                    (it.lastMessagePreview?.contains(q, ignoreCase = true) == true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { _query.value = q }

    fun clearUnread(conversationId: String) {
        viewModelScope.launch { container.conversations.clearUnread(conversationId) }
    }

    fun togglePinned(conversation: Conversation) {
        viewModelScope.launch {
            container.conversations.setPinned(conversation.conversationId, !conversation.pinned)
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch { container.conversations.deleteConversation(conversationId) }
    }
}
