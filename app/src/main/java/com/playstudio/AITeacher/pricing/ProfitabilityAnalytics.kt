package com.playstudio.aiteacher.pricing

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Business profitability analytics and monitoring system
 * Tracks revenue vs costs and provides optimization recommendations
 */
class ProfitabilityAnalyticsService(private val context: Context) {
    
    private val costStorage = CostStorage(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    companion object {
        private const val TAG = "ProfitabilityAnalytics"
        
        // Profitability thresholds
        private const val HEALTHY_PROFIT_MARGIN = 0.30 // 30%
        private const val WARNING_PROFIT_MARGIN = 0.10 // 10%
        private const val CRITICAL_PROFIT_MARGIN = 0.05 // 5%
    }
    
    /**
     * Generate comprehensive daily profitability report
     */
    suspend fun generateDailyReport(): ProfitabilityReport = withContext(Dispatchers.IO) {
        try {
            val today = getCurrentDate()
            val allUsers = costStorage.getAllUsersWithCostData()
            
            // Collect data for each subscription tier
            val tierProfitability = mutableMapOf<SubscriptionTier, TierProfitability>()
            var totalRevenue = 0.0
            var totalCosts = 0.0
            val topCostUsers = mutableListOf<UserCostAnalysis>()
            
            SubscriptionTier.values().forEach { tier ->
                val tierData = calculateTierProfitability(tier, allUsers, today)
                tierProfitability[tier] = tierData
                totalRevenue += tierData.totalRevenue
                totalCosts += tierData.totalCosts
            }
            
            // Get top cost users across all tiers
            for (userId in allUsers.take(100)) { // Limit to prevent performance issues
                try {
                    val userAnalysis = calculateUserCostAnalysis(userId, today)
                    topCostUsers.add(userAnalysis)
                } catch (e: Exception) {
                    Log.w(TAG, "Error analyzing user $userId", e)
                }
            }
            
            topCostUsers.sortByDescending { it.dailyCost }
            
            val netProfit = totalRevenue - totalCosts
            val profitMargin = if (totalRevenue > 0) netProfit / totalRevenue else 0.0
            
            Log.d(TAG, "Daily report: Revenue=$totalRevenue, Costs=$totalCosts, " +
                    "Profit=$netProfit, Margin=${profitMargin * 100}%")
            
            ProfitabilityReport(
                date = today,
                totalRevenue = totalRevenue,
                totalAPICosts = totalCosts,
                netProfit = netProfit,
                profitMargin = profitMargin,
                userProfitability = tierProfitability,
                topCostUsers = topCostUsers.take(10),
                costOptimizationRecommendations = generateOptimizationRecommendations(tierProfitability),
                alertLevel = determineAlertLevel(profitMargin, tierProfitability)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating daily report", e)
            ProfitabilityReport.getEmpty()
        }
    }
    
    /**
     * Calculate profitability for a specific subscription tier
     */
    private suspend fun calculateTierProfitability(
        tier: SubscriptionTier,
        allUsers: List<String>,
        date: String
    ): TierProfitability {
        
        val plan = SubscriptionPlans.getPlanForTier(tier)
        val tierUsers = allUsers.filter { userId ->
            costStorage.getUserSubscriptionTier(userId) == tier
        }
        
        var totalCosts = 0.0
        val activeUsers = tierUsers.filter { userId ->
            val usage = costStorage.getDailyUsage(userId, date)
            val hasActivity = usage.totalCost > 0 || usage.messageCount > 0
            if (hasActivity) {
                totalCosts += usage.totalCost
            }
            hasActivity
        }
        
        val totalRevenue = activeUsers.size * plan.dailyRevenue
        val netProfit = totalRevenue - totalCosts
        val averageCostPerUser = if (activeUsers.isNotEmpty()) totalCosts / activeUsers.size else 0.0
        val averageRevenuePerUser = plan.dailyRevenue
        
        return TierProfitability(
            tier = tier,
            userCount = activeUsers.size,
            totalUsers = tierUsers.size,
            totalRevenue = totalRevenue,
            totalCosts = totalCosts,
            netProfit = netProfit,
            averageCostPerUser = averageCostPerUser,
            averageRevenuePerUser = averageRevenuePerUser,
            profitMargin = if (totalRevenue > 0) netProfit / totalRevenue else 0.0,
            isHealthy = netProfit > 0 && (netProfit / totalRevenue) >= WARNING_PROFIT_MARGIN
        )
    }
    
    /**
     * Analyze individual user cost patterns
     */
    private suspend fun calculateUserCostAnalysis(userId: String, date: String): UserCostAnalysis {
        val usage = costStorage.getDailyUsage(userId, date)
        val userTier = costStorage.getUserSubscriptionTier(userId) ?: SubscriptionTier.FREE
        val plan = SubscriptionPlans.getPlanForTier(userTier)
        
        val revenue = plan.dailyRevenue
        val profit = revenue - usage.totalCost
        val utilizationRate = usage.totalCost / plan.maxDailyCostLimit
        
        // Get 7-day average for trend analysis
        val weeklyAnalytics = costStorage.getCostAnalytics(userId, getDateDaysAgo(7), date)
        val costTrend = if (weeklyAnalytics.averageDailyCost > usage.totalCost) "Decreasing" 
                      else if (weeklyAnalytics.averageDailyCost < usage.totalCost) "Increasing"
                      else "Stable"
        
        return UserCostAnalysis(
            userId = userId,
            subscriptionTier = userTier,
            dailyCost = usage.totalCost,
            dailyRevenue = revenue,
            dailyProfit = profit,
            messageCount = usage.messageCount,
            utilizationRate = utilizationRate,
            costTrend = costTrend,
            isProfiTable = profit > 0,
            riskLevel = when {
                profit < -plan.dailyRevenue -> RiskLevel.CRITICAL
                profit < 0 -> RiskLevel.HIGH
                utilizationRate > 0.8 -> RiskLevel.MEDIUM
                else -> RiskLevel.LOW
            }
        )
    }
    
    /**
     * Generate optimization recommendations based on profitability data
     */
    private fun generateOptimizationRecommendations(
        profitability: Map<SubscriptionTier, TierProfitability>
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        profitability.forEach { (tier, data) ->
            when {
                data.netProfit < 0 -> {
                    val lossPercentage = ((-data.netProfit / data.totalRevenue) * 100).toInt()
                    recommendations.add("❌ CRITICAL: $tier tier losing $${String.format("%.2f", -data.netProfit)}/day ($lossPercentage% loss). Consider increasing prices or reducing limits immediately.")
                }
                
                data.profitMargin < CRITICAL_PROFIT_MARGIN -> {
                    recommendations.add("🚨 URGENT: $tier tier has critically low profit margin (${(data.profitMargin * 100).toInt()}%). Risk of losses.")
                }
                
                data.profitMargin < WARNING_PROFIT_MARGIN -> {
                    recommendations.add("WARNING: $tier tier has low profit margin (${(data.profitMargin * 100).toInt()}%). Consider optimization.")
                }
                
                data.profitMargin > 0.6 -> {
                    recommendations.add("OPPORTUNITY: $tier tier has high profit margin (${(data.profitMargin * 100).toInt()}%). Consider adding more features or reducing prices to be competitive.")
                }
                
                data.averageCostPerUser > data.averageRevenuePerUser * 0.8 -> {
                    recommendations.add("OPTIMIZE: $tier tier users consuming high resources. Consider implementing usage-based pricing or smart model selection.")
                }
                
                data.userCount == 0 && tier != SubscriptionTier.FREE -> {
                    recommendations.add("MARKETING: No active users in $tier tier. Consider promotional campaigns or feature highlights.")
                }
            }
        }
        
        // Global recommendations
        val totalProfit = profitability.values.sumOf { it.netProfit }
        val totalRevenue = profitability.values.sumOf { it.totalRevenue }
        val overallMargin = if (totalRevenue > 0) totalProfit / totalRevenue else 0.0
        
        if (overallMargin < WARNING_PROFIT_MARGIN) {
            recommendations.add("BUSINESS CRITICAL: Overall profit margin is ${(overallMargin * 100).toInt()}%. Immediate action required to ensure sustainability.")
        }
        
        return recommendations
    }
    
    /**
     * Determine alert level based on overall business health
     */
    private fun determineAlertLevel(
        overallMargin: Double,
        tierProfitability: Map<SubscriptionTier, TierProfitability>
    ): AlertLevel {
        
        val losingTiers = tierProfitability.values.count { it.netProfit < 0 }
        val totalTiers = tierProfitability.size
        
        return when {
            overallMargin < 0 -> AlertLevel.CRITICAL
            losingTiers > totalTiers / 2 -> AlertLevel.CRITICAL
            overallMargin < CRITICAL_PROFIT_MARGIN -> AlertLevel.HIGH
            overallMargin < WARNING_PROFIT_MARGIN -> AlertLevel.MEDIUM
            losingTiers > 0 -> AlertLevel.LOW
            else -> AlertLevel.HEALTHY
        }
    }
    
    /**
     * Generate monthly profitability report
     */
    suspend fun generateMonthlyReport(): MonthlyProfitabilityReport = withContext(Dispatchers.IO) {
        try {
            val reports = mutableListOf<ProfitabilityReport>()
            val calendar = Calendar.getInstance()
            
            // Generate reports for last 30 days
            repeat(30) {
                val date = dateFormat.format(calendar.time)
                // Note: This is simplified - in production, you'd store daily reports
                // For now, we'll just use current data
                calendar.add(Calendar.DAY_OF_MONTH, -1)
            }
            
            // Use current daily report as sample
            val currentReport = generateDailyReport()
            
            MonthlyProfitabilityReport(
                month = getCurrentMonth(),
                dailyReports = listOf(currentReport), // Simplified
                totalRevenue = currentReport.totalRevenue * 30,
                totalCosts = currentReport.totalAPICosts * 30,
                netProfit = currentReport.netProfit * 30,
                averageDailyProfit = currentReport.netProfit,
                profitTrend = "Stable", // Simplified
                recommendations = generateMonthlyRecommendations(currentReport)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating monthly report", e)
            MonthlyProfitabilityReport.getEmpty()
        }
    }
    
    /**
     * Generate monthly optimization recommendations
     */
    private fun generateMonthlyRecommendations(dailyReport: ProfitabilityReport): List<String> {
        val recommendations = mutableListOf<String>()
        
        // Add monthly-specific insights
        val projectedMonthlyProfit = dailyReport.netProfit * 30
        val projectedMonthlyRevenue = dailyReport.totalRevenue * 30
        
        if (projectedMonthlyProfit < 1000) {
            recommendations.add("GROWTH: Projected monthly profit is $${String.format("%.0f", projectedMonthlyProfit)}. Consider growth strategies to reach $1000+ monthly profit.")
        }
        
        if (projectedMonthlyRevenue < 5000) {
            recommendations.add("📊 SCALE: Monthly revenue projection is $${String.format("%.0f", projectedMonthlyRevenue)}. Focus on user acquisition and tier upgrades.")
        }
        
        recommendations.addAll(dailyReport.costOptimizationRecommendations)
        
        return recommendations.distinct()
    }
    
    /**
     * Utility functions
     */
    private fun getCurrentDate(): String {
        return dateFormat.format(Date())
    }
    
    private fun getCurrentMonth(): String {
        return SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
    }
    
    private fun getDateDaysAgo(days: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -days)
        return dateFormat.format(calendar.time)
    }
}

/**
 * Main profitability report
 */
data class ProfitabilityReport(
    val date: String,
    val totalRevenue: Double,
    val totalAPICosts: Double,
    val netProfit: Double,
    val profitMargin: Double,
    val userProfitability: Map<SubscriptionTier, TierProfitability>,
    val topCostUsers: List<UserCostAnalysis>,
    val costOptimizationRecommendations: List<String>,
    val alertLevel: AlertLevel
) {
    companion object {
        fun getEmpty() = ProfitabilityReport(
            date = "",
            totalRevenue = 0.0,
            totalAPICosts = 0.0,
            netProfit = 0.0,
            profitMargin = 0.0,
            userProfitability = emptyMap(),
            topCostUsers = emptyList(),
            costOptimizationRecommendations = listOf("No data available"),
            alertLevel = AlertLevel.HEALTHY
        )
    }
}

/**
 * Profitability data for a subscription tier
 */
data class TierProfitability(
    val tier: SubscriptionTier,
    val userCount: Int,
    val totalUsers: Int,
    val totalRevenue: Double,
    val totalCosts: Double,
    val netProfit: Double,
    val averageCostPerUser: Double,
    val averageRevenuePerUser: Double,
    val profitMargin: Double,
    val isHealthy: Boolean
)

/**
 * Individual user cost analysis
 */
data class UserCostAnalysis(
    val userId: String,
    val subscriptionTier: SubscriptionTier,
    val dailyCost: Double,
    val dailyRevenue: Double,
    val dailyProfit: Double,
    val messageCount: Int,
    val utilizationRate: Double,
    val costTrend: String,
    val isProfiTable: Boolean,
    val riskLevel: RiskLevel
)

/**
 * Monthly profitability report
 */
data class MonthlyProfitabilityReport(
    val month: String,
    val dailyReports: List<ProfitabilityReport>,
    val totalRevenue: Double,
    val totalCosts: Double,
    val netProfit: Double,
    val averageDailyProfit: Double,
    val profitTrend: String,
    val recommendations: List<String>
) {
    companion object {
        fun getEmpty() = MonthlyProfitabilityReport(
            month = "",
            dailyReports = emptyList(),
            totalRevenue = 0.0,
            totalCosts = 0.0,
            netProfit = 0.0,
            averageDailyProfit = 0.0,
            profitTrend = "No data",
            recommendations = emptyList()
        )
    }
}

/**
 * Business alert levels
 */
enum class AlertLevel(val displayName: String, val color: String) {
    HEALTHY("Healthy", "#4CAF50"),
    LOW("Low Risk", "#FFC107"),
    MEDIUM("Medium Risk", "#FF9800"),
    HIGH("High Risk", "#FF5722"),
    CRITICAL("Critical", "#F44336")
}

/**
 * User risk levels for cost management
 */
enum class RiskLevel {
    LOW,        // Profitable user
    MEDIUM,     // High usage but still profitable
    HIGH,       // Unprofitable but manageable
    CRITICAL    // Severe cost overrun
}