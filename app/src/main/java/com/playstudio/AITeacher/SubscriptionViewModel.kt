// SubscriptionViewModel.kt
package com.playstudio.aiteacher

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.playstudio.aiteacher.pricing.SubscriptionTier
import com.playstudio.aiteacher.pricing.AIModel

class SubscriptionViewModel : ViewModel() {
    private val _isAdFree = MutableLiveData<Boolean>()
    val isAdFree: LiveData<Boolean> = _isAdFree

    private val _expirationTime = MutableLiveData<Long>()
    val expirationTime: LiveData<Long> = _expirationTime
    
    private val _subscriptionTier = MutableLiveData<SubscriptionTier>()
    val subscriptionTier: LiveData<SubscriptionTier> = _subscriptionTier
    
    private val _isSubscriptionActive = MutableLiveData<Boolean>()
    val isSubscriptionActive: LiveData<Boolean> = _isSubscriptionActive
    
    private val _availableModels = MutableLiveData<List<AIModel>>()
    val availableModels: LiveData<List<AIModel>> = _availableModels

    fun updateSubscriptionStatus(adFree: Boolean, expirationTime: Long) {
        _isAdFree.value = adFree
        _expirationTime.value = expirationTime
        checkSubscriptionStatus()
    }
    
    fun activateSubscription(tier: SubscriptionTier, durationInDays: Int) {
        val currentTime = System.currentTimeMillis()
        val expirationTime = currentTime + (durationInDays * 24 * 60 * 60 * 1000L)
        
        _subscriptionTier.value = tier
        _expirationTime.value = expirationTime
        _isSubscriptionActive.value = true
        _isAdFree.value = true
        
        // Update available models - all models are available to all tiers
        _availableModels.value = AIModel.getAllModels()
        
        // Save to preferences
        saveSubscriptionToPreferences(tier, expirationTime)
    }
    
    fun checkSubscriptionStatus() {
        val currentTime = System.currentTimeMillis()
        val expiration = _expirationTime.value ?: 0
        
        val isActive = expiration > currentTime
        _isSubscriptionActive.value = isActive
        
        if (!isActive) {
            // Subscription expired - downgrade to free
            _subscriptionTier.value = SubscriptionTier.FREE
            _isAdFree.value = false
            _availableModels.value = AIModel.getAllModels()
        }
    }
    
    fun isSubscriptionExpired(): Boolean {
        val currentTime = System.currentTimeMillis()
        val expiration = _expirationTime.value ?: 0
        return expiration <= currentTime
    }
    
    fun getRemainingDays(): Int {
        val currentTime = System.currentTimeMillis()
        val expiration = _expirationTime.value ?: 0
        if (expiration <= currentTime) return 0
        
        val remainingMillis = expiration - currentTime
        return (remainingMillis / (24 * 60 * 60 * 1000L)).toInt()
    }
    
    fun getCurrentTier(): SubscriptionTier {
        return if (isSubscriptionExpired()) {
            SubscriptionTier.FREE
        } else {
            _subscriptionTier.value ?: SubscriptionTier.FREE
        }
    }
    
    fun canAccessModel(model: AIModel): Boolean {
        // In the new usage-based system, all models are accessible to all tiers
        // but they have different usage limits
        return true
    }
    
    private fun saveSubscriptionToPreferences(tier: SubscriptionTier, expirationTime: Long) {
        // This would typically save to SharedPreferences
        // Implementation depends on your app's preference management
    }
}