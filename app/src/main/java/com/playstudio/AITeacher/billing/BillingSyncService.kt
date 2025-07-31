package com.playstudio.aiteacher.billing

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import com.playstudio.aiteacher.profile.FirebaseAuthenticationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Background service for periodic Google Play Billing to Firestore synchronization
 * Ensures subscription data is always up-to-date even when the app is not actively used
 */
class BillingSyncService : Service() {
    
    companion object {
        private const val TAG = "BillingSyncService"
        private const val SYNC_WORK_NAME = "billing_sync_work"
        
        /**
         * Schedule periodic billing sync (every 12 hours)
         */
        fun scheduleBillingSync(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresCharging(false)
                    .setRequiresDeviceIdle(false)
                    .build()
                
                val workRequest = PeriodicWorkRequestBuilder<BillingSyncWorker>(12, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()
                
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    SYNC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
                
                Log.d(TAG, "Billing sync work scheduled successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling billing sync work", e)
            }
        }
        
        /**
         * Cancel all billing sync work
         */
        fun cancelBillingSync(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
                Log.d(TAG, "Billing sync work cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling billing sync work", e)
            }
        }
        
        /**
         * Trigger immediate one-time sync
         */
        fun triggerImmediateSync(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                
                val workRequest = OneTimeWorkRequestBuilder<BillingSyncWorker>()
                    .setConstraints(constraints)
                    .build()
                
                WorkManager.getInstance(context).enqueue(workRequest)
                Log.d(TAG, "Immediate billing sync triggered")
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering immediate billing sync", e)
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "BillingSyncService started")
        
        // Perform immediate sync
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val billingSync = GooglePlayBillingSync(this@BillingSyncService)
                val success = billingSync.syncSubscriptionToFirestore()
                Log.d(TAG, "Service billing sync completed: success=$success")
            } catch (e: Exception) {
                Log.e(TAG, "Error in service billing sync", e)
            } finally {
                stopSelf()
            }
        }
        
        return START_NOT_STICKY
    }
}

/**
 * Worker class for background billing synchronization
 */
class BillingSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        private const val TAG = "BillingSyncWorker"
    }
    
    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting background billing sync")
            
            // Check if user is authenticated before attempting sync
            val firebaseAuthService = FirebaseAuthenticationService(applicationContext)
            if (!firebaseAuthService.isSignedIn()) {
                Log.d(TAG, "User not authenticated, skipping billing sync")
                return Result.success()
            }
            
            // Perform billing sync
            val billingSync = GooglePlayBillingSync(applicationContext)
            val success = billingSync.checkAndSyncIfNeeded()
            
            if (success) {
                Log.d(TAG, "Background billing sync completed successfully")
                Result.success()
            } else {
                Log.w(TAG, "Background billing sync failed")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in background billing sync", e)
            Result.failure()
        }
    }
}

/**
 * Utility class for managing billing sync across the app
 */
object BillingSyncManager {
    
    private const val TAG = "BillingSyncManager"
    
    /**
     * Initialize billing sync for the entire app
     * Call this from Application.onCreate() or MainActivity.onCreate()
     */
    fun initialize(context: Context) {
        try {
            // Schedule periodic sync
            BillingSyncService.scheduleBillingSync(context)
            
            // Store sync preferences
            val prefs = context.getSharedPreferences("billing_sync_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("sync_initialized_at", System.currentTimeMillis())
                .putBoolean("sync_enabled", true)
                .apply()
            
            Log.d(TAG, "Billing sync manager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing billing sync manager", e)
        }
    }
    
    /**
     * Sync billing data immediately and ensure UI is updated
     */
    suspend fun syncNow(context: Context): Boolean {
        return try {
            Log.d(TAG, "Performing immediate billing sync")
            
            val billingSync = GooglePlayBillingSync(context)
            val success = billingSync.syncSubscriptionToFirestore()
            
            if (success) {
                Log.d(TAG, "Immediate billing sync completed successfully")
                
                // Trigger immediate background sync as well
                BillingSyncService.triggerImmediateSync(context)
            } else {
                Log.w(TAG, "Immediate billing sync failed")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error in immediate billing sync", e)
            false
        }
    }
    
    /**
     * Check if sync is enabled and working
     */
    fun isSyncEnabled(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences("billing_sync_prefs", Context.MODE_PRIVATE)
            prefs.getBoolean("sync_enabled", false)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking sync status", e)
            false
        }
    }
    
    /**
     * Get last sync timestamp
     */
    fun getLastSyncTime(context: Context): Long {
        return try {
            val prefs = context.getSharedPreferences("billing_sync_prefs", Context.MODE_PRIVATE)
            prefs.getLong("last_sync_time", 0L)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last sync time", e)
            0L
        }
    }
    
    /**
     * Update last sync timestamp
     */
    fun updateLastSyncTime(context: Context) {
        try {
            val prefs = context.getSharedPreferences("billing_sync_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("last_sync_time", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating last sync time", e)
        }
    }
    
    /**
     * Disable billing sync (for testing or troubleshooting)
     */
    fun disableSync(context: Context) {
        try {
            BillingSyncService.cancelBillingSync(context)
            
            val prefs = context.getSharedPreferences("billing_sync_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("sync_enabled", false)
                .apply()
            
            Log.d(TAG, "Billing sync disabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling billing sync", e)
        }
    }
    
    /**
     * Re-enable billing sync
     */
    fun enableSync(context: Context) {
        try {
            BillingSyncService.scheduleBillingSync(context)
            
            val prefs = context.getSharedPreferences("billing_sync_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("sync_enabled", true)
                .apply()
            
            Log.d(TAG, "Billing sync enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling billing sync", e)
        }
    }
}