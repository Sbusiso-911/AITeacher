package com.playstudio.aiteacher.profile

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.ColumnInfo
import androidx.room.TypeConverters
// Converters are defined at database level
import java.util.Date

@Entity(
    tableName = "subscriptions",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@TypeConverters(DatabaseConverters::class)
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "subscription_id")
    val subscriptionId: Long = 0,
    
    @ColumnInfo(name = "user_id")
    val userId: Long,
    
    @ColumnInfo(name = "plan_type")
    val planType: String, // free, pro, premium
    
    @ColumnInfo(name = "status")
    val status: String, // active, cancelled, expired, trial
    
    @ColumnInfo(name = "start_date")
    val startDate: Date,
    
    @ColumnInfo(name = "end_date")
    val endDate: Date,
    
    @ColumnInfo(name = "billing_cycle")
    val billingCycle: String, // monthly, yearly
    
    @ColumnInfo(name = "payment_method_id")
    val paymentMethodId: String? = null,
    
    @ColumnInfo(name = "price_paid")
    val pricePaid: Double = 0.0,
    
    @ColumnInfo(name = "currency")
    val currency: String = "USD",
    
    @ColumnInfo(name = "auto_renew")
    val autoRenew: Boolean = true,
    
    @ColumnInfo(name = "trial_end_date")
    val trialEndDate: Date? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Date = Date(),
    
    @ColumnInfo(name = "features_included")
    val featuresIncluded: List<String> = emptyList()
)