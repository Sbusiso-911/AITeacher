// SubscriptionViewModel.kt
package com.playstudio.aiteacher

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SubscriptionViewModel : ViewModel() {
    private val _isAdFree = MutableLiveData<Boolean>()
    val isAdFree: LiveData<Boolean> = _isAdFree

    private val _expirationTime = MutableLiveData<Long>()
    val expirationTime: LiveData<Long> = _expirationTime

    fun updateSubscriptionStatus(adFree: Boolean, expirationTime: Long) {
        _isAdFree.value = adFree
        _expirationTime.value = expirationTime
    }
}