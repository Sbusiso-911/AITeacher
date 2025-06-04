package com.playstudio.aiteacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.playstudio.aiteacher.history.HistoryRepository
import com.playstudio.aiteacher.history.ConversationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: HistoryRepository) : ViewModel() {
    val conversations: Flow<List<ConversationEntity>> = repository.getConversations()

    fun addMessage(id: String, isUser: Boolean, content: String) {
        viewModelScope.launch {
            repository.addMessage(id, isUser, content)
        }
    }

    class Factory(private val repository: HistoryRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return HistoryViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
