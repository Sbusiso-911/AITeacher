package com.playstudio.aiteacher

import android.animation.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.marginStart
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.palette.graphics.Palette
import com.airbnb.lottie.LottieAnimationView
import com.android.billingclient.api.*
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import com.playstudio.aiteacher.databinding.ActivityMainBinding
import org.json.JSONArray
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.*

class MainActivity : AppCompatActivity(), PurchasesUpdatedListener, ChatFragment.OnSubscriptionClickListener {

    // Declaring keys for SharedPreferences
    private val prefsName = "prefs"
    private val keyAdFree = "ad_free"
    private val subscriptionTypeKey = "subscription_type"
    private val welcomeMessageShownKey = "welcome_message_shown"
    private val expirationTimeKey = "expiration_time"

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var lottieAnimationView: LottieAnimationView

    private var currentModel = "gpt-3.5-turbo"
    private var currentConversationId: String? = null

    private lateinit var billingClient: BillingClient
    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    private val base64EncodedPublicKey =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnW+9bE4fCNvpPazmIKiEuZSXg62IxL8Xsnn+pZ75PfCwlz5gSFbuqsME5sw2Qwzipz5qJ+IawXFtU/CUiy2LnQahJ7HHsV584ByU34b1XZPaowZdLcaodtstbdkwJk8VitjEWyICn/eIY7esccfonVxnHaIPjKyxks26zgUXRqTVzIm0rmf9vWap0cq+ms3XDdrcmYt1BdNEwPVF+qtbQa7A3v7YdnpPB3lDBgrOJVctS8a0AJ7zdBan+/DnyQuRdhr3EujQmSaxJu36ZhOi57/MZYrpn9FbjbIYUY7dS8YZjawDdCgJnt7ncC1BJQ4TjcXmxhsqc4yPGrxd0eDvuQIDAQAB"

    private var isAnimationPaused = false
    private var dX = 0f
    private var dY = 0f

    // ViewModel for Subscription status
    private val subscriptionViewModel: SubscriptionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash screen
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        supportActionBar?.setDisplayShowCustomEnabled(true)
        supportActionBar?.setCustomView(R.layout.custom_action_bar)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set the custom action bar background
        supportActionBar?.setBackgroundDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.action_bar_background
            )
        )

        // Reference the Lottie animation view
        lottieAnimationView = findViewById(R.id.lottieAnimation)

        // Set up the Lottie animation
        lottieAnimationView.setAnimation(R.raw.animation)
        lottieAnimationView.playAnimation()
        lottieAnimationView.loop(true)

        // Start fade-in animation for the action bar title
        val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        findViewById<TextView>(R.id.action_bar_title).startAnimation(fadeInAnimation)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(prefsName, Context.MODE_PRIVATE)

        // Apply custom font to the entire activity main view
        FontManager.applyFontToView(this, binding.root)
        loadSelectedColor()

        // Initialize BillingClient
        setupBillingClient()

        // Introduce a delay for the splash screen
        Handler(Looper.getMainLooper()).postDelayed({
            // Initialize the Mobile Ads SDK
            MobileAds.initialize(this) { initializationStatus ->
                Log.d("MainActivity", "Mobile Ads SDK initialized: $initializationStatus")
                loadAdBanner()
            }

            // Check the ad-free state from SharedPreferences
            val isAdFree = sharedPreferences.getBoolean(keyAdFree, false)
            setAdFree(isAdFree)

            // Check if the user has purchased the ad-free version
            checkAdFreeStatus()

            // Make the 'Remove Ads' button movable
            makeButtonMovable(binding.buyButton)

            binding.buyButton.setOnClickListener {
                // Handle buy button click
                it.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_click))
                showSubscriptionOptions()
            }

            // Set up other UI elements and listeners...
            setupUI()

            // Check subscription status and update ViewModel
            checkSubscriptionStatus()

            // Update badge and text based on the current subscription
            updateBadgeAndText()

            // Check if the welcome message has been shown
            if (!isWelcomeMessageShown()) {
                setWelcomeMessageShown(true)
            }
        }, 3000) // 3000 milliseconds = 3 seconds delay

        // Listen for fragment changes
        supportFragmentManager.addOnBackStackChangedListener {
            handleFragmentChanges()
        }

        // Apply animations to the "AI TEACHER" text and background
        applyAnimations()
    }

    override fun onSubscriptionClick() {
        binding.buyButton.performClick()
    }

    // Optionally, handle back navigation
    override fun onBackPressed() {
        val fragmentManager = supportFragmentManager
        if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }

    private fun applyAnimations() {
        val leftTitleContainer = findViewById<RelativeLayout>(R.id.left_title_container)
        val leftTitle = findViewById<TextView>(R.id.action_bar_left_title)
        val iText = findViewById<TextView>(R.id.i_text) // Reference to the 'I' TextView
        // Reference to the teacher container
        val teacherContainer = findViewById<LinearLayout>(R.id.teacher_container)

        // Fade in and out animation for the text
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out)

        // Scale animation for the text
        val scale = AnimationUtils.loadAnimation(this, R.anim.scale)

        // Slide in and out animation for the text
        val slideIn = AnimationUtils.loadAnimation(this, R.anim.slide_in)
        val slideOut = AnimationUtils.loadAnimation(this, R.anim.slide_out)

        // Color transition animation for the background
        val colorFrom = ContextCompat.getColor(this, android.R.color.black)
        val colorTo = ContextCompat.getColor(this, android.R.color.background_dark)
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo).apply {
            duration = 2000 // duration for each transition
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }

        colorAnimation.addUpdateListener { animator ->
            leftTitleContainer.setBackgroundColor(animator.animatedValue as Int)
        }

        // Start animations
        leftTitle.startAnimation(fadeIn)
        iText.startAnimation(fadeIn) // Start fade-in for 'I'
        teacherContainer.startAnimation(fadeIn) // Animate the LinearLayout for TEACHER
        leftTitle.startAnimation(scale)
        iText.startAnimation(scale) // Start scale animation for 'I'
        teacherContainer.startAnimation(scale)
        leftTitle.startAnimation(slideIn)
        iText.startAnimation(slideIn) // Start slide-in for 'I'
        teacherContainer.startAnimation(slideIn)
        colorAnimation.start()

        // Set up a handler to repeat the animations
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            leftTitle.startAnimation(fadeOut)
            iText.startAnimation(fadeOut) // Fade-out for 'I'
            teacherContainer.startAnimation(fadeOut) // Animate the LinearLayout for TEACHER
            leftTitle.startAnimation(slideOut)
            iText.startAnimation(slideOut) // Slide-out for 'I'
            teacherContainer.startAnimation(slideOut)
        }, 3000) // 3000 milliseconds = 3 seconds delay
    }

    private fun setupUI() {
        // Set up the click listener for the notification icon
        binding.recyclerView.post {
            val copyIcon: ImageView? = binding.recyclerView.findViewById(R.id.copy_icon)
            copyIcon?.setOnClickListener {
                it.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_click))
                val messageTextView: TextView? =
                    binding.recyclerView.findViewById(R.id.messageTextView)
                val message = messageTextView?.text.toString()

                // Copy the message to the custom clipboard
                CustomClipboard.copy(message)

                // Show a toast message
                showCustomToast("Message copied to custom clipboard")
            }
        }

        // Set up the click listener for the notification icon
        val notificationIcon: ImageView = findViewById(R.id.notificationIcon)
        notificationIcon.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_click))
            showUpdateDialog()
        }

        // Set up click listeners for suggested questions
        setupSuggestedQuestions()

        // Open ChatFragment on message input box touch
        binding.messageEditText.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                openChatFragmentWithModel(currentModel)
                true
            } else {
                false
            }
        }

        // Listen for fragment changes
        supportFragmentManager.addOnBackStackChangedListener {
            handleFragmentChanges()
        }

        // Load the last conversation ID and open the ChatFragment with it
        val lastConversationId = sharedPreferences.getString("last_conversation_id", null)
        if (lastConversationId != null) {
            // Do nothing, user will launch ChatFragment manually
        } else {
            // Initialize the first conversation on first launch
            currentConversationId = generateConversationId()
        }

        // Apply wave animation to the welcomeTextView
        val waveAnimation = AnimationUtils.loadAnimation(this, R.anim.wave_animation)
        binding.welcomeTextView.startAnimation(waveAnimation)

        // Move the welcome box to the side after 5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            val slideOutAnimation = AnimationUtils.loadAnimation(this, R.anim.slide_out)
            binding.welcomeTextView.startAnimation(slideOutAnimation)
        }, 5000) // 5000 milliseconds = 5 seconds delay

        // Set up the touch listener for the welcome box
        setupWelcomeBoxTouchListener()
    }

    private fun setupSuggestedQuestions() {
        // Set up click listeners for image questions
        val imageQuestions = listOf(
            findViewById(R.id.question1),
            findViewById(R.id.question2),
            findViewById(R.id.question3),
            findViewById(R.id.question4),
            findViewById(R.id.lastQuestion1),
            findViewById<ImageView>(R.id.lastQuestion2)
        )

        imageQuestions.forEach { imageView ->
            imageView.setOnClickListener {
                it.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_click))
                handleSuggestedQuestion(imageView.contentDescription.toString())
            }
        }

        // Set up click listeners for text view questions
        val textViewQuestions = listOf(
            findViewById(R.id.question5),
            findViewById(R.id.question6),
            findViewById(R.id.question7),
            findViewById(R.id.question8),
            findViewById(R.id.question9),
            findViewById(R.id.question10),
            findViewById(R.id.question11),
            findViewById(R.id.question12),
            findViewById(R.id.question13),
            findViewById(R.id.question14),
            findViewById(R.id.question15),
            findViewById(R.id.question16),
            findViewById(R.id.question17),
            findViewById(R.id.question18),
            findViewById(R.id.question19),
            findViewById(R.id.question20),
            findViewById(R.id.verticalQuestion1),
            findViewById(R.id.verticalQuestion2),
            findViewById(R.id.verticalQuestion3),
            findViewById(R.id.verticalQuestion4),
            findViewById(R.id.verticalQuestion5),
            findViewById(R.id.verticalQuestion6),
            findViewById<TextView>(R.id.verticalQuestion7)
        )

        textViewQuestions.forEach { textView ->
            textView?.apply {
                isClickable = true
                setOnClickListener {
                    it.startAnimation(AnimationUtils.loadAnimation(this@MainActivity, R.anim.button_click))
                    handleSuggestedQuestion(text.toString())
                }
            }
        }
    }

    private fun setupWelcomeBoxTouchListener() {
        binding.welcomeTextView.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (!isAnimationPaused) {
                            view.clearAnimation()
                            isAnimationPaused = true
                        }
                        dX = view.x - event.rawX
                        dY = view.y - event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        view.animate()
                            .x(event.rawX + dX)
                            .y(event.rawY + dY)
                            .setDuration(0)
                            .start()
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (isAnimationPaused) {
                            val waveAnimation = AnimationUtils.loadAnimation(
                                this@MainActivity,
                                R.anim.wave_animation
                            )
                            view.startAnimation(waveAnimation)
                            isAnimationPaused = false
                        }
                        return true
                    }

                    else -> return false
                }
            }
        })
    }


    private fun handleFragmentChanges() {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        val actionBarTitle: TextView? =
            supportActionBar?.customView?.findViewById(R.id.action_bar_title)
        val leftTitleContainer: RelativeLayout? =
            supportActionBar?.customView?.findViewById(R.id.left_title_container)

        if (fragment is ChatFragment) {
            binding.fragmentContainer.visibility = View.VISIBLE
            binding.nonChatElements.visibility = View.GONE
            // Update subscription status based on user subscription
            val isAdFree = sharedPreferences.getBoolean(keyAdFree, false)
            val expirationTime = sharedPreferences.getLong(expirationTimeKey, 0)
            fragment.updateSubscriptionStatus(isAdFree, expirationTime)

            // Set the title to "Chat" when the ChatFragment is displayed
            actionBarTitle?.text = "Chat"
            leftTitleContainer?.visibility = View.GONE

        } else {
            binding.fragmentContainer.visibility = View.GONE
            binding.nonChatElements.visibility = View.VISIBLE
            // Set the title to "Home" when the non-chat fragment is displayed
            actionBarTitle?.text = "Home"
            leftTitleContainer?.visibility = View.VISIBLE
        }
    }

    // Options Menu Methods
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }

            R.id.action_new_conversation -> {
                startNewConversation()
                true
            }

            R.id.action_change_background_color -> {
                showColorPickerDialog()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    // Chat Methods
    private fun startNewConversation() {
        currentConversationId = generateConversationId()

        val chatFragment = ChatFragment()
        val bundle = Bundle()
        bundle.putBoolean("is_new_conversation", true)
        bundle.putString("selected_model", currentModel)
        bundle.putString("conversation_id", currentConversationId)
        chatFragment.arguments = bundle

        supportFragmentManager.commit {
            replace(R.id.fragment_container, chatFragment)
            addToBackStack(null)
        }
        binding.fragmentContainer.visibility = View.VISIBLE
        binding.nonChatElements.visibility = View.GONE
    }

    private fun openChatFragmentWithModel(model: String) {
        val chatFragment = ChatFragment()
        val bundle = Bundle()
        bundle.putString("selected_model", model)
        bundle.putBoolean("is_new_conversation", true)
        chatFragment.arguments = bundle

        supportFragmentManager.commit {
            replace(R.id.fragment_container, chatFragment)
            addToBackStack(null)
        }
        binding.fragmentContainer.visibility = View.VISIBLE
        binding.nonChatElements.visibility = View.GONE

        // Call to update the action bar title
        handleFragmentChanges()
    }

    private fun openChatFragmentWithMessage(message: String) {
        val chatFragment = ChatFragment()
        val bundle = Bundle()
        bundle.putString("suggested_message", message)
        bundle.putString("selected_model", currentModel)
        chatFragment.arguments = bundle

        supportFragmentManager.commit {
            replace(R.id.fragment_container, chatFragment)
            addToBackStack(null)
        }
        binding.fragmentContainer.visibility = View.VISIBLE
        binding.nonChatElements.visibility = View.GONE
    }

    private fun setAdFree(isAdFree: Boolean) {
        sharedPreferences.edit().putBoolean(keyAdFree, isAdFree).apply()
        val adView: AdView = findViewById(R.id.adView)
        val adContainer: FrameLayout = findViewById(R.id.adContainer)

        if (isAdFree) {
            adContainer.visibility = View.GONE
            adView.visibility = View.GONE
            hideBuyButton()
        } else {
            adContainer.visibility = View.VISIBLE
            adView.visibility = View.VISIBLE
            loadAdBanner()
            showBuyButton()
            startButtonAnimation()
        }
    }

    private fun hideBuyButton() {
        binding.buyButton.visibility = View.GONE
    }

    private fun showBuyButton() {
        binding.buyButton.visibility = View.VISIBLE
    }

    private fun showUpdateDialog() {
        val packageInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName

        val builder = AlertDialog.Builder(this)
        builder.setTitle("App Update")
        builder.setMessage("Version: $versionName\n\nNo updates available at the moment.")
        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun isWelcomeMessageShown(): Boolean {
        return sharedPreferences.getBoolean(welcomeMessageShownKey, false)
    }

    private fun setWelcomeMessageShown(shown: Boolean) {
        sharedPreferences.edit().putBoolean(welcomeMessageShownKey, shown).apply()
    }

    private fun showCustomToast(message: String) {
        val inflater = layoutInflater
        val layout: View =
            inflater.inflate(R.layout.custom_toast, findViewById(R.id.custom_toast_container))

        val toastIcon: ImageView = layout.findViewById(R.id.toast_icon)
        val toastText: TextView = layout.findViewById(R.id.toast_text)

        toastText.text = message
        toastIcon.setImageResource(R.drawable.your_custom_icon) // Set your custom icon

        with(Toast(applicationContext)) {
            duration = Toast.LENGTH_SHORT
            view = layout
            show()
        }
    }

    private fun handleSuggestedQuestion(question: String) {
        openChatFragmentWithMessage(question)
    }

    private fun loadAdBanner() {
        val isAdFree = sharedPreferences.getBoolean(keyAdFree, false)
        val expirationTime = sharedPreferences.getLong(expirationTimeKey, 0)

        if (isAdFree || System.currentTimeMillis() < expirationTime) {
            // If the user has an active subscription, do not load the ad
            findViewById<FrameLayout>(R.id.adContainer).visibility = View.GONE
            return
        }

        val adRequest = AdRequest.Builder().build()
        val adView: AdView = findViewById(R.id.adView)
        adView.loadAd(adRequest)

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                // Ad loaded successfully, show the ad container
                findViewById<FrameLayout>(R.id.adContainer).visibility = View.VISIBLE
            }

            override fun onAdFailedToLoad(p0: LoadAdError) {
                // Ad failed to load, hide the ad container
                findViewById<FrameLayout>(R.id.adContainer).visibility = View.GONE
            }
        }
    }

    private fun generateConversationId(): String {
        return UUID.randomUUID().toString()
    }

    // Billing Methods
    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            showCustomToast("Purchase canceled")
        } else {
            Log.e("MainActivity", "Error during purchase: ${billingResult.debugMessage}")
            showCustomToast("Error: ${billingResult.debugMessage}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Verify the purchase
            if (verifyPurchase(purchase)) {
                // Grant the user the purchased subscription
                when (purchase.products[0]) {
                    "subscription_7days" -> {
                        showCustomToast("Weekly subscription purchased")
                        setSubscriptionTypeAndBadge("bronze", "Pro")
                        setAdFree(true)
                        saveSubscriptionExpiration(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L) // 7 days
                        updateChatFragmentSubscriptionStatus()
                    }

                    "monthly_subscription" -> {
                        showCustomToast("Monthly subscription purchased")
                        setSubscriptionTypeAndBadge("silver", "Pro")
                        setAdFree(true)
                        saveSubscriptionExpiration(System.currentTimeMillis() + 30 * 24 * 60 * 60 * 1000L) // 1 month
                        updateChatFragmentSubscriptionStatus()
                    }

                    "yearly_subscription" -> {
                        showCustomToast("Yearly subscription purchased")
                        setSubscriptionTypeAndBadge("gold", "Pro")
                        setAdFree(true)
                        saveSubscriptionExpiration(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L) // 1 year
                        updateChatFragmentSubscriptionStatus()
                    }
                }

                // Acknowledge the purchase if required
                if (!purchase.isAcknowledged) {
                    val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.d("MainActivity", "Purchase acknowledged")
                        } else {
                            Log.e(
                                "MainActivity",
                                "Error acknowledging purchase: ${billingResult.debugMessage}"
                            )
                        }
                    }
                }
            } else {
                Log.e("MainActivity", "Purchase verification failed")
            }
        }
    }

    private fun verifyPurchase(purchase: Purchase): Boolean {
        val signedData = purchase.originalJson
        val signature = purchase.signature
        return Security.verifyPurchase(base64EncodedPublicKey, signedData, signature)
    }

    private fun setSubscriptionTypeAndBadge(badge: String, text: String) {
        sharedPreferences.edit().apply {
            putString(subscriptionTypeKey, badge)
            apply()
        }

        updateBadgeAndText()
    }

    private fun updateBadgeAndText() {
        val subscriptionType = sharedPreferences.getString(subscriptionTypeKey, null)
        val badgeImageView = findViewById<ImageView>(R.id.badgeImageView)
        val badgeTextView = findViewById<TextView>(R.id.badgeTextView)
        val expirationTime = sharedPreferences.getLong(expirationTimeKey, 0)

        Log.d("MainActivity", "Current time: ${System.currentTimeMillis()}, Expiration time: $expirationTime")

        if (System.currentTimeMillis() < expirationTime) {
            // Currently subscribed
            when (subscriptionType) {
                "bronze" -> {
                    binding.badgeImageView.setImageResource(R.drawable.bronze_badge)
                    binding.badgeTextView.text = "Pro"
                    Log.d("MainActivity", "Subscription type: bronze, Text: Pro")
                }
                "silver" -> {
                    binding.badgeImageView.setImageResource(R.drawable.silver_badge)
                    binding.badgeTextView.text = "Pro"
                    Log.d("MainActivity", "Subscription type: silver, Text: Pro")
                }
                "gold" -> {
                    binding.badgeImageView.setImageResource(R.drawable.gold_badge)
                    binding.badgeTextView.text = "Pro"
                    Log.d("MainActivity", "Subscription type: gold, Text: Pro")
                }
            }
        } else {
            // Subscription expired
            badgeImageView.setImageResource(R.drawable.bronze_badge) // Default badge
            badgeTextView.text = "Light"
            Log.d("MainActivity", "Subscription expired, Text: Light")
            showAds()
            showBuyButton()
        }
    }

    private fun saveSubscriptionExpiration(expirationTime: Long) {
        sharedPreferences.edit().putLong(expirationTimeKey, expirationTime).apply()
        updateChatFragmentSubscriptionStatus()
    }

    private fun checkAdFreeStatus() {
        val isAdFree = sharedPreferences.getBoolean(keyAdFree, false)
        val expirationTime = sharedPreferences.getLong(expirationTimeKey, 0)
        if (isAdFree) {
            setAdFree(true)
        } else {
            if (System.currentTimeMillis() < expirationTime) {
                setAdFree(true)
            } else {
                setAdFree(false)
            }
        }
        subscriptionViewModel.updateSubscriptionStatus(isAdFree, expirationTime)
    }

    private fun updateChatFragmentSubscriptionStatus() {
        // Call this function to notify ChatFragment of the new subscription status
        val isAdFree = sharedPreferences.getBoolean(keyAdFree, false)
        val expirationTime = sharedPreferences.getLong(expirationTimeKey, 0)
        subscriptionViewModel.updateSubscriptionStatus(isAdFree, expirationTime)
    }

    private fun showSubscriptionOptions() {
        val options = arrayOf("Weekly Subscription", "Monthly Subscription", "Yearly Subscription")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Choose a subscription")
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> startPurchaseFlow("subscription_7days")
                1 -> startPurchaseFlow("monthly_subscription")
                2 -> startPurchaseFlow("yearly_subscription")
            }
        }
        builder.show()
    }

    private fun startPurchaseFlow(productId: String) {
        val productDetails = productDetailsMap[productId]
        if (productDetails != null) {
            if (productDetails.subscriptionOfferDetails.isNullOrEmpty()) {
                Log.e("MainActivity", "No offer details for subscription: $productId")
                showCustomToast("Error: No offer token available.")
                return
            }
            val offerToken = productDetails.subscriptionOfferDetails!![0].offerToken
            val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParams))
                .build()

            billingClient.launchBillingFlow(this, flowParams)
        } else {
            Log.e("MainActivity", "ProductDetails not found for productId: $productId")
            showCustomToast("Error: Product details not found")
        }
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(this)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("MainActivity", "Billing setup finished successfully")
                    queryAvailableSubscriptions()
                } else {
                    Log.e("MainActivity", "Error setting up billing: ${billingResult.debugMessage}")
                    showCustomToast("Error setting up billing: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.e("MainActivity", "Billing service disconnected")
                showCustomToast("Billing service disconnected. Trying to reconnect...")
                setupBillingClient()
            }
        })
    }

    private fun queryAvailableSubscriptions() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("subscription_7days")
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("monthly_subscription")
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("yearly_subscription")
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList != null) {
                for (productDetails in productDetailsList) {
                    if (productDetails.subscriptionOfferDetails?.isNotEmpty() == true) {
                        productDetailsMap[productDetails.productId] = productDetails
                        Log.d(
                            "MainActivity",
                            "ProductDetails found for ${productDetails.productId}: ${productDetails.title}"
                        )
                    } else {
                        Log.e(
                            "MainActivity",
                            "No offer details for subscription: ${productDetails.productId}"
                        )
                    }
                }
            } else {
                Log.e("MainActivity", "Error querying products: ${billingResult.debugMessage}")
                showCustomToast("Error querying products: ${billingResult.debugMessage}")
            }
        }
    }

    private fun checkSubscriptionStatus() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                var hasActiveSubscription = false
                for (purchase in purchases) {
                    if (purchase.products.contains("subscription_7days") || purchase.products.contains(
                            "monthly_subscription"
                        ) || purchase.products.contains("yearly_subscription")
                    ) {
                        if (isSubscriptionActive(purchase)) {
                            hasActiveSubscription = true
                            setAdFree(true)
                        }
                    }
                }
                if (!hasActiveSubscription) {
                    showAds()
                    startButtonAnimation()
                }
            } else {
                Log.e("MainActivity", "Error querying purchases: ${billingResult.debugMessage}")
                showCustomToast("Error querying purchases: ${billingResult.debugMessage}")
            }
            val isAdFree = sharedPreferences.getBoolean(keyAdFree, false)
            val expirationTime = sharedPreferences.getLong(expirationTimeKey, 0)
            subscriptionViewModel.updateSubscriptionStatus(isAdFree, expirationTime)
            updateBadgeAndText() // Ensure the badge and text are updated
        }
    }

    private fun isSubscriptionActive(purchase: Purchase): Boolean {
        val expirationTime = sharedPreferences.getLong(expirationTimeKey, 0)
        return System.currentTimeMillis() < expirationTime
    }

    private fun showAds() {
        val adView: AdView = findViewById(R.id.adView)
        val adContainer: FrameLayout = findViewById(R.id.adContainer)
        adContainer.visibility = View.VISIBLE
        adView.visibility = View.VISIBLE
        loadAdBanner()
    }

    private fun changeBackgroundColor(drawableResId: Int) {
        try {
            val nonChatElements: View = findViewById(R.id.non_chat_elements)
            nonChatElements.setBackgroundResource(drawableResId)
            saveSelectedColor(drawableResId)

            // Assuming you have a method to determine if the drawable is dark or light
            val isDark = isDrawableDark(drawableResId)
            val textColor = if (isDark) Color.WHITE else Color.BLACK

            // binding.gptoptionsButton.setTextColor(textColor)
            binding.buyButton.setTextColor(textColor)

            // Ensure 'Remove Ads' and 'Clear Recent Conversations' buttons always have black text
            //binding.clearRecentConversationsButton.setTextColor(Color.BLACK)
            binding.buyButton.setTextColor(Color.BLACK)

            binding.messageEditText.setTextColor(textColor)
            //binding.sendButton.setTextColor(textColor)

            binding.notificationIcon.setImageResource(R.drawable.ic_notification)
            //binding.mainIcon.setImageResource(R.drawable.ic_envelope)

            val notificationText: TextView = findViewById(R.id.notificationText)
            // val chatText: TextView = findViewById(R.id.chatText)
            notificationText.setTextColor(textColor)
            //chatText.setTextColor(textColor)
        } catch (e: android.content.res.Resources.NotFoundException) {
            Log.e("MainActivity", "Resource not found: $drawableResId", e)
            // Set a default color if the resource is not found
            val defaultDrawableResId = R.drawable.gradient_black
            val nonChatElements: View = findViewById(R.id.non_chat_elements)
            nonChatElements.setBackgroundResource(defaultDrawableResId)
            saveSelectedColor(defaultDrawableResId)
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 100 // Default width
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 100 // Default height

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun isDrawableDark(drawableResId: Int): Boolean {
        val drawable = resources.getDrawable(drawableResId, null)
        val bitmap = drawableToBitmap(drawable)

        val palette = Palette.from(bitmap).generate()
        val dominantColor = palette.getDominantColor(Color.WHITE)

        return isColorDark(dominantColor)
    }

    private fun isColorDark(color: Int): Boolean {
        val darkness =
            1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.5
    }

    private fun saveSelectedColor(drawableResId: Int) {
        sharedPreferences.edit().putInt("selected_color", drawableResId).apply()
    }

    private fun loadSelectedColor() {
        val drawableResId = sharedPreferences.getInt(
            "selected_color",
            R.drawable.gradient_black // Default gradient drawable
        )
        changeBackgroundColor(drawableResId)
    }

    private fun setBackgroundColor(drawableResId: Int) {
        val nonChatElements: View = findViewById(R.id.non_chat_elements)
        nonChatElements.setBackgroundColor(drawableResId)
        findViewById<View>(R.id.non_chat_elements).setBackgroundResource(drawableResId)
        saveSelectedColor(drawableResId)
    }

    private fun showColorPickerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)

        val dialog = builder.create()

        // Adding click listeners for all color views
        dialogView.findViewById<View>(R.id.colorRed).setOnClickListener {
            val drawableResId = R.drawable.gradient_red
            setBackgroundColor(drawableResId)
            saveSelectedColor(drawableResId)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.colorGreen).setOnClickListener {
            val drawableResId = R.drawable.gradient_green
            setBackgroundColor(drawableResId)
            saveSelectedColor(drawableResId)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.colorBlue).setOnClickListener {
            val drawableResId = R.drawable.gradient_blue
            setBackgroundColor(drawableResId)
            saveSelectedColor(drawableResId)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.colorCyan).setOnClickListener {
            val drawableResId = R.drawable.gradient_cyan
            setBackgroundColor(drawableResId)
            saveSelectedColor(drawableResId)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.colorMagenta).setOnClickListener {
            val drawableResId = R.drawable.gradient_magenta
            setBackgroundColor(drawableResId)
            saveSelectedColor(drawableResId)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.colorBlack).setOnClickListener {
            val drawableResId = R.drawable.gradient_black
            setBackgroundColor(drawableResId)
            saveSelectedColor(drawableResId)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun startButtonAnimation() {
        // Load the pulsing animation
        val pulseAnimation: Animation = AnimationUtils.loadAnimation(this, R.anim.pulse)
        binding.buyButton.startAnimation(pulseAnimation)

        // Create a glowing animation
        val colorFrom = ContextCompat.getColor(this, R.color.colorAccent)
        val colorTo = ContextCompat.getColor(this, R.color.light_grey)
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo).apply {
            duration = 1000 // duration for each transition
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }

        colorAnimation.addUpdateListener { animator ->
            binding.buyButton.setBackgroundColor(animator.animatedValue as Int)
        }

        colorAnimation.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::billingClient.isInitialized && billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    // Security class for verifying purchases
    object Security {
        fun verifyPurchase(
            base64PublicKey: String,
            signedData: String,
            signature: String
        ): Boolean {
            return try {
                val key = generatePublicKey(base64PublicKey)
                val signatureInstance = Signature.getInstance("SHA1withRSA")
                signatureInstance.initVerify(key)
                signatureInstance.update(signedData.toByteArray())
                signatureInstance.verify(Base64.decode(signature, Base64.DEFAULT))
            } catch (e: Exception) {
                Log.e("Security", "Error verifying purchase: ${e.message}")
                false
            }
        }

        private fun generatePublicKey(base64PublicKey: String): PublicKey {
            val keyFactory = KeyFactory.getInstance("RSA")
            val keySpec = X509EncodedKeySpec(Base64.decode(base64PublicKey, Base64.DEFAULT))
            return keyFactory.generatePublic(keySpec)
        }
    }

    private fun makeButtonMovable(button: Button) {
        button.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dX = view.x - event.rawX
                        dY = view.y - event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        view.animate()
                            .x(event.rawX + dX)
                            .y(event.rawY + dY)
                            .setDuration(0)
                            .start()
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (Math.abs(event.rawX + dX - view.x) < 10 && Math.abs(event.rawY + dY - view.y) < 10) {
                            view.performClick()
                        }
                        return true
                    }
                    else -> return false
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Set the title to "Home" when the activity is resumed
        supportActionBar?.title = "Home"
        checkSubscriptionStatus() // Check subscription status on resume
        updateBadgeAndText() // Update badge and text on resume
    }
}