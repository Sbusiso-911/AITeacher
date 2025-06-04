package com.playstudio.aiteacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.playstudio.aiteacher.history.HistoryRepository
import com.playstudio.aiteacher.history.ConversationEntity
import kotlinx.coroutines.flow.Flow

class HistoryViewModel(private val repository: HistoryRepository) : ViewModel() {
    val conversations: Flow<List<ConversationEntity>> = repository.getConversations()

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
