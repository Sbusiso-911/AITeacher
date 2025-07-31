package com.playstudio.AITeacher.databinding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.airbnb.lottie.LottieAnimationView
//import com.playstudio.AITeacher.R
import com.playstudio.aiteacher.R

/**
 * Data binding class for dialog_cost_aware_subscription.xml
 * Auto-generated style class to match Android's ViewBinding pattern
 */
class DialogCostAwareSubscriptionBinding private constructor(
    val root: NestedScrollView,
    val dialogLayout: LinearLayout,
    val lottieAnimationView: LottieAnimationView,
    val dialogTitle: TextView,
    val currentStatusLayout: LinearLayout,
    val currentTierText: TextView,
    val currentUsageText: TextView,
    val freeTier: LinearLayout,
    val basicTier: LinearLayout,
    val basicPrice: TextView,
    val proTier: LinearLayout,
    val proPrice: TextView,
    val premiumTier: LinearLayout,
    val premiumPrice: TextView,
    val ultraTier: LinearLayout,
    val ultraPrice: TextView,
    val btnPurchase: Button,
    val btnClose: TextView
) {
    
    companion object {
        @JvmStatic
        fun inflate(inflater: LayoutInflater): DialogCostAwareSubscriptionBinding {
            return inflate(inflater, null, false)
        }
        
        @JvmStatic
        fun inflate(
            inflater: LayoutInflater,
            parent: ViewGroup?,
            attachToParent: Boolean
        ): DialogCostAwareSubscriptionBinding {
            val root = inflater.inflate(R.layout.dialog_cost_aware_subscription, parent, attachToParent) as NestedScrollView
            return bind(root)
        }
        
        @JvmStatic
        fun bind(rootView: View): DialogCostAwareSubscriptionBinding {
            val root = rootView as NestedScrollView
            
            // Find the main dialog layout (first LinearLayout child)
            val dialogLayout = root.getChildAt(0) as LinearLayout
            val innerDialogLayout = dialogLayout.getChildAt(0) as LinearLayout
            
            // Find views by ID within the dialog layout
            val lottieAnimationView = innerDialogLayout.findViewById<LottieAnimationView>(R.id.lottieAnimationView)
            val dialogTitle = innerDialogLayout.findViewById<TextView>(R.id.dialogTitle)
            val currentStatusLayout = innerDialogLayout.findViewById<LinearLayout>(R.id.currentStatusLayout)
            val currentTierText = innerDialogLayout.findViewById<TextView>(R.id.currentTierText)
            val currentUsageText = innerDialogLayout.findViewById<TextView>(R.id.currentUsageText)
            val freeTier = innerDialogLayout.findViewById<LinearLayout>(R.id.freeTier)
            val basicTier = innerDialogLayout.findViewById<LinearLayout>(R.id.basicTier)
            val basicPrice = innerDialogLayout.findViewById<TextView>(R.id.basicPrice)
            val proTier = innerDialogLayout.findViewById<LinearLayout>(R.id.proTier)
            val proPrice = innerDialogLayout.findViewById<TextView>(R.id.proPrice)
            val premiumTier = innerDialogLayout.findViewById<LinearLayout>(R.id.premiumTier)
            val premiumPrice = innerDialogLayout.findViewById<TextView>(R.id.premiumPrice)
            val ultraTier = innerDialogLayout.findViewById<LinearLayout>(R.id.ultraTier)
            val ultraPrice = innerDialogLayout.findViewById<TextView>(R.id.ultraPrice)
            
            // Find buttons in the main dialog layout
            val btnPurchase = dialogLayout.findViewById<Button>(R.id.btnPurchase)
            val btnClose = dialogLayout.findViewById<TextView>(R.id.btnClose)
            
            return DialogCostAwareSubscriptionBinding(
                root = root,
                dialogLayout = innerDialogLayout,
                lottieAnimationView = lottieAnimationView,
                dialogTitle = dialogTitle,
                currentStatusLayout = currentStatusLayout,
                currentTierText = currentTierText,
                currentUsageText = currentUsageText,
                freeTier = freeTier,
                basicTier = basicTier,
                basicPrice = basicPrice,
                proTier = proTier,
                proPrice = proPrice,
                premiumTier = premiumTier,
                premiumPrice = premiumPrice,
                ultraTier = ultraTier,
                ultraPrice = ultraPrice,
                btnPurchase = btnPurchase,
                btnClose = btnClose
            )
        }
    }
}