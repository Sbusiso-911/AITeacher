package com.playstudio.aiteacher

import android.app.Activity
import android.content.Context
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.appcompat.app.ActionBar

class AiRobotThemeManager {
    
    companion object {
        /**
         * Apply AI Robot Cyberpunk theme to an activity
         */
        fun applyTheme(activity: Activity) {
            // Update status bar
            activity.window?.statusBarColor = ContextCompat.getColor(activity, R.color.ai_robot_background)
            
            // Update action bar if present
            activity.actionBar?.setBackgroundDrawable(
                ContextCompat.getDrawable(activity, R.color.ai_robot_background)
            )
            
            // Update support action bar if present (more common)
            if (activity is androidx.appcompat.app.AppCompatActivity) {
                activity.supportActionBar?.setBackgroundDrawable(
                    ContextCompat.getDrawable(activity, R.color.ai_robot_background)
                )
            }
            
            // Update navigation bar
            activity.window?.navigationBarColor = ContextCompat.getColor(activity, R.color.ai_robot_background)
        }
        
        /**
         * Apply theme colors to primary buttons
         */
        fun styleAsPrimaryButton(button: Button, context: Context) {
            button.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.ai_robot_primary))
            button.setTextColor(ContextCompat.getColor(context, R.color.ai_robot_background))
        }
        
        /**
         * Apply theme colors to secondary buttons
         */
        fun styleAsSecondaryButton(button: Button, context: Context) {
            button.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.ai_robot_secondary))
            button.setTextColor(ContextCompat.getColor(context, R.color.ai_robot_primary))
        }
        
        /**
         * Apply theme colors to primary text
         */
        fun styleAsPrimaryText(textView: TextView, context: Context) {
            textView.setTextColor(ContextCompat.getColor(context, R.color.ai_robot_primary))
        }
        
        /**
         * Apply theme colors to secondary text
         */
        fun styleAsSecondaryText(textView: TextView, context: Context) {
            textView.setTextColor(ContextCompat.getColor(context, R.color.ai_robot_white))
        }
        
        /**
         * Apply theme colors to error text
         */
        fun styleAsErrorText(textView: TextView, context: Context) {
            textView.setTextColor(ContextCompat.getColor(context, R.color.ai_robot_status_error))
        }
        
        /**
         * Apply theme colors to warning text
         */
        fun styleAsWarningText(textView: TextView, context: Context) {
            textView.setTextColor(ContextCompat.getColor(context, R.color.ai_robot_status_warning))
        }
        
        /**
         * Apply theme colors to success text
         */
        fun styleAsSuccessText(textView: TextView, context: Context) {
            textView.setTextColor(ContextCompat.getColor(context, R.color.ai_robot_status_active))
        }
        
        /**
         * Get theme colors programmatically
         */
        object Colors {
            fun getPrimary(context: Context) = ContextCompat.getColor(context, R.color.ai_robot_primary)
            fun getSecondary(context: Context) = ContextCompat.getColor(context, R.color.ai_robot_secondary)
            fun getBackground(context: Context) = ContextCompat.getColor(context, R.color.ai_robot_background)
            fun getWhite(context: Context) = ContextCompat.getColor(context, R.color.ai_robot_white)
            fun getStatusActive(context: Context) = ContextCompat.getColor(context, R.color.ai_robot_status_active)
            fun getStatusWarning(context: Context) = ContextCompat.getColor(context, R.color.ai_robot_status_warning)
            fun getStatusError(context: Context) = ContextCompat.getColor(context, R.color.ai_robot_status_error)
        }
    }
}