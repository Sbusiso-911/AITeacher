package com.playstudio.aiteacher

import android.Manifest
import android.animation.*
import android.app.*
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.util.Base64
import android.util.Log
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.appcompat.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.palette.graphics.Palette
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.airbnb.lottie.LottieAnimationView
import com.android.billingclient.api.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.playstudio.aiteacher.SubscriptionUIManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import com.playstudio.aiteacher.databinding.ActivityMainBinding
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.*
import android.widget.Button
import android.widget.Toast
import java.util.concurrent.TimeUnit
import androidx.lifecycle.lifecycleScope
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
//import com.playstudio.aiteacher.EmailProviderHelper // This was previously commented out
import com.playstudio.aiteacher.utils.FileUtils
// import com.playstudio.aiteacher.profile.ProfileIntegration // Replaced by UnifiedDataManager
// import com.playstudio.aiteacher.profile.WebappSwitchingService // Replaced by UnifiedBackendClient
import com.playstudio.aiteacher.backend.UnifiedDataManager
import com.playstudio.aiteacher.backend.UnifiedBackendClient
import com.playstudio.aiteacher.profile.AuthenticationService
import com.playstudio.aiteacher.profile.FirebaseAuthenticationService
import com.playstudio.aiteacher.profile.ProfileActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import android.accounts.Account
import android.accounts.AccountManager
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Typeface
import android.text.InputType
import android.provider.AlarmClock
import android.provider.CalendarContract
import java.util.*
import android.text.TextUtils
import android.util.TypedValue
import android.view.ViewGroup
//import android.text.TextUtils
import com.google.android.material.button.MaterialButton
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.NumberFormat
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.Purchase
import com.playstudio.aiteacher.billing.GooglePlayBillingSync


class MainActivity : AppCompatActivity(), PurchasesUpdatedListener, ChatFragment.OnSubscriptionClickListener {



    private lateinit var subscriptionUIManager: SubscriptionUIManager
    
    // Unified Backend Services
    private lateinit var unifiedDataManager: UnifiedDataManager
    private lateinit var authenticationService: AuthenticationService
    private lateinit var firebaseAuthService: FirebaseAuthenticationService
    
    // Activity Result Launchers
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    private val skuDetailsList = mutableListOf<SkuDetails>()
    // Badge elements removed for cleaner design
    // private lateinit var subscriptionStatusText: TextView
    // private lateinit var subscriptionTimer: TextView
    private var timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

        // Initialize EmailProviderHelper correctly
    private val emailProviderHelper by lazy { EmailProviderHelper(this) }
    // private val EMAIL_PROVIDER_REQUEST = 1004 // This seems unused, EmailProviderHelper.EMAIL_PICK_REQUEST is used

    private lateinit var btnExtractText: Button
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var themeManager: ThemeManager
    private val prefsName = "prefs"
    private val keyAdFree = "ad_free"
    private val subscriptionTypeKey = "subscription_type"
    private val welcomeMessageShownKey = "welcome_message_shown"
    private val expirationTimeKey = "expiration_time"
    private val thankYouDialogShownKey = "thank_you_dialog_shown" // New key for tracking the dialog
    private val firstTimeUserKey = "first_time_user"
    private val lastInteractionTimeKey = "last_interaction_time"
    private val subscriptionDialogShownKey = "subscription_dialog_shown" // New key for tracking the subscription dialog


    private var secretTapCount = 0
    private var lastSecretTapTime = 0L
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var lottieAnimationView: LottieAnimationView
    private lateinit var fingerAnimationView: LottieAnimationView
    private lateinit var scrollView: ScrollView

    // User/account identifier for syncing with the web app
    private lateinit var userId: String

    private var versionTapCount = 0
    private var lastVersionTapTime = 0L


    private var currentModel = "gpt-3.5-turbo"
    private var currentConversationId: String? = null

    private lateinit var billingClient: BillingClient
    private val productDetailsMap = mutableMapOf<String, ProductDetails>()
    private lateinit var billingSync: GooglePlayBillingSync

    private val base64EncodedPublicKey =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnW+9bE4fCNvpPazmIKiEuZSXg62IxL8Xsnn+pZ75PfCwlz5gSFbuqsME5sw2Qwzipz5qJ+IawXFtU/CUiy2LnQahJ7HHsV584ByU34b1XZPaowZdLcaodtstbdkwJk8VitjEWyICn/eIY7esccfonVxnHaIPjKyxks26zgUXRqTVzIm0rmf9vWap0cq+ms3XDdrcmYt1BdNEwPVF+qtbQa7A3v7YdnpPB3lDBgrOJVctS8a0AJ7zdBan+/DnyQuRdhr3EujQmSaxJu36ZhOi57/MZYrpn9FbjbIYUY7dS8YZjawDdCgJnt7ncC1BJQ4TjcXmxhsqc4yPGrxd0eDvuQIDAQAB"

    private var isAnimationPaused = false
    private var dX = 0f
    private var dY = 0f

    // ViewModel for Subscription status
    private val subscriptionViewModel: SubscriptionViewModel by viewModels()


    // Cached last interaction time
    private var lastInteractionTime: Long = 0

    // Define subjects, chapters, topics, and subtopics
    private val subjects = mapOf(
        "Maths" to mapOf(
            "Algebra" to mapOf(
                "Linear Equations" to listOf("Solving Linear Equations", "Graphing Linear Equations", "Applications of Linear Equations", "Systems of Linear Equations", "Word Problems", "Inequalities", "Absolute Value Equations", "Piecewise Functions", "Linear Programming", "Matrix Solutions"),
                "Quadratic Equations" to listOf("Solving Quadratic Equations", "Graphing Quadratic Equations", "Quadratic Formula", "Completing the Square", "Factoring Quadratics", "Vertex Form", "Applications of Quadratics", "Quadratic Inequalities", "Complex Roots", "Parabolas")
            ),
            "Geometry" to mapOf(
                "Triangles" to listOf("Types of Triangles", "Triangle Theorems", "Congruence", "Similarity", "Pythagorean Theorem", "Trigonometry Basics", "Special Right Triangles", "Area and Perimeter", "Triangle Inequality", "Heron's Formula"),
                "Circles" to listOf("Properties of Circles", "Circle Theorems", "Arcs and Angles", "Chords and Secants", "Tangents", "Sector Area", "Segment Area", "Equations of Circles", "Inscribed Angles", "Circumference and Area")
            ),
            "Trigonometry" to mapOf(
                "Trigonometric Functions" to listOf("Sine", "Cosine", "Tangent", "Cotangent", "Secant", "Cosecant", "Inverse Trigonometric Functions", "Graphs of Trigonometric Functions", "Trigonometric Identities", "Applications of Trigonometry"),
                "Advanced Trigonometry" to listOf("Law of Sines", "Law of Cosines", "Trigonometric Equations", "Polar Coordinates", "Complex Numbers in Trigonometry", "Hyperbolic Functions", "Fourier Series", "Wave Functions", "Spherical Trigonometry", "Elliptic Functions")
            ),
            "Complex Maths" to mapOf(
                "Complex Numbers" to listOf("Introduction to Complex Numbers", "Operations with Complex Numbers", "Polar Form", "Euler's Formula", "Complex Conjugates", "Roots of Complex Numbers", "Complex Functions", "Complex Analysis", "Applications of Complex Numbers", "Mandelbrot Set"),
                "Advanced Complex Maths" to listOf("Complex Integration", "Residue Theorem", "Laurent Series", "Conformal Mapping", "Riemann Surfaces", "Analytic Continuation", "Complex Dynamics", "Harmonic Functions", "Complex Differential Equations", "Applications of Complex Analysis")
            ),
            "Exponents" to mapOf(
                "Basic Exponents" to listOf("Laws of Exponents", "Simplifying Exponential Expressions", "Exponential Equations", "Exponential Functions", "Graphing Exponential Functions", "Applications of Exponents", "Exponential Growth and Decay", "Compound Interest", "Logarithmic Functions", "Inverse Functions"),
                "Advanced Exponents" to listOf("Rational Exponents", "Radicals", "Exponential Models", "Exponential Regression", "Exponential Series", "Exponential Transformations", "Exponential Inequalities", "Exponential Approximations", "Exponential Algorithms", "Exponential Applications")
            ),
            "Logs" to mapOf(
                "Basic Logarithms" to listOf("Definition of Logarithms", "Properties of Logarithms", "Logarithmic Equations", "Logarithmic Functions", "Graphing Logarithmic Functions", "Applications of Logarithms", "Logarithmic Scales", "Natural Logarithms", "Change of Base Formula", "Inverse Functions"),
                "Advanced Logarithms" to listOf("Logarithmic Models", "Logarithmic Regression", "Logarithmic Series", "Logarithmic Transformations", "Logarithmic Inequalities", "Logarithmic Approximations", "Logarithmic Algorithms", "Logarithmic Applications", "Logarithmic Integrals", "Logarithmic Differentiation")
            ),
            "Compounds" to mapOf(
                "Basic Compounds" to listOf("Introduction to Compounds", "Types of Compounds", "Properties of Compounds", "Formation of Compounds", "Chemical Bonds", "Molecular Structure", "Chemical Reactions", "Stoichiometry", "Balancing Equations", "Applications of Compounds"),
                "Advanced Compounds" to listOf("Organic Compounds", "Inorganic Compounds", "Coordination Compounds", "Polymeric Compounds", "Biochemical Compounds", "Pharmaceutical Compounds", "Industrial Compounds", "Environmental Compounds", "Nanocompounds", "Compound Synthesis")
            )
        ),
        "Science" to mapOf(
            "Physics" to mapOf(
                "Newton's Laws" to listOf("First Law", "Second Law", "Third Law", "Applications of Newton's Laws", "Friction", "Tension", "Normal Force", "Free-Body Diagrams", "Inclined Planes", "Circular Motion"),
                "Thermodynamics" to listOf("Laws of Thermodynamics", "Heat Transfer", "Thermal Expansion", "Specific Heat", "Phase Changes", "Heat Engines", "Entropy", "Thermodynamic Processes", "Carnot Cycle", "Applications of Thermodynamics")
            ),
            "Chemistry" to mapOf(
                "Periodic Table" to listOf("Elements", "Groups and Periods", "Metals and Nonmetals", "Transition Metals", "Lanthanides and Actinides", "Periodic Trends", "Electron Configuration", "Valence Electrons", "Atomic Radius", "Ionization Energy"),
                "Chemical Reactions" to listOf("Types of Reactions", "Balancing Equations", "Reaction Rates", "Equilibrium", "Le Chatelier's Principle", "Acids and Bases", "Redox Reactions", "Precipitation Reactions", "Combustion Reactions", "Synthesis and Decomposition")
            ),
            "Biology" to mapOf(
                "Cell Biology" to listOf("Cell Structure", "Cell Membrane", "Cell Division", "Cell Metabolism", "Cell Communication", "Stem Cells", "Cell Differentiation", "Cell Cycle", "Apoptosis", "Cancer Biology"),
                "Genetics" to listOf("Mendelian Genetics", "DNA Structure", "Gene Expression", "Genetic Mutations", "Genetic Engineering", "Population Genetics", "Epigenetics", "Genomics", "Inheritance Patterns", "Genetic Disorders")
            )
        ),
        "Technology" to mapOf(
            "IoT" to mapOf(
                "Introduction to IoT" to listOf("What is IoT?", "IoT Applications", "IoT Architecture", "IoT Protocols", "IoT Security", "IoT Platforms", "IoT Devices", "IoT Data Management", "IoT Analytics", "IoT Standards"),
                "IoT Applications" to listOf("Smart Homes", "Industrial IoT", "Healthcare IoT", "Agriculture IoT", "Smart Cities", "Wearable Devices", "Connected Vehicles", "Environmental Monitoring", "IoT in Retail", "IoT in Manufacturing")
            ),
            "AI" to mapOf(
                "Machine Learning" to listOf("Supervised Learning", "Unsupervised Learning", "Reinforcement Learning", "Neural Networks", "Decision Trees", "Support Vector Machines", "Clustering", "Dimensionality Reduction", "Model Evaluation", "Feature Engineering"),
                "Deep Learning" to listOf("Neural Networks", "Convolutional Neural Networks", "Recurrent Neural Networks", "Generative Adversarial Networks", "Transfer Learning", "Deep Reinforcement Learning", "Natural Language Processing", "Computer Vision", "Speech Recognition", "Deep Learning Frameworks")
            ),
            "Digital Electronics" to mapOf(
                "Basic Concepts" to listOf("Binary Numbers", "Logic Gates", "Boolean Algebra", "Combinational Circuits", "Sequential Circuits", "Flip-Flops", "Counters", "Registers", "Multiplexers", "Demultiplexers"),
                "Advanced Concepts" to listOf("Digital Design", "Digital Signal Processing", "Microcontrollers", "Field-Programmable Gate Arrays", "Digital Communication", "Digital Storage", "Digital Interfaces", "Digital Control Systems", "Digital Testing", "Digital Applications")
            ),
            "Computer Architecture" to mapOf(
                "Basic Concepts" to listOf("CPU Architecture", "Memory Hierarchy", "Input/Output Systems", "Instruction Set Architecture", "Pipelining", "Cache Memory", "Virtual Memory", "Parallel Processing", "Computer Performance", "Computer Organization"),
                "Advanced Concepts" to listOf("Superscalar Architecture", "Multicore Processors", "Graphics Processing Units", "Network Processors", "Embedded Systems", "Real-Time Systems", "Quantum Computing", "Neuromorphic Computing", "Computer Security", "Computer Design")
            ),
            "x86 Assembly Language" to mapOf(
                "Basic Concepts" to listOf("Introduction to x86 Assembly", "Registers", "Memory Addressing", "Data Movement Instructions", "Arithmetic Instructions", "Control Flow Instructions", "Subroutines", "Interrupts", "Input/Output Instructions", "Assembly Language Tools"),
                "Advanced Concepts" to listOf("Advanced Addressing Modes", "Floating-Point Instructions", "SIMD Instructions", "System Programming", "Optimization Techniques", "Inline Assembly", "Assembly Language Debugging", "Assembly Language Profiling", "Assembly Language Security", "Assembly Language Applications")
            )
        ),
        "Coding" to mapOf(
            "Python" to mapOf(
                "Basics of Python" to listOf("Syntax", "Data Types", "Control Structures", "Functions", "Modules", "File I/O", "Error Handling", "List Comprehensions", "Lambda Functions", "Decorators"),
                "Advanced Python" to listOf("Decorators", "Generators", "Context Managers", "Metaclasses", "Concurrency", "Networking", "Web Development", "Data Analysis", "Machine Learning", "Testing and Debugging")
            ),
            "Java" to mapOf(
                "Basics of Java" to listOf("Syntax", "OOP Concepts", "Control Structures", "Methods", "Arrays", "Inheritance", "Interfaces", "Exception Handling", "File I/O", "Collections Framework"),
                "Advanced Java" to listOf("Streams", "Concurrency", "Networking", "JDBC", "JavaFX", "Servlets and JSP", "Spring Framework", "Microservices", "Testing and Debugging", "Performance Optimization")
            )
        ),
        "Computer Science" to mapOf(
            "Data Structures" to mapOf(
                "Arrays" to listOf("Introduction to Arrays", "Array Operations", "Dynamic Arrays", "Multidimensional Arrays", "Sparse Arrays", "Array Sorting", "Array Searching", "Array Merging", "Array Rotation", "Array Applications"),
                "Linked Lists" to listOf("Singly Linked List", "Doubly Linked List", "Circular Linked List", "Skip List", "Linked List Operations", "Linked List Sorting", "Linked List Searching", "Linked List Reversal", "Linked List Merging", "Linked List Applications")
            ),
            "Algorithms" to mapOf(
                "Sorting" to listOf("Bubble Sort", "Quick Sort", "Merge Sort", "Insertion Sort", "Selection Sort", "Heap Sort", "Radix Sort", "Counting Sort", "Bucket Sort", "Tim Sort"),
                "Searching" to listOf("Linear Search", "Binary Search", "Depth-First Search", "Breadth-First Search", "Jump Search", "Exponential Search", "Interpolation Search", "Fibonacci Search", "Sublist Search", "Pattern Matching")
            )
        ),
        "IT" to mapOf(
            "Networking" to mapOf(
                "OSI Model" to listOf("Layers of OSI Model", "Functions of Each Layer", "Protocols in OSI Model", "Data Encapsulation", "Network Devices", "Network Topologies", "Network Addressing", "Network Security", "Network Troubleshooting", "Network Performance"),
                "TCP/IP" to listOf("Layers of TCP/IP", "Protocols in TCP/IP", "IP Addressing", "Subnetting", "Routing", "TCP vs UDP", "DNS", "DHCP", "NAT", "VPN")
            ),
            "Cloud Computing" to mapOf(
                "AWS" to listOf("Introduction to AWS", "AWS Services", "AWS Architecture", "AWS Security", "AWS Pricing", "AWS Management", "AWS Deployment", "AWS Monitoring", "AWS Compliance", "AWS Best Practices"),
                "Azure" to listOf(                "Introduction to Azure", "Azure Services", "Azure Architecture", "Azure Security", "Azure Pricing", "Azure Management", "Azure Deployment", "Azure Monitoring", "Azure Compliance", "Azure Best Practices")
            )
        ),
        "Geography" to mapOf(
            "Physical Geography" to mapOf(
                "Mountains" to listOf("Types of Mountains", "Formation of Mountains", "Mountain Ranges", "Mountain Climates", "Mountain Ecosystems", "Mountain Hazards", "Mountain Tourism", "Mountain Conservation", "Mountain Geology", "Mountain Hydrology"),
                "Rivers" to listOf("River Systems", "River Erosion", "River Deposition", "River Landforms", "River Climates", "River Ecosystems", "River Hazards", "River Tourism", "River Conservation", "River Hydrology")
            ),
            "Human Geography" to mapOf(
                "Urbanization" to listOf("Causes of Urbanization", "Effects of Urbanization", "Urban Planning", "Urban Transportation", "Urban Housing", "Urban Economy", "Urban Environment", "Urban Culture", "Urban Health", "Urban Governance"),
                "Population" to listOf("Population Growth", "Population Distribution", "Population Density", "Population Migration", "Population Demographics", "Population Policies", "Population Health", "Population Education", "Population Employment", "Population Aging")
            )
        ),
        "Biology" to mapOf(
            "Botany" to mapOf(
                "Plant Cells" to listOf("Structure of Plant Cells", "Functions of Plant Cells", "Plant Cell Division", "Plant Cell Metabolism", "Plant Cell Communication", "Plant Cell Differentiation", "Plant Cell Cycle", "Plant Cell Apoptosis", "Plant Cell Genetics", "Plant Cell Biotechnology"),
                "Photosynthesis" to listOf("Process of Photosynthesis", "Factors Affecting Photosynthesis", "Photosynthetic Pigments", "Photosynthetic Pathways", "Photosynthetic Efficiency", "Photosynthetic Adaptations", "Photosynthetic Evolution", "Photosynthetic Regulation", "Photosynthetic Applications", "Photosynthetic Research")
            ),
            "Zoology" to mapOf(
                "Animal Cells" to listOf("Structure of Animal Cells", "Functions of Animal Cells", "Animal Cell Division", "Animal Cell Metabolism", "Animal Cell Communication", "Animal Cell Differentiation", "Animal Cell Cycle", "Animal Cell Apoptosis", "Animal Cell Genetics", "Animal Cell Biotechnology"),
                "Animal Behavior" to listOf("Types of Animal Behavior", "Factors Influencing Behavior", "Behavioral Ecology", "Behavioral Genetics", "Behavioral Evolution", "Behavioral Adaptations", "Behavioral Communication", "Behavioral Learning", "Behavioral Research", "Behavioral Applications")
            )
        ),
        "Chef" to mapOf(
            "Culinary Skills" to mapOf(
                "Cooking Techniques" to listOf("Boiling", "Grilling", "Roasting", "Baking", "Frying", "Steaming", "Poaching", "Braising", "Sautéing", "Blanching")
            ),
            "Recipes" to mapOf(
                "Appetizers" to listOf("Salads", "Soups", "Dips", "Finger Foods", "Canapés", "Bruschetta", "Stuffed Vegetables", "Spring Rolls", "Deviled Eggs", "Cheese Platters"),
                "Main Courses" to listOf("Pasta", "Steak", "Chicken", "Fish", "Vegetarian", "Vegan", "Gluten-Free", "Low-Carb", "Keto", "Paleo")
            )
        ),
        "Cars" to mapOf(
            "Mechanics" to mapOf(
                "Engine Basics" to listOf("Types of Engines", "Engine Components", "Engine Operation", "Engine Maintenance", "Engine Troubleshooting", "Engine Performance", "Engine Tuning", "Engine Rebuilding", "Engine Upgrades", "Engine Diagnostics"),
                "Transmission" to listOf("Types of Transmissions", "Transmission Components", "Transmission Operation", "Transmission Maintenance", "Transmission Troubleshooting", "Transmission Performance", "Transmission Tuning", "Transmission Rebuilding", "Transmission Upgrades", "Transmission Diagnostics")
            ),
            "Electronics" to mapOf(
                "Car Sensors" to listOf("Types of Sensors", "Functions of Sensors", "Sensor Operation", "Sensor Maintenance", "Sensor Troubleshooting", "Sensor Performance", "Sensor Upgrades", "Sensor Diagnostics", "Sensor Calibration", "Sensor Integration"),
                "ECU" to listOf("Functions of ECU", "ECU Programming", "ECU Operation", "ECU Maintenance", "ECU Troubleshooting", "ECU Performance", "ECU Tuning", "ECU Rebuilding", "ECU Upgrades", "ECU Diagnostics")
            )
        ),
        "Aircraft" to mapOf(
            "Aerodynamics" to mapOf(
                "Lift and Drag" to listOf("Principles of Lift", "Factors Affecting Drag", "Lift-to-Drag Ratio", "Aerodynamic Forces", "Airfoil Design", "Wing Configuration", "Boundary Layer", "Flow Separation", "Stall", "Aerodynamic Efficiency"),
                "Flight Mechanics" to listOf("Forces of Flight", "Flight Dynamics", "Stability and Control", "Aircraft Performance", "Flight Maneuvers", "Flight Instruments", "Flight Planning", "Flight Safety", "Flight Training", "Flight Operations")
            ),
            "Avionics" to mapOf(
                "Navigation Systems" to listOf("Types of Navigation Systems", "Functions of Navigation Systems", "Navigation System Operation", "Navigation System Maintenance", "Navigation System Troubleshooting", "Navigation System Performance", "Navigation System Upgrades", "Navigation System Diagnostics", "Navigation System Calibration", "Navigation System Integration"),
                "Communication Systems" to listOf("Types of Communication Systems", "Functions of Communication Systems", "Communication System Operation", "Communication System Maintenance", "Communication System Troubleshooting", "Communication System Performance", "Communication System Upgrades", "Communication System Diagnostics")
            )
        ),
        "Health" to mapOf(
            "Nutrition" to mapOf(
                "Macronutrients" to listOf("Carbohydrates", "Proteins", "Fats", "Fiber", "Water"),
                "Micronutrients" to listOf("Vitamins", "Minerals", "Antioxidants"),
                "Dietary Guidelines" to listOf("Balanced Diet", "Dietary Recommendations", "Food Pyramid", "Portion Control", "Healthy Eating Habits"),
                "Special Diets" to listOf("Vegetarian", "Vegan", "Gluten-Free", "Keto", "Paleo", "Mediterranean Diet")
            ),
            "Fitness" to mapOf(
                "Exercise Types" to listOf("Cardio", "Strength Training", "Flexibility", "Balance", "High-Intensity Interval Training (HIIT)", "Yoga", "Pilates"),
                "Workout Plans" to listOf("Beginner Workouts", "Intermediate Workouts", "Advanced Workouts", "Home Workouts", "Gym Workouts", "Sports-Specific Training"),
                "Fitness Tips" to listOf("Warm-Up and Cool-Down", "Proper Form", "Injury Prevention", "Recovery", "Motivation", "Consistency")
            ),
            "Mental Health" to mapOf(
                "Stress Management" to listOf("Relaxation Techniques", "Mindfulness", "Meditation", "Breathing Exercises", "Time Management", "Work-Life Balance"),
                "Mental Disorders" to listOf("Anxiety", "Depression", "Bipolar Disorder", "Schizophrenia", "PTSD", "OCD"),
                "Therapies" to listOf("Cognitive Behavioral Therapy (CBT)", "Psychotherapy", "Counseling", "Group Therapy", "Medication", "Self-Help Strategies")
            )
        ),
        "Lifestyle" to mapOf(
            "Personal Development" to mapOf(
                "Goal Setting" to listOf("SMART Goals", "Long-Term Goals", "Short-Term Goals", "Action Plans", "Tracking Progress"),
                "Time Management" to listOf("Prioritization", "Scheduling", "Productivity Techniques", "Avoiding Procrastination", "Work-Life Balance"),
                "Self-Care" to listOf("Physical Self-Care", "Emotional Self-Care", "Mental Self-Care", "Social Self-Care", "Spiritual Self-Care")
            ),
            "Home Improvement" to mapOf(
                "Interior Design" to listOf("Room Layouts", "Color Schemes", "Furniture Arrangement", "Decorating Tips", "Lighting"),
                "DIY Projects" to listOf("Home Repairs", "Furniture Building", "Crafts", "Gardening", "Upcycling"),
                "Organization" to listOf("Decluttering", "Storage Solutions", "Cleaning Tips", "Home Maintenance", "Efficient Living Spaces")
            ),
            "Travel" to mapOf(
                "Travel Planning" to listOf("Destination Research", "Itinerary Creation", "Budgeting", "Packing Tips", "Travel Insurance"),
                "Travel Tips" to listOf("Safety", "Cultural Etiquette", "Language Barriers", "Local Cuisine", "Sustainable Travel"),
                "Travel Experiences" to listOf("Adventure Travel", "Cultural Travel", "Relaxation Travel", "Solo Travel", "Group Travel")
            )
        ),
        "Social Media" to mapOf(
            "Platforms" to mapOf(
                "Facebook" to listOf("Creating a Profile", "Privacy Settings", "Posting Content", "Engaging with Friends", "Groups and Pages"),
                "Instagram" to listOf("Creating a Profile", "Posting Photos and Videos", "Stories", "Hashtags", "Engagement Strategies"),
                "Twitter" to listOf("Creating a Profile", "Tweeting", "Retweeting", "Hashtags", "Engagement Strategies"),
                "LinkedIn" to listOf("Creating a Profile", "Networking", "Job Searching", "Posting Content", "Engagement Strategies")
            ),
            "Content Creation" to mapOf(
                "Visual Content" to listOf("Photography", "Videography", "Graphic Design", "Editing Tools", "Content Planning"),
                "Written Content" to listOf("Blogging", "Copywriting", "SEO", "Content Strategy", "Engagement Techniques"),
                "Influencer Marketing" to listOf("Building a Personal Brand", "Collaborations", "Sponsorships", "Audience Engagement", "Monetization")
            ),
            "Analytics" to mapOf(
                "Metrics" to listOf("Reach", "Engagement", "Impressions", "Followers", "Conversions"),
                "Tools" to listOf("Google Analytics", "Facebook Insights", "Instagram Insights", "Twitter Analytics", "LinkedIn Analytics"),
                "Strategies" to listOf("Data-Driven Decisions", "Performance Tracking", "A/B Testing", "Reporting", "Optimization")
            )
        ),
        "Marketing" to mapOf(
            "Digital Marketing" to mapOf(
                "SEO" to listOf("On-Page SEO", "Off-Page SEO", "Technical SEO", "Keyword Research", "Link Building"),
                "Content Marketing" to listOf("Content Creation", "Content Distribution", "Content Strategy", "Blogging", "Video Marketing"),
                "Social Media Marketing" to listOf("Platform Strategies", "Content Planning", "Engagement Techniques", "Advertising", "Analytics")
            ),
            "Traditional Marketing" to mapOf(
                "Advertising" to listOf("Print Ads", "TV Ads", "Radio Ads", "Billboards", "Direct Mail"),
                "Public Relations" to listOf("Press Releases", "Media Relations", "Event Planning", "Crisis Management", "Reputation Management"),
                "Market Research" to listOf("Surveys", "Focus Groups", "Data Analysis", "Consumer Behavior", "Competitive Analysis")
            ),
            "Branding" to mapOf(
                "Brand Identity" to listOf("Logo Design", "Brand Colors", "Typography", "Brand Voice", "Brand Guidelines"),
                "Brand Strategy" to listOf("Positioning", "Messaging", "Target Audience", "Competitive Analysis", "Brand Equity"),
                "Brand Management" to listOf("Consistency", "Rebranding", "Brand Loyalty", "Brand Advocacy", "Brand Monitoring")
            )
        ),
        "Business" to mapOf(
            "Entrepreneurship" to mapOf(
                "Business Ideas" to listOf("Identifying Opportunities", "Market Research", "Business Models", "Validation", "Prototyping"),
                "Startup" to listOf("Business Plan", "Funding", "Legal Structure", "Product Development", "Go-to-Market Strategy"),
                "Scaling" to listOf("Growth Strategies", "Operations Management", "Team Building", "Customer Acquisition", "Financial Management")
            ),
            "Management" to mapOf(
                "Leadership" to listOf("Leadership Styles", "Decision Making", "Team Motivation", "Conflict Resolution", "Performance Management"),
                "Project Management" to listOf("Project Planning", "Resource Allocation", "Risk Management", "Agile Methodologies", "Project Tracking"),
                "Operations" to listOf("Process Improvement", "Supply Chain Management", "Quality Control", "Inventory Management", "Logistics")
            ),
            "Finance" to mapOf(
                "Accounting" to listOf("Financial Statements", "Bookkeeping", "Taxation", "Auditing", "Budgeting"),
                "Investment" to listOf("Stock Market", "Bonds", "Mutual Funds", "Real Estate", "Cryptocurrency"),
                "Personal Finance" to listOf("Budgeting", "Saving", "Investing", "Debt Management", "Retirement Planning")
            )
        ),
        "Day-to-Day Tips and Tricks" to mapOf(
            "Productivity" to mapOf(
                "Time Management" to listOf("Prioritization", "Scheduling", "Task Management", "Avoiding Procrastination", "Productivity Tools"),
                "Organization" to listOf("Decluttering", "Storage Solutions", "Efficient Workspaces", "Digital Organization", "Routine Building"),
                "Efficiency" to listOf("Automation", "Delegation", "Focus Techniques", "Energy Management", "Work-Life Balance")
            ),
            "Life Hacks" to mapOf(
                "Home" to listOf("Cleaning Tips", "DIY Repairs", "Organization Hacks", "Cooking Shortcuts", "Gardening Tips"),
                "Technology" to listOf("Device Optimization", "App Recommendations", "Tech Troubleshooting", "Online Security", "Digital Wellbeing"),
                "Travel" to listOf("Packing Tips", "Travel Deals", "Safety Tips", "Local Experiences", "Travel Apps")
            ),
            "Self-Improvement" to mapOf(
                "Habits" to listOf("Habit Formation", "Breaking Bad Habits", "Consistency", "Tracking Progress", "Motivation"),
                "Mindfulness" to listOf("Meditation", "Breathing Exercises", "Gratitude Practices", "Stress Reduction", "Mindful Living"),
                "Learning" to listOf("Continuous Learning", "Skill Development", "Reading Strategies", "Online Courses", "Learning Techniques")
            )
        ),
        "Web Development" to mapOf(
            "Frontend Development" to mapOf(
                "HTML" to listOf("Introduction to HTML", "HTML Elements", "Forms and Inputs", "Semantic HTML", "Accessibility"),
                "CSS" to listOf("Introduction to CSS", "Selectors and Properties", "Layouts", "Responsive Design", "CSS Frameworks"),
                "JavaScript" to listOf("Introduction to JavaScript", "DOM Manipulation", "Events", "ES6+", "JavaScript Frameworks")
            ),
            "Backend Development" to mapOf(
                "Node.js" to listOf("Introduction to Node.js", "Modules", "Express.js", "Database Integration", "Authentication"),
                "Python" to listOf("Flask", "Django", "Database Integration", "REST APIs", "Authentication"),
                "PHP" to listOf("Introduction to PHP", "Laravel", "Database Integration", "REST APIs", "Authentication")
            ),
            "Full Stack Development" to mapOf(
                "MERN Stack" to listOf("MongoDB", "Express.js", "React", "Node.js"),
                "MEAN Stack" to listOf("MongoDB", "Express.js", "Angular", "Node.js"),
                "LAMP Stack" to listOf("Linux", "Apache", "MySQL", "PHP")
            )
        ),
        "App Development" to mapOf(
            "Android Development" to mapOf(
                "Java" to listOf("Introduction to Java", "Android Studio", "UI Components", "Activities and Intents", "Data Storage"),
                "Kotlin" to listOf("Introduction to Kotlin", "Android Studio", "UI Components", "Activities and Intents", "Data Storage")
            ),
            "iOS Development" to mapOf(
                "Swift" to listOf("Introduction to Swift", "Xcode", "UI Components", "View Controllers", "Data Storage"),
                "Objective-C" to listOf("Introduction to Objective-C", "Xcode", "UI Components", "View Controllers", "Data Storage")
            ),
            "Cross-Platform Development" to mapOf(
                "Flutter" to listOf("Introduction to Flutter", "Dart", "Widgets", "State Management", "API Integration"),
                "React Native" to listOf("Introduction to React Native", "Components", "Navigation", "State Management", "API Integration"),
                "Xamarin" to listOf("Introduction to Xamarin", "C#", "UI Components", "Navigation", "API Integration")
            )
        ),
        "Game Development" to mapOf(
            "Game Design" to mapOf(
                "Concept Development" to listOf("Game Ideas", "Storyboarding", "Character Design", "Level Design", "Game Mechanics"),
                "Prototyping" to listOf("Paper Prototyping", "Digital Prototyping", "Playtesting", "Iteration", "Feedback")
            ),
            "Game Programming" to mapOf(
                "Unity" to listOf("Introduction to Unity", "C# Programming", "Physics", "Animation", "Scripting"),
                "Unreal Engine" to listOf("Introduction to Unreal Engine", "Blueprints", "C++ Programming", "Physics", "Animation"),
                "Godot" to listOf("Introduction to Godot", "GDScript", "Physics", "Animation", "Scripting")
            ),
            "Game Art" to mapOf(
                "2D Art" to listOf("Pixel Art", "Vector Art", "Digital Painting", "Animation", "UI Design"),
                "3D Art" to listOf("Modeling", "Texturing", "Rigging", "Animation", "Rendering")
            )
        )
    )

    // Register the file picker launcher
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                processSelectedFile(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // Call super first!

        // Enable edge-to-edge design for seamless blended appearance
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }

        // Make status bar transparent for blended design
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
        }

        // Hide action bar for seamless blended design
        supportActionBar?.hide()

        // Initialize Data Binding
        binding = ActivityMainBinding.inflate(layoutInflater) // Replace YourLayoutBinding
        setContentView(binding.root)
        
        // Initialize Theme Manager
        themeManager = ThemeManager(this)
        
        // Action bar is hidden for blended design

        // Badge elements removed for cleaner design
        val splashScreen = installSplashScreen()
        //checkForAppUpdate()


        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Initialize sharedPreferences
        sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        setContentView(R.layout.activity_main)
        createNotificationChannel()
        checkNotificationPermission() // Check and request notification permissions
        scheduleReminder()

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(prefsName, MODE_PRIVATE)

        // Obtain or create a unique user ID for syncing with the web app
        userId = getOrCreateUserId()

        // Load the last interaction time from SharedPreferences
        lastInteractionTime = sharedPreferences.getLong(lastInteractionTimeKey, 0)

        // Update last interaction time when the app is opened
        updateLastInteractionTime()

        // Schedule the reminder
        scheduleReminder()
        enhanceBuyButton()

        // Initialize the ScrollView
        scrollView = findViewById(R.id.mainScrollView)

        createNotificationChannel()
        scheduleReminder()

        supportActionBar?.setDisplayShowCustomEnabled(true)
        supportActionBar?.setCustomView(R.layout.custom_action_bar)


        // Set up the UI with subjects
        setupSubjects()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        subscriptionUIManager = SubscriptionUIManager(this)
        
        // CRITICAL: Clear legacy subscription data to prevent conflicts with Firestore
        forceClearAllLegacySubscriptionData()
        
        // Initialize Firebase first (critical after app reinstall)
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
            Log.d("MainActivity", "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Firebase initialization failed", e)
            Toast.makeText(this, "Firebase initialization failed. Please restart the app.", Toast.LENGTH_LONG).show()
        }
        
        // Initialize Unified Backend Services
        unifiedDataManager = UnifiedDataManager.getInstance(this)
        authenticationService = AuthenticationService(this)
        firebaseAuthService = FirebaseAuthenticationService(this)
        
        // Verify Firebase Auth is working
        try {
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            Log.d("MainActivity", "Firebase Auth status: currentUser=${currentUser?.uid}, email=${currentUser?.email}")
        } catch (e: Exception) {
            Log.e("MainActivity", "Firebase Auth verification failed", e)
        }
        
        // Initialize Billing Sync Service
        billingSync = GooglePlayBillingSync(this)
        
        // FORCE SYNC: Force sync with updated product IDs to fix subscription detection
        lifecycleScope.launch {
            try {
                Log.d("MainActivity", "Forcing subscription sync with updated product IDs...")
                val syncResult = billingSync.forceSyncWithUpdatedProductIds()
                Log.d("MainActivity", "Force sync result: $syncResult")
                
                // Wait a moment for Firestore to update
                kotlinx.coroutines.delay(2000)
                
                // Update UI with fresh data
                //updateBadgeAndText() // Badge functionality disabled - using stub
            } catch (e: Exception) {
                Log.e("MainActivity", "Error during force sync", e)
            }
        }
        
        // Initialize billing sync manager for background sync
        com.playstudio.aiteacher.billing.BillingSyncManager.initialize(this)
        
        // Setup Google Sign-In launcher
        setupGoogleSignInLauncher()

        // Action bar is hidden for seamless blended design
        // All navigation is now handled through the blended header
        binding.buttonNew2.setOnClickListener {
            // Voice Chat - Launch Chat with live voice mode enabled
            if (checkAndRequestPermissions()) {
                startVoiceChatSessionGeneral()
            }
        }





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

            binding.cardHomeworkHelper.setOnClickListener {
                // Homework Helper - Extract text from docs, images, and PDFs
                Log.d("ButtonTest", "Homework Helper clicked")
                if (checkAndRequestPermissions()) {
                    startHomeworkHelperSession()
                }
            }
            binding.buttonNew3.setOnClickListener {
                // AI Image Generator - Direct access to GPT Image 1
                startAdvancedImageGeneration()
            }

            // Add secret tap detection for top-left corner
            window.decorView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    checkSecretTap(event.rawX, event.rawY)
                }
                false // Don't consume the event
            }

            // Update your email button click listener
            // Update your email button click listener
            binding.btnExtractEmail.setOnClickListener {
                // Email Assistant - Smart email composition and analysis
                startEmailAssistantSession()
            }

            // AI Tool Cards Click Handlers
            binding.cardAnalyzeImage.setOnClickListener {
                // Analyze Image tool - Launch image analysis directly
                startImageAnalysisSession()
            }

            binding.cardScienceTool.setOnClickListener {
                // Science tool - Show academic hierarchy starting with Science subjects
                showAcademicHierarchy("Physics") // or could start with all science subjects
            }

            // Category Tab Click Handlers
            binding.tabAllTools.setOnClickListener {
                selectCategoryTab("all_tools")
            }

            binding.tabCreative.setOnClickListener {
                selectCategoryTab("creative")
            }

            binding.tabAcademic.setOnClickListener {
                selectCategoryTab("academic")
            }

            binding.tabProductivity.setOnClickListener {
                selectCategoryTab("productivity")
            }

            // Navigation Menu Click Handlers
            binding.hamburgerMenu.setOnClickListener {
                showHamburgerMenu()
            }

            binding.profileIcon.setOnClickListener {
                openProfileActivity()
            }

            // Floating Action Button - Quick Actions
            binding.floatingActionButton.setOnClickListener {
                showQuickActionsMenu()
            }

            // Set up other UI elements and listeners...
            setupUI()

            // Check subscription status and update ViewModel
            //checkSubscriptionStatus()

            // Update badge and text based on the current subscription
            //updateBadgeAndText() // Badge functionality disabled - using stub

            // Check if the welcome message has been shown
            if (!isWelcomeMessageShown()) {
                setWelcomeMessageShown(true)
            }

            // Show the "Thank You for Downloading" dialog if it hasn't been shown before
            if (!isThankYouDialogShown()) {
                showThankYouDialog()
                setThankYouDialogShown(true)
            }
            // Badge elements removed for cleaner design


            // Show the subscription dialog if it hasn't been shown before
            if (!isSubscriptionDialogShown()) {
                showSubscriptionDialog()
                setSubscriptionDialogShown(true)
            }
        }, 3000) // 3000 milliseconds = 3 seconds delay

        // Listen for fragment changes
        supportFragmentManager.addOnBackStackChangedListener {
            handleFragmentChanges()

            //emailProviderHelper = EmailProviderHelper(this)
        }
        
        // Set initial UI state
        handleFragmentChanges()

        // Handle intent action for subscription purchase
        handleIntentAction(intent)
        
        // Check for existing subscription on startup
        checkExistingSubscription()

        // Set default selected tab to "All Tools" when app starts
        selectCategoryTab("all_tools")
    }

    private fun showDalle3SubscriptionRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("Premium Feature Required")
            .setMessage("DALL-E 3 image generation requires a premium subscription. Subscribe now to unlock this feature!")
            .setPositiveButton("Subscribe") { dialog: DialogInterface, which: Int ->
                // Check authentication before showing subscription options
                if (!firebaseAuthService.isSignedIn()) {
                    Log.w("MainActivity", "User not authenticated, showing authentication required dialog")
                    showAuthenticationRequiredDialog()
                } else {
                    showSubscriptionOptions()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun startSpeechToText() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                SPEECH_REQUEST_CODE)
        } else {
            launchSpeechRecognizer()
        }
    }






    // Make sure your timer is properly cleared when not needed
    override fun onPause() {
        super.onPause()
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
    }

    private fun launchSpeechRecognizer() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
            // You can set language if needed
            // putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this,
                "Speech recognition not supported on this device",
                Toast.LENGTH_SHORT).show()
        }
    }


    private fun passRecognizedTextToChatFragment(text: String) {
        val chatFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? ChatFragment
        if (chatFragment != null) {
            // If ChatFragment is already visible
            chatFragment.setRecognizedText(text)
        } else {
            // If ChatFragment is not visible, create a new instance and pass the text
            val newChatFragment = ChatFragment().apply {
                arguments = Bundle().apply {
                    putString("recognized_text", text)
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, newChatFragment)
                .addToBackStack(null)
                .commit()
            
            // Update UI visibility
            handleFragmentChanges()
        }
    }







    private fun checkAndRequestPermissions(): Boolean {
        val requiredPermissions = mutableListOf<String>().apply {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.READ_MEDIA_IMAGES) // Android 13+
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        return if (requiredPermissions.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }) {
            ActivityCompat.requestPermissions(
                this,
                requiredPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
            false
        } else {
            true
        }
    }

    private fun getEmojiForSubject(subject: String): String {
        return when {
            subject.contains("Math", ignoreCase = true) -> "🧮"
            subject.contains("Science", ignoreCase = true) -> "🔬"
            subject.contains("Physics", ignoreCase = true) -> "⚛️"
            subject.contains("Chemistry", ignoreCase = true) -> "🧪"
            subject.contains("Biology", ignoreCase = true) -> "🧬"
            subject.contains("Tech", ignoreCase = true) -> "💻"
            subject.contains("Computer", ignoreCase = true) -> "🖥️"
            subject.contains("Code", ignoreCase = true) -> "👨‍💻"
            subject.contains("Health", ignoreCase = true) -> "🏥"
            subject.contains("Business", ignoreCase = true) -> "💼"
            subject.contains("Marketing", ignoreCase = true) -> "📈"
            subject.contains("Social", ignoreCase = true) -> "📱"
            subject.contains("Geo", ignoreCase = true) -> "🌍"
            subject.contains("Cars", ignoreCase = true) -> "🚗"
            subject.contains("Aircraft", ignoreCase = true) -> "✈️"
            subject.contains("Chef", ignoreCase = true) -> "👨‍🍳"
            subject.contains("Game", ignoreCase = true) -> "🎮"
            subject.contains("Web", ignoreCase = true) -> "🌐"
            subject.contains("App", ignoreCase = true) -> "📲"
            subject.contains("Day-to-Day", ignoreCase = true) -> "📅"
            subject.contains("Lifestyle", ignoreCase = true) -> "🏡"
            else -> "📚"
        }
    }

    private fun setupSubjects() {
        // Hide other containers first
        findViewById<LinearLayout>(R.id.chaptersLayout).visibility = View.GONE
        findViewById<LinearLayout>(R.id.topicsLayout).visibility = View.GONE
        findViewById<LinearLayout>(R.id.subtopicsLayout).visibility = View.GONE

        // Show subjects container - removed, functionality moved to Academic button
        // findViewById<HorizontalScrollView>(R.id.subjectsScrollView).visibility = View.VISIBLE

        // Subjects layout removed - functionality moved to Academic button
        // val subjectsLayout = findViewById<LinearLayout>(R.id.subjectsLayout)
        // subjectsLayout.removeAllViews()

        // Create subject cards - COMMENTED OUT: subjectsLayout removed from layout
        /*
        subjects.forEach { (subject, chapters) ->
            val subjectCard = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    148.dpToPx(), // Optimal width
                    160.dpToPx() // Fixed height
                ).apply {
                    setMargins(8.dpToPx(), 0, 8.dpToPx(), 0)
                }
                radius = 16.dpToPx().toFloat()
                elevation = 4.dpToPx().toFloat()
                strokeColor = ContextCompat.getColor(this@MainActivity, R.color.shining_navy_light)
                strokeWidth = 1.dpToPx()
                setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.card_surface))
                rippleColor = ColorStateList.valueOf(
                    ContextCompat.getColor(this@MainActivity, R.color.shining_navy_light).withAlpha(30)
                )

                val cardContent = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    ).apply {
                        setPadding(12.dpToPx(), 16.dpToPx(), 12.dpToPx(), 16.dpToPx())
                    }
                }

                // Subject icon (emoji)
                val subjectIcon = TextView(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        40.dpToPx(),
                        40.dpToPx()
                    ).apply {
                        gravity = Gravity.CENTER
                        bottomMargin = 12.dpToPx()
                    }
                    text = getEmojiForSubject(subject)
                    textSize = 24f
                    gravity = Gravity.CENTER
                }
                cardContent.addView(subjectIcon)

                // Subject name
                val subjectName = TextView(this@MainActivity).apply {
                    text = subject
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    typeface = ResourcesCompat.getFont(context, R.font.montserrat_medium)
                    gravity = Gravity.CENTER
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
                cardContent.addView(subjectName)

                // Click animation and action
                setOnClickListener {
                    val scaleDown = ObjectAnimator.ofPropertyValuesHolder(
                        this,
                        PropertyValuesHolder.ofFloat("scaleX", 0.95f),
                        PropertyValuesHolder.ofFloat("scaleY", 0.95f)
                    ).apply {
                        duration = 100
                        interpolator = AccelerateDecelerateInterpolator()
                    }

                    val scaleUp = ObjectAnimator.ofPropertyValuesHolder(
                        this,
                        PropertyValuesHolder.ofFloat("scaleX", 1f),
                        PropertyValuesHolder.ofFloat("scaleY", 1f)
                    ).apply {
                        duration = 100
                        interpolator = OvershootInterpolator(1.5f)
                    }

                    scaleDown.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            scaleUp.start()
                            Handler(Looper.getMainLooper()).postDelayed({
                                setupChapters(subject, chapters)
                            }, 150)
                        }
                    })
                    scaleDown.start()
                }

                addView(cardContent)
            }

            // subjectsLayout.addView(subjectCard) // Commented out - subjectsLayout removed from layout
        }
        */
    }


    private fun setupChapters(subject: String, chapters: Map<String, Map<String, List<String>>>) {
        // Hide subjects and other containers
        // findViewById<HorizontalScrollView>(R.id.subjectsScrollView).visibility = View.GONE // Commented out - subjectsScrollView removed from layout
        findViewById<LinearLayout>(R.id.topicsLayout).visibility = View.GONE
        findViewById<LinearLayout>(R.id.subtopicsLayout).visibility = View.GONE

        // Show chapters container
        val chaptersLayout = findViewById<LinearLayout>(R.id.chaptersLayout)
        chaptersLayout.visibility = View.VISIBLE
        chaptersLayout.removeAllViews()

        // Add back button
        val backButton = MaterialButton(this).apply {
            text = "← Back to Subjects"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.primaryText))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.secondaryColor))
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_arrow_back)
            iconGravity = MaterialButton.ICON_GRAVITY_START
            cornerRadius = 8.dpToPx()
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 16.dpToPx()
            layoutParams = params
            setOnClickListener {
                setupSubjects()
            }
        }
        chaptersLayout.addView(backButton)

        // Add title
        val title = TextView(this).apply {
            text = "$subject - Select Chapter"
            textSize = 20f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.primaryText))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dpToPx()
            }
        }
        chaptersLayout.addView(title)

        // Create chapter buttons
        chapters.forEach { (chapter, topics) ->
            val chapterButton = MaterialButton(this).apply {
                text = chapter
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.primaryText))
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.primaryColor))
                cornerRadius = 8.dpToPx()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8.dpToPx(), 0, 8.dpToPx())
                }
                setOnClickListener {
                    // Add ripple effect
                    val anim = ObjectAnimator.ofFloat(this, "scaleX", 1f, 0.95f, 1f)
                    anim.duration = 200
                    anim.start()

                    Handler(Looper.getMainLooper()).postDelayed({
                        setupTopics(subject, chapter, topics)
                    }, 200)
                }
            }
            chaptersLayout.addView(chapterButton)
        }

        // Scroll to the top of the chapters section
        binding.mainScrollView.post {
            binding.mainScrollView.smoothScrollTo(0, chaptersLayout.top)
        }
    }
    private fun setupTopics(subject: String, chapter: String, topics: Map<String, List<String>>) {
        // Hide other containers
        findViewById<LinearLayout>(R.id.chaptersLayout).visibility = View.GONE
        findViewById<LinearLayout>(R.id.subtopicsLayout).visibility = View.GONE

        // Show topics container
        val topicsLayout = findViewById<LinearLayout>(R.id.topicsLayout)
        topicsLayout.visibility = View.VISIBLE
        topicsLayout.removeAllViews()

        // Add back button
        val backButton = MaterialButton(this).apply {
            text = "← Back to Chapters"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.primaryText))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.secondaryColor))
            iconGravity = MaterialButton.ICON_GRAVITY_START
            cornerRadius = 8.dpToPx()
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 16.dpToPx()
            layoutParams = params
            setOnClickListener {
                val chapters = subjects[subject] ?: emptyMap()
                setupChapters(subject, chapters)
            }
        }
        topicsLayout.addView(backButton)

        // Add title
        val title = TextView(this).apply {
            text = "$subject - $chapter - Select Topic"
            textSize = 20f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.primaryText))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dpToPx()
            }
        }
        topicsLayout.addView(title)

        // Create topic buttons
        topics.forEach { (topic, subtopics) ->
            val topicButton = MaterialButton(this).apply {
                text = topic
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.primaryText))
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.primaryColor))
                cornerRadius = 8.dpToPx()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8.dpToPx(), 0, 8.dpToPx())
                }
                setOnClickListener {
                    // Add ripple effect
                    val anim = ObjectAnimator.ofFloat(this, "scaleX", 1f, 0.95f, 1f)
                    anim.duration = 200
                    anim.start()

                    Handler(Looper.getMainLooper()).postDelayed({
                        setupSubtopics(subject, chapter, topic, subtopics)
                    }, 200)
                }
            }
            topicsLayout.addView(topicButton)
        }

        // Scroll to the top of the topics section
        binding.mainScrollView.post {
            binding.mainScrollView.smoothScrollTo(0, topicsLayout.top)
        }
    }
    private fun setupSubtopics(subject: String, chapter: String, topic: String, subtopics: List<String>) {
        // Hide other containers
        findViewById<LinearLayout>(R.id.chaptersLayout).visibility = View.GONE
        findViewById<LinearLayout>(R.id.topicsLayout).visibility = View.GONE

        // Show subtopics container
        val subtopicsLayout = findViewById<LinearLayout>(R.id.subtopicsLayout)
        subtopicsLayout.visibility = View.VISIBLE

        // Clear previous views but keep the GridLayout
        subtopicsLayout.removeAllViews()

        // Add back button
        val backButton = MaterialButton(this).apply {
            text = "← Back to Topics"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.primaryText))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.secondaryColor))
            iconGravity = MaterialButton.ICON_GRAVITY_START
            cornerRadius = 8.dpToPx()
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 16.dpToPx()
            layoutParams = params
            setOnClickListener {
                val chapters = subjects[subject] ?: emptyMap()
                val topics = chapters[chapter] ?: emptyMap()
                setupTopics(subject, chapter, topics)
            }
        }
        subtopicsLayout.addView(backButton)

        // Add title
        val title = TextView(this).apply {
            text = "$subject - $chapter - $topic"
            textSize = 20f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.primaryText))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dpToPx()
            }
        }
        subtopicsLayout.addView(title)

        // Create the GridLayout
        val gridLayout = GridLayout(this).apply {
            id = R.id.subtopicsGrid
            columnCount = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        subtopics.forEach { subtopic ->
            val card = MaterialCardView(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
                }
                radius = 12.dpToPx().toFloat()
                elevation = 4.dpToPx().toFloat()
                strokeColor = ContextCompat.getColor(this@MainActivity, R.color.card_stroke)
                strokeWidth = 1.dpToPx()
                setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.card_background))

                // Add content to the card
                val cardContent = TextView(this@MainActivity).apply {
                    text = subtopic
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.primaryText))
                    textSize = 14f
                    typeface = ResourcesCompat.getFont(context, R.font.montserrat_medium)
                    gravity = Gravity.CENTER
                    setPadding(16.dpToPx(), 24.dpToPx(), 16.dpToPx(), 24.dpToPx())
                }
                addView(cardContent)

                // Set click listener with proper animation
                setOnClickListener { view ->
                    view.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .withEndAction {
                            view.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(200)
                                .setInterpolator(OvershootInterpolator(1.5f))
                                .start()

                            // Open chat with subtopic query
                            val userQuery = "$subject - $chapter - $topic: $subtopic"
                            val response = generateResponse(userQuery)
                            openChatActivityWithMessage(response)
                        }
                        .start()
                }
            }
            gridLayout.addView(card)
        }

        subtopicsLayout.addView(gridLayout)

        // Scroll to the subtopics section
        binding.mainScrollView.post {
            binding.mainScrollView.smoothScrollTo(0, subtopicsLayout.top)
        }
    }
    // Extension function to convert dp to pixels
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
    // Add this extension function to your code
    fun Int.withAlpha(alpha: Int): Int {
        return Color.argb(
            alpha,
            Color.red(this),
            Color.green(this),
            Color.blue(this)
        )
    }






    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*" // All file types
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "image/*",
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ))
        }
        filePickerLauncher.launch(intent)
    }


    private fun passExtractedTextToChatFragment(extractedText: String) {
        // Check if ChatFragment is currently visible
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

        if (currentFragment is ChatFragment) {
            currentFragment.setExtractedText(extractedText)
        } else {
            // Create new ChatFragment instance and pass the text
            val chatFragment = ChatFragment().apply {
                arguments = Bundle().apply {
                    putString("extracted_text", extractedText)
                }
            }

            // Replace current fragment with ChatFragment
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, chatFragment)
                .addToBackStack(null)
                .commit()
        }
    }





    private fun processSelectedFile(fileUri: Uri) {
        val mimeType = contentResolver.getType(fileUri)

        val callback = object : FileUtils.TextExtractionCallback {
            override fun onTextExtracted(extractedText: String) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Text extracted successfully!",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Directly pass to ChatFragment if it exists
                    val chatFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? ChatFragment
                    chatFragment?.setExtractedText(extractedText) ?: run {
                        // If no ChatFragment, open ChatActivity with the text
                        openChatActivityWithMessage(extractedText)
                    }
                }
            }

            override fun onError(errorMessage: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }

        when {
            mimeType?.startsWith("image/") == true -> {
                FileUtils.extractTextFromImage(this, fileUri, callback)
            }
            mimeType in setOf(
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ) -> {
                FileUtils.extractTextFromDocument(this, fileUri, callback)
            }
            else -> {
                Toast.makeText(this, "Unsupported file type", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generateResponse(userQuery: String): String {
        val baseResponse = "Generate explanation for this query: $userQuery"
        val needsDiagram = userQuery.contains("diagram", ignoreCase = true) ||
                userQuery.contains("sketch", ignoreCase = true) ||
                userQuery.contains("draw", ignoreCase = true) ||
                userQuery.contains("looks like", ignoreCase = true)

        return if (needsDiagram) {
            val searchQuery = userQuery.replace(" ", "+")
            val searchUrl = "https://www.google.com/search?q=$searchQuery+diagram"
            "$baseResponse\nFor a visual explanation, please visit: $searchUrl"
        } else {
            baseResponse
        }
    }


    private fun handleIntentAction(intent: Intent?) {
        // Handle subscription dialog intent extra
        if (intent?.getBooleanExtra("show_subscription_dialog", false) == true) {
            Log.d("MainActivity", "Showing subscription dialog after successful authentication")
            // Use retry logic to ensure Firebase auth state is ready
            showSubscriptionOptionsWithRetry()
            return
        }
        
        intent?.getStringExtra("action")?.let { action ->
            if (action == "buy_subscription") {
                // Check authentication before showing subscription options
                if (!firebaseAuthService.isSignedIn()) {
                    Log.w("MainActivity", "User not authenticated, showing authentication required dialog")
                    showAuthenticationRequiredDialog()
                } else {
                    showSubscriptionOptions()
                }
            }
        }
    }

    private fun showAuthenticationRequiredDialog() {
        val authDialog = AlertDialog.Builder(this, R.style.BlueDialogTheme)
            .setTitle("Account Required")
            .setMessage("You need to create an account before purchasing a subscription. This helps us secure your subscription and sync it across devices.")
            .setPositiveButton("Create Account") { dialog: DialogInterface, which: Int ->
                // Navigate to profile/login screen
                val intent = Intent(this, ProfileActivity::class.java)
                intent.putExtra("show_registration", true)
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog: DialogInterface, which: Int ->
                // Stay in current activity
            }
            .create()
        
        authDialog.show()
    }

    // Function to show the "Thank You for Downloading" dialog
    private fun showThankYouDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_thank_you, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val lottieAnimationView =
            dialogView.findViewById<LottieAnimationView>(R.id.lottieAnimationView)
        lottieAnimationView.setAnimation(R.raw.thank_you_animation) // Ensure you have this JSON animation in res/raw
        lottieAnimationView.playAnimation()

        val btnRateNow = dialogView.findViewById<Button>(R.id.btnRateNow)
        val btnRateLater = dialogView.findViewById<Button>(R.id.btnRateLater)
        val btnClose = dialogView.findViewById<Button>(R.id.btnClose)

        btnRateNow.setOnClickListener {
            // Open the app's rating page
            val appPackageName = packageName
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=$appPackageName")
                    )
                )
            } catch (e: ActivityNotFoundException) {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")
                    )
                )
            }
            dialog.dismiss()
        }

        btnRateLater.setOnClickListener {

            // Just close the dialog
            dialog.dismiss()
        }

        btnClose.setOnClickListener {
            // Just close the dialog
            dialog.dismiss()
        }

        dialog.show()
    }


    // Function to check if the "Thank You" dialog has been shown
    private fun isThankYouDialogShown(): Boolean {
        return sharedPreferences.getBoolean(thankYouDialogShownKey, false)
    }

    // Function to mark the "Thank You" dialog as shown
    private fun setThankYouDialogShown(shown: Boolean) {
        sharedPreferences.edit().putBoolean(thankYouDialogShownKey, shown).apply()
    }

    // Function to check if the subscription dialog has been shown
    private fun isSubscriptionDialogShown(): Boolean {
        return sharedPreferences.getBoolean(subscriptionDialogShownKey, false)
    }

    // Function to mark the subscription dialog as shown
    private fun setSubscriptionDialogShown(shown: Boolean) {
        sharedPreferences.edit().putBoolean(subscriptionDialogShownKey, shown).apply()
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

    private fun openChatActivityWithMessage(message: String) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("suggested_message", message)
            putExtra("is_ad_free", sharedPreferences.getBoolean(keyAdFree, false))
            putExtra("expiration_time", sharedPreferences.getLong(expirationTimeKey, 0))
        }
        startActivity(intent)
        updateLastInteractionTime()
    }

    private fun setupUI() {
        // Apply current theme to UI elements
        applyCurrentTheme()
        
        // Set up the click listener for the notification icon
        binding.emailRecyclerView.post {
            val copyIcon: ImageView? = binding.emailRecyclerView.findViewById(R.id.copy_icon)
            copyIcon?.setOnClickListener {
                it.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_click))
                val messageTextView: TextView? =
                    binding.emailRecyclerView.findViewById(R.id.messageTextView)
                val message = messageTextView?.text.toString()

                // Copy the message to the custom clipboard
                CustomClipboard.copy(message)

                // Show a toast message
                showCustomToast("Message copied to custom clipboard")
            }
            binding.searchBar.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    openChatActivityWithModel(currentModel)
                    updateLastInteractionTime()
                    true
                } else {
                    false
                }
            }

        }



        // Set up click listeners for suggested questions
        setupSubjects()

        // Open ChatActivity on message input box touch
        binding.searchBar.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                openChatActivityWithModel(currentModel)
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
    }




    private fun isUserSubscribed(): Boolean {
        val currentTime = System.currentTimeMillis()
        return sharedPreferences.getBoolean(keyAdFree, false) &&
                currentTime < sharedPreferences.getLong(expirationTimeKey, 0)
    }


    private fun openChatActivityWithModel(model: String) {
        Intent(this, ChatActivity::class.java).apply {
            putExtra("selected_model", model)
            putExtra("is_ad_free", sharedPreferences.getBoolean(keyAdFree, false))
            putExtra("expiration_time", sharedPreferences.getLong(expirationTimeKey, 0))
        }.also { startActivity(it) }
    }

    private fun openChatActivityWithModel(model: String, initialMessage: String) {
        Intent(this, ChatActivity::class.java).apply {
            putExtra("selected_model", model)
            putExtra("initial_message", initialMessage)
            putExtra("is_ad_free", sharedPreferences.getBoolean(keyAdFree, false))
            putExtra("expiration_time", sharedPreferences.getLong(expirationTimeKey, 0))
        }.also { startActivity(it) }
    }









    private fun handleFragmentChanges() {
        // Get reference to AI robot icon in the action bar
        val actionBarIcon: ImageView? = supportActionBar?.customView?.findViewById(R.id.actionBarIcon)

        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

        if (fragment is ChatFragment) {
            findViewById<FrameLayout>(R.id.fragment_container).visibility = View.VISIBLE
            findViewById<ScrollView>(R.id.mainScrollView).visibility = View.GONE
            // Update subscription status based on user subscription
            val isAdFree = sharedPreferences.getBoolean(keyAdFree, false)
            val expirationTime = sharedPreferences.getLong(expirationTimeKey, 0)
            fragment.updateSubscriptionStatus(isAdFree, expirationTime)

            // Optional: Make robot icon more prominent in chat mode
            actionBarIcon?.alpha = 1.0f

        } else {
            findViewById<FrameLayout>(R.id.fragment_container).visibility = View.GONE
            findViewById<ScrollView>(R.id.mainScrollView).visibility = View.VISIBLE
            // Optional: Make robot icon slightly dimmed in home mode
            actionBarIcon?.alpha = 0.8f
        }
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.let {
            menuInflater.inflate(R.menu.main_menu, it)

            // Update profile menu item based on Firebase authentication status
            val profileMenuItem = it.findItem(R.id.action_profile)
            // Check Firebase auth status and update menu
            lifecycleScope.launch {
                try {
                    val isLoggedIn = firebaseAuthService.isSignedIn()
                    Log.d("MainActivity", "Menu update - Firebase auth status: $isLoggedIn")
                    runOnUiThread {
                        if (isLoggedIn) {
                            profileMenuItem?.title = "👤 Profile"
                        } else {
                            profileMenuItem?.title = "🔐 Login"
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error checking Firebase auth status for menu", e)
                    // Default to login option on error
                    runOnUiThread {
                        profileMenuItem?.title = "🔐 Login"
                    }
                }
            }

            // Only show promo code option if user isn't subscribed
            if (!sharedPreferences.getBoolean(keyAdFree, false)) {
                it.findItem(R.id.hidden_version_item)?.isVisible = true
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.hidden_version_item -> {
                handleVersionItemTap()
                true
            }
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
                showAIThemeSelectionDialog()
                true
            }
            R.id.action_profile -> {
                handleProfileMenuAction()
                true
            }
            R.id.action_switch_to_webapp -> {
                handleWebappSwitchAction()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Profile Methods
    private fun handleProfileMenuAction() {
        lifecycleScope.launch {
            try {
                // Use Firebase authentication instead of legacy authenticationService
                val isLoggedIn = firebaseAuthService.isSignedIn()
                Log.d("MainActivity", "Profile menu clicked - Firebase auth status: $isLoggedIn")
                
                if (isLoggedIn) {
                    // User is logged in with Firebase, navigate directly to ProfileActivity
                    Log.d("MainActivity", "User authenticated, navigating to ProfileActivity")
                    startActivity(Intent(this@MainActivity, ProfileActivity::class.java))
                } else {
                    // User is not logged in, show login dialog
                    Log.d("MainActivity", "User not authenticated, showing login dialog")
                    showLoginDialog()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error handling profile action", e)
                Toast.makeText(this@MainActivity, "Error accessing profile", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showProfileDialog() {
        lifecycleScope.launch {
            try {
                // Use Firebase authentication instead of legacy authenticationService
                val user = firebaseAuthService.getCurrentUser()
                if (user != null) {
                    val dialog = AlertDialog.Builder(this@MainActivity)
                        .setTitle("User Profile")
                        .setMessage("Email: ${user.email}\nName: ${user.fullName}\nUID: ${user.uid}")
                        .setPositiveButton("View Profile") { dialog: DialogInterface, which: Int ->
                            startActivity(Intent(this@MainActivity, ProfileActivity::class.java))
                        }
                        .setNeutralButton("Settings") { dialog: DialogInterface, which: Int ->
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        }
                        .setNegativeButton("Logout") { dialog: DialogInterface, which: Int ->
                            handleLogout()
                        }
                        .create()
                    dialog.show()
                } else {
                    Toast.makeText(this@MainActivity, "Error loading profile", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error showing profile dialog", e)
                Toast.makeText(this@MainActivity, "Error loading profile", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showLoginDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Login Required")
            .setMessage("Please log in to access your profile, chat history, and subscription features across devices.")
            .setPositiveButton("Email Login") { dialog: DialogInterface, which: Int ->
                showLoginForm()
            }
            .setNeutralButton("Google Sign-In") { dialog: DialogInterface, which: Int ->
                startGoogleSignIn()
            }
            .setNegativeButton("Register") { dialog: DialogInterface, which: Int ->
                showRegistrationForm()
            }
            .create()
        dialog.show()
    }
    
    private fun showLoginForm() {
        val dialogView = layoutInflater.inflate(android.R.layout.simple_list_item_2, null)
        val emailEditText = EditText(this).apply {
            hint = "Email"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val passwordEditText = EditText(this).apply {
            hint = "Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            addView(emailEditText)
            addView(passwordEditText)
        }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Login")
            .setView(layout)
            .setPositiveButton("Login") { dialog: DialogInterface, which: Int ->
                val email = emailEditText.text.toString().trim()
                val password = passwordEditText.text.toString().trim()
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    performLogin(email, password)
                } else {
                    Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }
    
    private fun showRegistrationForm() {
        val emailEditText = EditText(this).apply {
            hint = "Email"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val passwordEditText = EditText(this).apply {
            hint = "Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val confirmPasswordEditText = EditText(this).apply {
            hint = "Confirm Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            addView(emailEditText)
            addView(passwordEditText)
            addView(confirmPasswordEditText)
        }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Create Account")
            .setView(layout)
            .setPositiveButton("Register") { dialog: DialogInterface, which: Int ->
                val email = emailEditText.text.toString().trim()
                val password = passwordEditText.text.toString().trim()
                val confirmPassword = confirmPasswordEditText.text.toString().trim()
                
                when {
                    email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() -> {
                        Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                    }
                    password != confirmPassword -> {
                        Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                    }
                    password.length < 6 -> {
                        Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        performRegistration(email, password)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }
    
    private fun performLogin(email: String, password: String) {
        lifecycleScope.launch {
            try {
                // Use Firebase authentication instead of legacy authenticationService
                val result = firebaseAuthService.signInWithFirebase(email, password)
                if (result.success) {
                    Toast.makeText(this@MainActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                    // Refresh menu to show profile options
                    invalidateOptionsMenu()
                    // Update badge and UI to reflect authentication status
                    //updateBadgeAndText() // Badge functionality disabled - using stub
                } else {
                    Toast.makeText(this@MainActivity, "Login failed: ${result.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Login error", e)
                Toast.makeText(this@MainActivity, "Login error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun performRegistration(email: String, password: String) {
        lifecycleScope.launch {
            try {
                // Use Firebase authentication instead of legacy authenticationService
                val fullName = email.substringBefore("@") // Use email prefix as default name
                val result = firebaseAuthService.registerWithFirebase(email, password, fullName)
                if (result.success) {
                    Toast.makeText(this@MainActivity, "Registration successful! You are now logged in.", Toast.LENGTH_SHORT).show()
                    // Refresh menu to show profile options
                    invalidateOptionsMenu()
                    // Update badge and UI to reflect authentication status
                    updateBadgeAndText() // Badge functionality disabled - using stub
                } else {
                    Toast.makeText(this@MainActivity, "Registration failed: ${result.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Registration error", e)
                Toast.makeText(this@MainActivity, "Registration error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun handleLogout() {
        lifecycleScope.launch {
            try {
                // Use Firebase authentication instead of legacy authenticationService
                val success = firebaseAuthService.signOut()
                if (success) {
                    Toast.makeText(this@MainActivity, "Logged out successfully", Toast.LENGTH_SHORT).show()
                    // Clear legacy data and refresh menu
                    forceClearAllLegacySubscriptionData()
                    invalidateOptionsMenu()
                    // Update badge to show free tier
                    updateBadgeAndText() // Badge functionality disabled - using stub
                } else {
                    Toast.makeText(this@MainActivity, "Logout failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Logout error", e)
                Toast.makeText(this@MainActivity, "Logout error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Google Sign-In Methods
    private fun setupGoogleSignInLauncher() {
        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            handleGoogleSignInResult(result.data)
        }
    }
    
    private fun startGoogleSignIn() {
        val signInIntent = firebaseAuthService.getGoogleSignInIntent()
        googleSignInLauncher.launch(signInIntent)
    }
    
    private fun handleGoogleSignInResult(data: Intent?) {
        lifecycleScope.launch {
            try {
                val result = firebaseAuthService.handleGoogleSignInResult(data)
                if (result.success) {
                    val message = if (result.isNewUser) {
                        "Welcome! Your account has been created successfully."
                    } else {
                        "Welcome back! Signed in successfully."
                    }
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    
                    // Refresh menu to show profile options
                    invalidateOptionsMenu()
                } else {
                    Toast.makeText(this@MainActivity, "Google Sign-In failed: ${result.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Google Sign-In error", e)
                Toast.makeText(this@MainActivity, "Google Sign-In error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Webapp Switching Methods
    private fun handleWebappSwitchAction() {
        lifecycleScope.launch {
            try {
                // Check if user is logged in
                val isLoggedIn = authenticationService.isLoggedIn()
                if (!isLoggedIn) {
                    showLoginDialog()
                    return@launch
                }
                
                // Show loading dialog
                val progressDialog = androidx.appcompat.app.AlertDialog.Builder(this@MainActivity, R.style.BlueDialogTheme)
                    .setTitle("Switching to Web")
                    .setMessage("Preparing webapp access...")
                    .setCancelable(false)
                    .create()
                
                progressDialog.show()
                
                // Generate webapp token using UnifiedBackendClient
                val backendClient = UnifiedBackendClient(this@MainActivity)
                val webappToken = backendClient.generateWebappToken()
                
                progressDialog.dismiss()
                
                if (webappToken != null) {
                    val webappUrl = "https://your-webapp-url.com/auth?token=${webappToken}"
                    showWebappSwitchDialog(webappUrl, 3600) // Token expires in 1 hour
                } else {
                    showWebappErrorDialog("Failed to generate webapp access token")
                }
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Webapp switch error", e)
                showWebappErrorDialog("Failed to prepare webapp switch: ${e.message}")
            }
        }
    }
    
    // TODO: Remove WebappSwitchingService parameter
    private fun showWebappSwitchDialog(webappUrl: String, expiresIn: Int) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.BlueDialogTheme)
            .setTitle("🌐 Switch to Web")
            .setMessage("Your AI Teacher webapp is ready!\n\n" +
                       "✅ Profile synced\n" +
                       "✅ Chat history synced\n" +
                       "✅ Preferences synced\n\n" +
                       "Access expires in ${expiresIn / 60} minutes.\n\n" +
                       "Open in browser?")
            .setPositiveButton("Open Webapp") { dialog: DialogInterface, which: Int ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webappUrl))
                    startActivity(intent)
                    Toast.makeText(this, "Webapp opened in browser", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    showWebappErrorDialog("Failed to open webapp: ${e.message}")
                }
            }
            .setNegativeButton("Copy Link") { dialog: DialogInterface, which: Int ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("AI Teacher Webapp", webappUrl)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Webapp link copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Cancel", null)
            .create()
        
        dialog.show()
    }
    
    private fun showWebappErrorDialog(message: String) {
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.BlueDialogTheme)
            .setTitle("Webapp Switch Failed")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Try Again") { dialog: DialogInterface, which: Int ->
                handleWebappSwitchAction()
            }
            .show()
    }

    // Chat Methods
    private fun startNewConversation() {
        currentConversationId = generateConversationId()

        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("is_new_conversation", true)
            putExtra("selected_model", currentModel)
            putExtra("conversation_id", currentConversationId)
        }
        startActivity(intent)
    }

    private fun openChatActivity(model: String, suggestedMessage: String? = null) {
        Log.d(
            "MainActivity",
            "Opening ChatActivity with model: $model, suggestedMessage: $suggestedMessage"
        )
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("selected_model", model)
            putExtra("is_new_conversation", true)
            putExtra("is_ad_free", sharedPreferences.getBoolean(keyAdFree, false))
            putExtra("expiration_time", sharedPreferences.getLong(expirationTimeKey, 0))
            if (suggestedMessage != null) {
                putExtra("suggested_message", suggestedMessage)
            }
        }
        startActivity(intent)
    }

    private fun setAdFree(isAdFree: Boolean) {
        // DEPRECATED: No longer save to SharedPreferences - SubscriptionUIManager handles this with Firestore
        Log.d("MainActivity", "setAdFree called with $isAdFree - delegating to SubscriptionUIManager")
        
        // Use SubscriptionUIManager which reads from Firestore instead of manually setting SharedPreferences
        lifecycleScope.launch {
            try {
                subscriptionUIManager.updateUIForSubscriptionStatus(this@MainActivity)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error updating subscription UI in setAdFree", e)
                // Fallback to old behavior only if Firestore fails
                setAdFreeLegacy(isAdFree)
            }
        }
    }
    
    private fun setAdFreeLegacy(isAdFree: Boolean) {
        // Legacy fallback method
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


    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // SECURITY FIX: Enhanced error handling for unauthenticated purchases
            if (!firebaseAuthService.isSignedIn()) {
                Log.e("MainActivity", "Critical: User paid for subscription but is not authenticated")
                
                // Show critical error dialog with recovery options
                AlertDialog.Builder(this)
                    .setTitle("Account Required")
                    .setMessage("Your purchase was successful, but you need to sign in to activate your subscription. Your purchase will be restored once you sign in.")
                    .setPositiveButton("Sign In Now") { dialog: DialogInterface, which: Int ->
                        val intent = Intent(this, ProfileActivity::class.java)
                        intent.putExtra("show_login", true)
                        intent.putExtra("purchase_recovery_mode", true)
                        intent.putExtra("pending_purchase_token", purchase.purchaseToken)
                        startActivity(intent)
                    }
                    .setNegativeButton("Later") { dialog: DialogInterface, which: Int ->
                        showCustomToast("Please sign in to activate your subscription")
                    }
                    .setCancelable(false)
                    .show()
                return
            }
            
            // Verify the purchase
            if (verifyPurchase(purchase)) {
                // Get purchase details for Firestore
                val productId = purchase.products[0]
                val orderId = purchase.orderId
                val purchaseToken = purchase.purchaseToken
                
                // Process purchase using subscription UI manager with Firestore integration
                lifecycleScope.launch {
                    try {
                        val success = when (productId) {
                            "basic_monthly_subscription" -> {
                                showCustomToast("Essential Plan purchased")
                                subscriptionUIManager.onSubscriptionPurchased(
                                    planType = "basic",
                                    billingCycle = "monthly",
                                    orderId = orderId,
                                    productId = productId,
                                    purchaseToken = purchaseToken,
                                    pricePaid = 9.99
                                )
                            }
                            "pro_monthly_plan" -> {
                                showCustomToast("Professional Plan purchased")
                                subscriptionUIManager.onSubscriptionPurchased(
                                    planType = "pro",
                                    billingCycle = "monthly",
                                    orderId = orderId,
                                    productId = productId,
                                    purchaseToken = purchaseToken,
                                    pricePaid = 19.99
                                )
                            }
                            "premium_monthly_subscription" -> {
                                showCustomToast("Premium Plan purchased")
                                subscriptionUIManager.onSubscriptionPurchased(
                                    planType = "premium",
                                    billingCycle = "monthly",
                                    orderId = orderId,
                                    productId = productId,
                                    purchaseToken = purchaseToken,
                                    pricePaid = 29.99
                                )
                            }
                            "ultra_monthly_subscription" -> {
                                showCustomToast("Enterprise Max purchased")
                                subscriptionUIManager.onSubscriptionPurchased(
                                    planType = "premium", // Map ultra to premium for Firestore
                                    billingCycle = "monthly",
                                    orderId = orderId,
                                    productId = productId,
                                    purchaseToken = purchaseToken,
                                    pricePaid = 39.99
                                )
                            }
                            else -> false
                        }
                        
                        if (success) {
                            // Update UI after successful Firestore save
                            setAdFree(true)
                            updateChatFragmentSubscriptionStatus()
                            //updateSubscriptionTimer() // Badge functionality disabled - using stub
                            
                            // Also save to SharedPreferences for backward compatibility
                            val expirationTime = System.currentTimeMillis() + 30 * 24 * 60 * 60 * 1000L
                            saveSubscriptionExpiration(expirationTime)
                            val tierName = when (productId) {
                                "basic_monthly_subscription" -> "BASIC"
                                "pro_monthly_plan" -> "PRO"
                                "premium_monthly_subscription" -> "PREMIUM"
                                "ultra_monthly_subscription" -> "ENTERPRISE"
                                else -> "FREE"
                            }
                            saveSubscriptionToPreferences(tierName, expirationTime)
                        } else {
                            Log.e("MainActivity", "Failed to save subscription to Firestore")
                            showCustomToast("Error activating subscription. Please try again.")
                        }
                        
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error processing subscription purchase", e)
                        showCustomToast("Error activating subscription. Please contact support.")
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
                            Log.e("MainActivity", "Error acknowledging purchase: ${billingResult.debugMessage}")
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

    /*private fun setSubscriptionTypeAndBadge(badge: String, text: String) {
        sharedPreferences.edit().apply {
            putString(subscriptionTypeKey, badge)
            apply()
        }

        updateBadgeAndText()
    }

    private fun updateBadgeAndText() {
        // IMPORTANT: Use Firestore data instead of legacy SharedPreferences
        // This ensures badge reflects actual subscription status from Google Play Billing
        lifecycleScope.launch {
            try {
                val firestoreSubscriptionManager = com.playstudio.aiteacher.profile.FirestoreSubscriptionManager(this@MainActivity)
                val firestoreAuthService = com.playstudio.aiteacher.profile.FirebaseAuthenticationService(this@MainActivity)
                
                if (!firestoreAuthService.isSignedIn()) {
                    // User not authenticated - show free tier
                    updateBadgeForFreeTier()
                    return@launch
                }
                
                val subscriptionStatus = firestoreSubscriptionManager.getSubscriptionStatus()
                Log.d("MainActivity", "Updating badge with Firestore data: isActive=${subscriptionStatus.isActive}, planType=${subscriptionStatus.planType}")
                
                if (subscriptionStatus.isActive && !subscriptionStatus.isExpired) {
                    // Active subscription from Firestore
                    updateBadgeForActivePlan(subscriptionStatus.planType, subscriptionStatus.daysRemaining)
                } else {
                    // Free tier or expired subscription
                    updateBadgeForFreeTier()
                    
                    // Clear old SharedPreferences data that might be causing conflicts
                    clearLegacySubscriptionData()
                }
                
                // CRITICAL: Also update SubscriptionUIManager to ensure buy buttons reflect Firestore data
                subscriptionUIManager.updateUIForSubscriptionStatus(this@MainActivity)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error updating badge with Firestore data, using legacy method", e)
                updateBadgeAndTextLegacy()
            }
        }
    }*/
    
    /*private fun updateBadgeForActivePlan(planType: String, daysRemaining: Int) {
        when (planType) {
            "basic" -> {
                binding.badgeImageView.setImageResource(R.drawable.bronze_badge)
                binding.badgeTextView.text = "Essential"
                binding.subscriptionStatusText.text = "Essential Plan Active\n$daysRemaining days remaining"
                binding.subscriptionStatusText.setTextColor(ContextCompat.getColor(this, R.color.green))
            }
            "pro" -> {
                binding.badgeImageView.setImageResource(R.drawable.silver_badge)
                binding.badgeTextView.text = "Professional"
                binding.subscriptionStatusText.text = "Professional Plan Active\n$daysRemaining days remaining"
                binding.subscriptionStatusText.setTextColor(ContextCompat.getColor(this, R.color.green))
            }
            "premium" -> {
                binding.badgeImageView.setImageResource(R.drawable.gold_badge)
                binding.badgeTextView.text = "Premium"
                binding.subscriptionStatusText.text = "Premium Plan Active\n$daysRemaining days remaining"
                binding.subscriptionStatusText.setTextColor(ContextCompat.getColor(this, R.color.gold))
            }
            "ultra_premium" -> {
                binding.badgeImageView.setImageResource(R.drawable.gold_badge)
                binding.badgeTextView.text = "Enterprise Max"
                binding.subscriptionStatusText.text = "Enterprise Max Active\n$daysRemaining days remaining"
                binding.subscriptionStatusText.setTextColor(ContextCompat.getColor(this, R.color.gold))
            }
            else -> {
                binding.badgeImageView.setImageResource(R.drawable.bronze_badge)
                binding.badgeTextView.text = "Pro"
                binding.subscriptionStatusText.text = "Premium Active\n$daysRemaining days remaining"
                binding.subscriptionStatusText.setTextColor(ContextCompat.getColor(this, R.color.green))
            }
        }
        binding.subscriptionTimer.visibility = View.VISIBLE
        updateSubscriptionTimer() // Badge functionality disabled - using stub
    }*/
    
    /*private fun updateBadgeForFreeTier() {
        // Free tier state
        binding.badgeImageView.setImageResource(R.drawable.bronze_badge)
        binding.badgeTextView.text = "Light"
        binding.subscriptionStatusText.text = "Upgrade for:\n• No ads\n• Better AI models\n• Image generation"
        binding.subscriptionStatusText.setTextColor(ContextCompat.getColor(this, R.color.red))
        binding.subscriptionTimer.visibility = View.GONE
    }*/
    
    private fun clearLegacySubscriptionData() {
        try {
            // Clear old SharedPreferences data that conflicts with Firestore
            sharedPreferences.edit()
                .remove(subscriptionTypeKey)
                .remove(expirationTimeKey)
                .remove(keyAdFree)
                .apply()
            Log.d("MainActivity", "Cleared legacy subscription data from SharedPreferences")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error clearing legacy subscription data", e)
        }
    }
    
    /**
     * Force clear all legacy subscription data to prevent conflicts with Firestore
     * Call this once to migrate from SharedPreferences to Firestore system
     */
    private fun forceClearAllLegacySubscriptionData() {
        try {
            // Clear all subscription-related SharedPreferences
            val subscriptionPrefs = getSharedPreferences("subscription_prefs", Context.MODE_PRIVATE)
            subscriptionPrefs.edit().clear().apply()
            
            sharedPreferences.edit()
                .remove(subscriptionTypeKey)
                .remove(expirationTimeKey)
                .remove(keyAdFree)
                .remove("subscription_type")
                .remove("expiration_time")
                .remove("is_premium")
                .remove("plan_type")
                .remove("billing_cycle")
                .apply()
                
            Log.d("MainActivity", "Force cleared ALL legacy subscription data - now using Firestore only")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error force clearing legacy subscription data", e)
        }
    }
    
    /*private fun updateBadgeAndTextLegacy() {
        // Legacy method as fallback
        val subscriptionType = sharedPreferences.getString(subscriptionTypeKey, null)
        val expirationTime = sharedPreferences.getLong(expirationTimeKey, 0)
        val currentTime = System.currentTimeMillis()

        if (currentTime < expirationTime) {
            // User is subscribed - premium state
            when (subscriptionType) {
                "basic" -> {
                    binding.badgeImageView.setImageResource(R.drawable.bronze_badge)
                    binding.badgeTextView.text = "Essential"
                    binding.subscriptionStatusText.text = "Essential Plan Active\nHigher Usage Limits"
                    binding.subscriptionStatusText.setTextColor(ContextCompat.getColor(this, R.color.green))
                }
                "pro" -> {
                    binding.badgeImageView.setImageResource(R.drawable.silver_badge)
                    binding.badgeTextView.text = "Professional"
                    binding.subscriptionStatusText.text = "Professional Plan Active\nMuch Higher Usage Limits"
                    binding.subscriptionStatusText.setTextColor(ContextCompat.getColor(this, R.color.green))
                }
                "premium" -> {
                    binding.badgeImageView.setImageResource(R.drawable.gold_badge)
                    binding.badgeTextView.text = "Premium"
                    binding.subscriptionStatusText.text = "Premium Plan Active\nVery High Usage Limits"
                    binding.subscriptionStatusText.setTextColor(ContextCompat.getColor(this, R.color.gold))
                }
                "ultra_premium" -> {
                    binding.badgeImageView.setImageResource(R.drawable.gold_badge)
                    binding.badgeTextView.text = "Enterprise Max"
                    binding.subscriptionStatusText.text = "Enterprise Max Active\nUnlimited Usage"
                    binding.subscriptionStatusText.setTextColor(ContextCompat.getColor(this, R.color.gold))
                }
                else -> {
                    binding.badgeImageView.setImageResource(R.drawable.bronze_badge)
                    binding.badgeTextView.text = "Pro"
                    binding.subscriptionStatusText.text = "Premium Active"
                    binding.subscriptionStatusText.setTextColor(ContextCompat.getColor(this, R.color.green))
                }
            }
            binding.subscriptionTimer.visibility = View.VISIBLE
            updateSubscriptionTimer() // Badge functionality disabled - using stub
        } else {
            // Free tier state
            binding.badgeImageView.setImageResource(R.drawable.bronze_badge)
            binding.badgeTextView.text = "Light"
            binding.subscriptionStatusText.text = "Upgrade for:\n• No ads\n• Better AI models\n• Image generation"
            binding.subscriptionStatusText.setTextColor(ContextCompat.getColor(this, R.color.red))
            binding.subscriptionTimer.visibility = View.GONE
        }
    }*/
    private fun saveSubscriptionExpiration(expirationTime: Long) {
        // DEPRECATED: No longer save to SharedPreferences - use Firestore instead
        // This prevents conflicts with Firestore-based subscription system
        Log.d("MainActivity", "saveSubscriptionExpiration called but using Firestore system instead")
        updateChatFragmentSubscriptionStatus()
    }


    private fun updateChatFragmentSubscriptionStatus() {
        // Call this function to notify ChatFragment of the new subscription status
        val isAdFree = sharedPreferences.getBoolean(keyAdFree, false)
        val expirationTime = sharedPreferences.getLong(expirationTimeKey, 0)
        subscriptionViewModel.updateSubscriptionStatus(isAdFree, expirationTime)
        
        // Also broadcast the subscription change to any listening fragments
        val intent = Intent("com.playstudio.aiteacher.SUBSCRIPTION_CHANGED")
        intent.putExtra("subscription_active", isAdFree)
        intent.putExtra("expiration_time", expirationTime)
        sendBroadcast(intent)
        
        Log.d("MainActivity", "updateChatFragmentSubscriptionStatus: Broadcasting subscription change")
    }
    
    private fun saveSubscriptionToPreferences(tier: String, expirationTime: Long) {
        val subscriptionPrefs = getSharedPreferences("subscription_prefs", Context.MODE_PRIVATE)
        subscriptionPrefs.edit().apply {
            putString("subscription_tier", tier)
            putLong("expiration_time", expirationTime)
            putBoolean("subscription_active", true)
            putLong("activation_time", System.currentTimeMillis())
            apply()
        }
    }
    
    private fun checkExistingSubscription() {
        val isAdFree = sharedPreferences.getBoolean(keyAdFree, false)
        val expirationTime = sharedPreferences.getLong(expirationTimeKey, 0)
        val currentTime = System.currentTimeMillis()
        
        Log.d("MainActivity", "checkExistingSubscription: isAdFree=$isAdFree, expirationTime=$expirationTime, currentTime=$currentTime")
        
        if (isAdFree && expirationTime > currentTime) {
            // User has an active subscription, get the tier from subscription_prefs or default to BASIC
            val subscriptionPrefs = getSharedPreferences("subscription_prefs", Context.MODE_PRIVATE)
            val existingTier = subscriptionPrefs.getString("subscription_tier", "BASIC")
            
            // If we don't have a tier saved, default to BASIC for existing subscriptions
            val tier = existingTier ?: "BASIC"
            
            saveSubscriptionToPreferences(tier, expirationTime)
            Log.d("MainActivity", "checkExistingSubscription: Synced existing subscription to subscription_prefs with tier $tier")
        } else if (isAdFree && expirationTime <= currentTime) {
            // Subscription has expired
            setAdFree(false)
            // Also update subscription_prefs to reflect expired state
            val subscriptionPrefs = getSharedPreferences("subscription_prefs", Context.MODE_PRIVATE)
            subscriptionPrefs.edit().apply {
                putBoolean("subscription_active", false)
                putString("subscription_tier", "FREE")
                apply()
            }
            Log.d("MainActivity", "checkExistingSubscription: Subscription expired, set adFree to false")
        } else {
            // No subscription or expired - ensure subscription_prefs reflects FREE status
            val subscriptionPrefs = getSharedPreferences("subscription_prefs", Context.MODE_PRIVATE)
            subscriptionPrefs.edit().apply {
                putBoolean("subscription_active", false)
                putString("subscription_tier", "FREE")
                putLong("expiration_time", 0)
                apply()
            }
            Log.d("MainActivity", "checkExistingSubscription: No active subscription, set to FREE")
        }
        
        // Update ChatFragment with current status
        updateChatFragmentSubscriptionStatus()
    }

    private fun startPurchaseFlow(productId: String) {
        // SECURITY FIX: Require authentication before any subscription purchase
        if (!firebaseAuthService.isSignedIn()) {
            Log.w("MainActivity", "Authentication required before subscription purchase")
            showCustomToast("Please sign in to purchase a subscription")
            
            // Redirect to authentication
            val intent = Intent(this, ProfileActivity::class.java)
            intent.putExtra("show_login", true)
            intent.putExtra("redirect_after_login", "subscription_purchase")
            intent.putExtra("requested_product_id", productId)
            startActivity(intent)
            return
        }
        
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

            Log.d("MainActivity", "Launching billing flow for authenticated user")
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
                .setProductId("basic_monthly_subscription")
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("pro_monthly_plan")
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("premium_monthly_subscription")
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("ultra_monthly_subscription")
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList != null) {
                for (productDetails in productDetailsList) {
                    productDetailsMap[productDetails.productId] = productDetails
                }
            } else {
                Log.e("MainActivity", "Error querying products: ${billingResult.debugMessage}")
            }
        }
    }
    private fun handleVersionItemTap() {
        val currentTime = System.currentTimeMillis()

        // Reset if more than 1 second between taps
        if (currentTime - lastVersionTapTime > 1000) {
            versionTapCount = 0
        }

        versionTapCount++
        lastVersionTapTime = currentTime

        if (versionTapCount >= 10) {
            versionTapCount = 0
            showPromoCodeDialog()
        } else {
            showVersionInfoToast()
        }
    }


    private fun checkSecretTap(x: Float, y: Float) {
        val currentTime = System.currentTimeMillis()

        // Reset if too much time between taps
        if (currentTime - lastSecretTapTime > SECRET_TAP_TIMEOUT) {
            secretTapCount = 0
        }

        // Only count taps in TOP-LEFT corner (first 20% of screen width and height)
        if (x < window.decorView.width * 0.2f &&
            y < window.decorView.height * 0.2f) {
            secretTapCount++

            if (secretTapCount >= SECRET_TAP_COUNT) {
                secretTapCount = 0
                showPromoCodeDialog()
            }
        }

        lastSecretTapTime = currentTime
    }


    private fun showVersionInfoToast() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            Toast.makeText(this, "Version ${packageInfo.versionName}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Version info unavailable", Toast.LENGTH_SHORT).show()
        }
    }


    /*private fun checkSubscriptionStatus() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                var hasActiveSubscription = false
                for (purchase in purchases) {
                    if (purchase.products.contains("subscription_7days") || purchase.products.contains("monthly_subscription") || purchase.products.contains("yearly_subscription")) {
                        if (isSubscriptionActive(purchase)) {
                            hasActiveSubscription = true
                            setAdFree(true)
                            updateSubscriptionTimer() // Badge functionality disabled - using stub // Add this line
                        }
                    }
                }
                if (!hasActiveSubscription) {
                    showAds()
                    startButtonAnimation()
                }
            } else {
                Log.e("MainActivity", "Error querying purchases: ${billingResult.debugMessage}")
                //showCustomToast("Error querying purchases: ${billingResult.debugMessage}")
            }
            val isAdFree = sharedPreferences.getBoolean(keyAdFree, false)
            val expirationTime = sharedPreferences.getLong(expirationTimeKey, 0)
            subscriptionViewModel.updateSubscriptionStatus(isAdFree, expirationTime)
            updateBadgeAndText() // Badge functionality disabled - using stub // Ensure the badge and text are updated
        }
    }*/





    private fun setupPromoCodeDetection(titleView: TextView) {
        var tapCount = 0
        var lastTapTime = 0L

        titleView.setOnClickListener {
            val currentTime = System.currentTimeMillis()

            // Reset if too much time between taps
            if (currentTime - lastTapTime > SECRET_TAP_TIMEOUT) {
                tapCount = 0
            }

            tapCount++
            lastTapTime = currentTime

            if (tapCount >= SECRET_TAP_COUNT) {
                tapCount = 0
                showPromoCodeDialog()
            }
        }
    }

    private fun showPromoCodeDialog() {
        val input = EditText(this).apply {
            hint = "Enter promo code"
            inputType = InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }

        AlertDialog.Builder(this)
            .setTitle("Reviewer Access")
            .setMessage("Enter promo code to unlock all features")
            .setView(input)
            .setPositiveButton("Apply") { dialog: DialogInterface, which: Int ->
                verifyPromoCode(input.text.toString().trim())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun verifyPromoCode(code: String): Boolean {
        if (code.uppercase() != GOOGLE_REVIEW_PROMO) {
            Toast.makeText(this, "Invalid promo code", Toast.LENGTH_SHORT).show()
            return false
        }

        // Grant temporary premium access
        val expirationTime = System.currentTimeMillis() + PROMO_EXPIRATION
        sharedPreferences.edit().apply {
            putBoolean(keyAdFree, true)
            putLong(expirationTimeKey, expirationTime)
            putString(subscriptionTypeKey, "gold")
            apply()
        }

        Toast.makeText(this, "Premium features unlocked!", Toast.LENGTH_SHORT).show()
        updateBadgeAndText()
        checkAdFreeStatus()
        return true
    }
    private fun checkAdFreeStatus() {
        val isAdFree = sharedPreferences.getBoolean(keyAdFree, false) || checkPromoStatus()
        val expirationTime = sharedPreferences.getLong(expirationTimeKey, 0)

        if (isAdFree || System.currentTimeMillis() < expirationTime) {
            setAdFree(true)
        } else {
            setAdFree(false)
        }
        subscriptionViewModel.updateSubscriptionStatus(isAdFree, expirationTime)
    }



    // Add this function to check for active promo status
    private fun checkPromoStatus(): Boolean {
        val expirationTime = sharedPreferences.getLong(expirationTimeKey, 0)
        return System.currentTimeMillis() < expirationTime &&
                sharedPreferences.getBoolean(keyAdFree, false)
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

            binding.searchBar.setTextColor(textColor)

        } catch (e: Resources.NotFoundException) {
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
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.5
    }

    private fun saveSelectedColor(drawableResId: Int) {
        sharedPreferences.edit().putInt("selected_color", drawableResId).apply()
    }

    private fun loadSelectedColor() {
        val drawableResId = sharedPreferences.getInt("selected_color", R.drawable.gradient_black) // Default gradient drawable
        changeBackgroundColor(drawableResId)
    }

    private fun setBackgroundColor(drawableResId: Int) {
        val nonChatElements: View = findViewById(R.id.non_chat_elements)
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

    private fun showAIThemeSelectionDialog() {
        val themeManager = ThemeManager(this)
        val currentTheme = themeManager.getCurrentTheme()
        
        val dialog = ThemeSelectionDialog(this, currentTheme) { selectedTheme ->
            themeManager.setTheme(selectedTheme)
            
            // Apply the theme to the current activity
            applyCurrentTheme()
        }
        dialog.show()
    }

    private fun startButtonAnimation() {
        // Check if the user is subscribed
        val isAdFree = sharedPreferences.getBoolean(keyAdFree, false)
        val expirationTime = sharedPreferences.getLong(expirationTimeKey, 0)
        val currentTime = System.currentTimeMillis()

        if (!isAdFree || currentTime >= expirationTime) {
            // Load the pulsing animation
            val pulseAnimation: Animation = AnimationUtils.loadAnimation(this, R.anim.pulse)
            binding.buyButton.startAnimation(pulseAnimation)

            // Create a glowing animation
            val colorFrom = ContextCompat.getColor(this, R.color.colorPrimaryDark)
            val colorTo = ContextCompat.getColor(this, R.color.yellow)
            val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo).apply {
                duration = 1000 // duration for each transition
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
            }

            colorAnimation.addUpdateListener { animator ->
                binding.buyButton.setBackgroundColor(animator.animatedValue as Int)
            }

            colorAnimation.start()
        } else {
            // Stop any ongoing animations if the user is subscribed
            binding.buyButton.clearAnimation()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::billingClient.isInitialized && billingClient.isReady) {
            billingClient.endConnection()
        }
        
        // Cleanup billing sync service
        try {
            if (::billingSync.isInitialized) {
                billingSync.cleanup()
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "Error cleaning up billing sync", e)
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

    companion object {
        private const val REQUEST_CODE_NOTIFICATION_PERMISSION = 1001
        private const val PERMISSION_REQUEST_CODE = 100  // For storage permissions
        private const val SPEECH_REQUEST_CODE = 123
        private const val PREFS_NAME = "app_preferences"       // Name of our preferences file
        private const val FIRST_LAUNCH_KEY = "is_first_launch" // Key for our first launch flag
        // Add these constants at the top of your MainActivity
        private const val PROMO_CODE_KEY = "promo_code"
        private const val GOOGLE_REVIEW_PROMO = "GOOGLE_REVIEW_2024" // Change this to a unique code
        private const val PROMO_EXPIRATION = 7 * 24 * 60 * 60 * 1000L // 7 days

        private const val SECRET_TAP_COUNT = 7
        private const val SECRET_TAP_TIMEOUT = 1000L // 1 second between taps

        // Shared constants so fragments can access user identifier
        const val USER_PREFS = "user_prefs"
        const val USER_ID_KEY = "user_id"
        const val WEB_APP_BASE_URL = "https://your-webapp.example.com"
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_NOTIFICATION_PERMISSION
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Set the title to "Home" when the activity is resumed
        supportActionBar?.title = "Home"
        
        // Use updated subscription management with authentication and Firestore
        lifecycleScope.launch {
            try {
                // Check and sync billing data if needed (periodic sync)
                try {
                    billingSync.checkAndSyncIfNeeded()
                } catch (e: Exception) {
                    Log.w("MainActivity", "Billing sync check failed on resume", e)
                }
                
                // Update UI for subscription status (now includes authentication check and Firestore)
                subscriptionUIManager.updateUIForSubscriptionStatus(this@MainActivity)
                
                // Check and update legacy subscription status for backward compatibility
                //checkSubscriptionStatus()
                updateBadgeAndText() // Badge functionality disabled - using stub
                //updateSubscriptionTimer() // Badge functionality disabled - using stub
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Error updating subscription status on resume", e)
                // Fallback to legacy methods
                //checkSubscriptionStatus()
                updateBadgeAndText() // Badge functionality disabled - using stub
                //updateSubscriptionTimer() // Badge functionality disabled - using stub
            }
        }
        
        // Cancel existing reminders and schedule new one with current time
        cancelReminder()
        updateLastInteractionTime()
        scheduleReminder()
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
            
            // Trigger billing sync to Firestore after successful purchase
            lifecycleScope.launch {
                try {
                    Log.d("MainActivity", "Purchase completed, syncing to Firestore...")
                    val syncSuccess = billingSync.syncSubscriptionToFirestore()
                    if (syncSuccess) {
                        Log.d("MainActivity", "Billing sync to Firestore completed successfully")
                        // Update UI after sync
                        subscriptionUIManager.updateUIForSubscriptionStatus(this@MainActivity)
                    } else {
                        Log.e("MainActivity", "Failed to sync billing to Firestore")
                        showCustomToast("Purchase completed, but sync failed. Please check your profile.")
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error during billing sync", e)
                    showCustomToast("Purchase completed, but sync failed. Please check your profile.")
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            showCustomToast("Purchase canceled")
        } else {
            Log.e("MainActivity", "Error during purchase: ${billingResult.debugMessage}")
            showCustomToast("Error: ${billingResult.debugMessage}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "daily_reminder_channel"
            val channelName = "Daily Reminder"
            val channelDescription = "Reminders for the app"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance)
            channel.description = channelDescription
            val notificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateLastInteractionTime() {
        lastInteractionTime = System.currentTimeMillis()
        val editor = sharedPreferences.edit()
        editor.putLong(lastInteractionTimeKey, lastInteractionTime)
        editor.apply()
        Log.d("MainActivity", "Last interaction time updated: $lastInteractionTime")
    }

    private fun scheduleReminder() {
        val lastInteractionTime = sharedPreferences.getLong(lastInteractionTimeKey, System.currentTimeMillis())
        val currentTime = System.currentTimeMillis()
        val timeSinceLastInteraction = currentTime - lastInteractionTime

        val delay = if (timeSinceLastInteraction >= 24 * 60 * 60 * 1000) {
            0L // Trigger immediately if overdue
        } else {
            24 * 60 * 60 * 1000 - timeSinceLastInteraction // Time until 24 hours
        }

        val workRequest = OneTimeWorkRequest.Builder(ReminderWorker::class.java)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueue(workRequest)
    }

    private fun cancelReminder() {
        WorkManager.getInstance(this).cancelAllWork()
        Log.d("Reminder", "Pending reminders canceled")
    }

    private fun showSubscriptionOptionsWithRetry(retryCount: Int = 0) {
        Log.d("MainActivity", "Checking auth state for subscription dialog (attempt ${retryCount + 1})")
        
        if (firebaseAuthService.isSignedIn()) {
            Log.d("MainActivity", "User authenticated, showing subscription options")
            showSubscriptionOptions()
        } else if (retryCount < 3) {
            Log.w("MainActivity", "Auth state not ready, retrying in ${(retryCount + 1) * 500}ms...")
            Handler(Looper.getMainLooper()).postDelayed({
                showSubscriptionOptionsWithRetry(retryCount + 1)
            }, (retryCount + 1) * 500L) // 500ms, 1000ms, 1500ms delays
        } else {
            Log.e("MainActivity", "Auth state still not ready after 3 retries, showing auth required dialog")
            showAuthenticationRequiredDialog()
        }
    }

    private fun showSubscriptionOptions() {
        // Check authentication before showing subscription options
        if (!firebaseAuthService.isSignedIn()) {
            Log.w("MainActivity", "User not authenticated, showing authentication required dialog")
            showAuthenticationRequiredDialog()
            return
        }
        
        try {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_subscription, null)
            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create()

            // Get references to NEW view IDs from your updated layout
            val basicSubscription = dialogView.findViewById<LinearLayout>(R.id.basicSubscription)
            val proSubscription = dialogView.findViewById<LinearLayout>(R.id.proSubscription)
            val premiumSubscription = dialogView.findViewById<LinearLayout>(R.id.premiumSubscription)
            val ultraSubscription = dialogView.findViewById<LinearLayout>(R.id.ultraSubscription)
            
            val basicPrice = dialogView.findViewById<TextView>(R.id.basicPrice)
            val proPrice = dialogView.findViewById<TextView>(R.id.proPrice)
            val premiumPrice = dialogView.findViewById<TextView>(R.id.premiumPrice)
            val ultraPrice = dialogView.findViewById<TextView>(R.id.ultraPrice)
            
            val btnBuy = dialogView.findViewById<Button>(R.id.btnBuy)
            val btnClose = dialogView.findViewById<TextView>(R.id.btnClose)

            // Track selected subscription
            var selectedProductId: String? = null

            // Update prices with Google Play billing if available
            updateSubscriptionPrices(basicPrice, proPrice, premiumPrice, ultraPrice)

            // Selection function to highlight selected tier
            fun selectSubscription(productId: String, selectedView: LinearLayout, tierName: String) {
                // Reset all backgrounds to unselected
                basicSubscription?.setBackgroundResource(R.drawable.subscription_option_unselected)
                proSubscription?.setBackgroundResource(R.drawable.subscription_option_unselected)
                premiumSubscription?.setBackgroundResource(R.drawable.subscription_option_unselected)
                ultraSubscription?.setBackgroundResource(R.drawable.subscription_option_unselected)
                
                // Highlight selected tier
                Log.d("MainActivity", "Setting subscription_option_selected on view ID = ${selectedView.id}, size = ${selectedView.width}x${selectedView.height}")
                selectedView.setBackgroundResource(R.drawable.subscription_option_selected)
                
                selectedProductId = productId
                btnBuy?.isEnabled = true
                btnBuy?.text = "🔥 UPGRADE TO $tierName"
                
                Log.d("MainActivity", "Selected subscription: $productId")
            }

            // Set up click listeners for NEW layout IDs
            basicSubscription?.setOnClickListener { 
                selectSubscription("basic_monthly_subscription", basicSubscription, "ESSENTIAL")
            }
            
            proSubscription?.setOnClickListener { 
                selectSubscription("pro_monthly_plan", proSubscription, "PROFESSIONAL")
            }
            
            premiumSubscription?.setOnClickListener { 
                selectSubscription("premium_monthly_subscription", premiumSubscription, "PREMIUM")
            }
            
            ultraSubscription?.setOnClickListener { 
                selectSubscription("ultra_monthly_subscription", ultraSubscription, "ENTERPRISE MAX")
            }

            // SECURITY FIX: Purchase button click with authentication check
            btnBuy?.setOnClickListener {
                // Check authentication before allowing purchase
                if (!firebaseAuthService.isSignedIn()) {
                    Log.w("MainActivity", "Authentication required for subscription purchase from dialog")
                    dialog.dismiss()
                    
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Sign In Required")
                        .setMessage("Please sign in to purchase a subscription. This ensures your subscription can be restored on all your devices.")
                        .setPositiveButton("Sign In") { dialog: DialogInterface, which: Int ->
                            val intent = Intent(this@MainActivity, ProfileActivity::class.java)
                            intent.putExtra("show_login", true)
                            intent.putExtra("redirect_after_login", "subscription_dialog")
                            startActivity(intent)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    return@setOnClickListener
                }
                
                selectedProductId?.let { productId ->
                    try {
                        Log.d("MainActivity", "Starting authenticated purchase flow for: $productId")
                        startPurchaseFlow(productId)
                        dialog.dismiss()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error starting purchase flow", e)
                        showCustomToast("Error starting purchase. Please try again.")
                    }
                } ?: run {
                    showCustomToast("Please select a subscription plan first")
                }
            }

            // Close button click
            btnClose?.setOnClickListener {
                Log.d("MainActivity", "Subscription dialog closed")
                dialog.dismiss()
            }

            // Show the dialog
            dialog.show()
            
            Log.d("MainActivity", "Subscription dialog shown successfully")
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Error showing subscription dialog", e)
            showCustomToast("Error loading subscription options")
        }
    }

    // Helper method to update subscription prices from Google Play billing
    private fun updateSubscriptionPrices(
        basicPrice: TextView?,
        proPrice: TextView?,
        premiumPrice: TextView?,
        ultraPrice: TextView?
    ) {
        try {
            // Update with Google Play billing prices if available
            productDetailsMap["basic_monthly_subscription"]?.let { productDetails ->
                val price = productDetails.subscriptionOfferDetails?.firstOrNull()
                    ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                if (price != null) {
                    basicPrice?.text = price
                    Log.d("MainActivity", "Updated Essential price: $price")
                }
            }
            
            productDetailsMap["pro_monthly_plan"]?.let { productDetails ->
                val price = productDetails.subscriptionOfferDetails?.firstOrNull()
                    ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                if (price != null) {
                    proPrice?.text = price
                    Log.d("MainActivity", "Updated Professional price: $price")
                }
            }
            
            productDetailsMap["premium_monthly_subscription"]?.let { productDetails ->
                val price = productDetails.subscriptionOfferDetails?.firstOrNull()
                    ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                if (price != null) {
                    premiumPrice?.text = price
                    Log.d("MainActivity", "Updated Premium price: $price")
                }
            }
            
            productDetailsMap["ultra_monthly_subscription"]?.let { productDetails ->
                val price = productDetails.subscriptionOfferDetails?.firstOrNull()
                    ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                if (price != null) {
                    ultraPrice?.text = price
                    Log.d("MainActivity", "Updated Enterprise Max price: $price")
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating subscription prices", e)
        }
    }



    private fun isFirstLaunch(): Boolean {
        // 1. Access SharedPreferences (creates file if it doesn't exist)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // 2. Try to read the flag (default to TRUE if not found)
        val isFirstLaunch = prefs.getBoolean(FIRST_LAUNCH_KEY, true)

        // 3. If this is actually the first launch
        if (isFirstLaunch) {
            // 4. Immediately mark that we've completed first launch
            prefs.edit().putBoolean(FIRST_LAUNCH_KEY, false).apply()
            return true
        }

        // 5. For all subsequent launches
        return false
    }

    private fun showSubscriptionDialog() {
        // Don't show if it's the first launch OR if the user is already subscribed
        if (isFirstLaunch() || isUserSubscribed()) {
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_subscription, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        /*/ Rest of your existing dialog setup...
        val weeklySubscription = dialogView.findViewById<View>(R.id.weeklySubscription)
        val monthlySubscription = dialogView.findViewById<View>(R.id.monthlySubscription)
        val yearlySubscription = dialogView.findViewById<View>(R.id.yearlySubscription)*/
        val btnBuy = dialogView.findViewById<Button>(R.id.btnBuy)
        val btnClose = dialogView.findViewById<TextView>(R.id.btnClose)

        var selectedSubscription: String? = null

        /*/ Subscription option click listeners
        weeklySubscription.setOnClickListener {
            updateSubscriptionUI(weeklySubscription, monthlySubscription, yearlySubscription)
            selectedSubscription = "subscription_7days"
            btnBuy.isEnabled = true
        }

        monthlySubscription.setOnClickListener {
            updateSubscriptionUI(monthlySubscription, weeklySubscription, yearlySubscription)
            selectedSubscription = "monthly_subscription"
            btnBuy.isEnabled = true
        }

        yearlySubscription.setOnClickListener {
            updateSubscriptionUI(yearlySubscription, weeklySubscription, monthlySubscription)
            selectedSubscription = "yearly_subscription"
            btnBuy.isEnabled = true
        }*/

        btnBuy.setOnClickListener {
            selectedSubscription?.let { sub ->
                startPurchaseFlow(sub)
                dialog.dismiss()
            }
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // Set prices from Google Play Billing
        updatePriceDisplay(dialogView)

        // Highlight default selection
        //weeklySubscription.performClick()

        dialog.show()
    }

    // Helper function to update subscription UI state
    private fun updateSubscriptionUI(
        selectedView: View,
        unselectedView1: View,
        unselectedView2: View
    ) {
        Log.d("MainActivity", "updateSubscriptionUI: selectedView ID = ${selectedView.id}, size = ${selectedView.width}x${selectedView.height}")
        selectedView.background = ContextCompat.getDrawable(this, R.drawable.subscription_option_selected)
        unselectedView1.background = ContextCompat.getDrawable(this, R.drawable.subscription_option_unselected)
        unselectedView2.background = ContextCompat.getDrawable(this, R.drawable.subscription_option_unselected)
    }

    // Helper function to update price display
    private fun updatePriceDisplay(dialogView: View) {
        // Use the actual view IDs from dialog_subscription.xml layout
        val basicPrice = dialogView.findViewById<TextView>(R.id.basicPrice)
        val proPrice = dialogView.findViewById<TextView>(R.id.proPrice)
        val premiumPrice = dialogView.findViewById<TextView>(R.id.premiumPrice)
        val ultraPrice = dialogView.findViewById<TextView>(R.id.ultraPrice)

        // Update pricing based on current SKU details
        skuDetailsList.forEach { skuDetails ->
            when (skuDetails.sku) {
                "basic_monthly_subscription" -> {
                    basicPrice?.text = skuDetails.price
                }
                "pro_monthly_subscription" -> {
                    proPrice?.text = skuDetails.price
                }
                "premium_monthly_subscription" -> {
                    premiumPrice?.text = skuDetails.price
                }
                "ultra_monthly_subscription" -> {
                    ultraPrice?.text = skuDetails.price
                }
            }
        }
    }




    private fun refreshSubscriptionDialogs() {
        // Implement if you need to refresh dialogs that are already showing
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            SPEECH_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    results?.get(0)?.let { spokenText ->
                        passRecognizedTextToChatFragment(spokenText)
                    }
                }
            }

            EmailProviderHelper.EMAIL_PICK_REQUEST -> {
                if (resultCode == RESULT_OK && data != null) {
                    val progressDialog = ProgressDialog(this).apply {
                        setMessage("Processing email...")
                        setCancelable(false)
                        show()
                    }

                    emailProviderHelper.extractEmailContent(data) { emailMessage ->
                        runOnUiThread {
                            progressDialog.dismiss()

                            emailMessage?.let { message ->
                                passEmailToChatFragment(
                                    subject = message.subject,
                                    body = message.body
                                )
                            } ?: run {
                                Toast.makeText(
                                    this,
                                    "Failed to extract email content",
                                    Toast.LENGTH_LONG // Make it longer for error messages

                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }



    /*private fun updateSubscriptionTimer() {
        val expirationTime = sharedPreferences.getLong(expirationTimeKey, 0)
        val currentTime = System.currentTimeMillis()

        timerRunnable?.let { timerHandler.removeCallbacks(it) }

        if (expirationTime > currentTime) {
            binding.subscriptionTimer.visibility = View.VISIBLE
            binding.subscriptionStatusText.text = "Premium Active" // Ensure this is set

            // Update immediately first
            updateTimerText(expirationTime)

            timerRunnable = object : Runnable {
                override fun run() {
                    updateTimerText(expirationTime)
                    timerHandler.postDelayed(this, 60000) // Update every minute
                }
            }
            timerHandler.post(timerRunnable as Runnable)
        } else {
            binding.subscriptionTimer.visibility = View.GONE
            binding.subscriptionStatusText.text = "You're missing out on:\n- No ads\n- Premium features"
        }
    }*/



   /* private fun updateTimerText(expirationTime: Long) {
        val remainingTime = expirationTime - System.currentTimeMillis()

        if (remainingTime > 0) {
            val days = TimeUnit.MILLISECONDS.toDays(remainingTime)
            val hours = TimeUnit.MILLISECONDS.toHours(remainingTime) % 24
            val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime) % 60

            binding.subscriptionTimer.text = when {
                days > 1 -> String.format(Locale.getDefault(), "Expires in %d days", days)
                days == 1L -> String.format(Locale.getDefault(), "Expires in 1 day %d hrs", hours)
                hours > 0 -> String.format(Locale.getDefault(), "Expires in %d hrs %d min", hours, minutes)
                else -> String.format(Locale.getDefault(), "Expires in %d min", minutes)
            }

            // Change color based on remaining time
            binding.subscriptionTimer.setTextColor(when {
                days < 1 -> ContextCompat.getColor(this, R.color.red) // Less than 1 day - red
                days < 3 -> ContextCompat.getColor(this, R.color.orange) // Less than 3 days - orange
                else -> ContextCompat.getColor(this, R.color.green) // More than 3 days - green
            })
        } else {
            // Subscription expired
            binding.subscriptionTimer.visibility = View.GONE
            timerRunnable?.let { timerHandler.removeCallbacks(it) }
            setAdFree(false)
            updateBadgeAndText() // Badge functionality disabled - using stub

            // Show what user is missing
            binding.subscriptionStatusText.text = "You're missing:\n- Ad-free experience\n- Premium models"
        }
    }*/

    private fun enhanceBuyButton() {
        // Apply pulse animation (View animation)
        val pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.button_pulse)
        binding.buyButton.startAnimation(pulseAnimation)

        // Apply glow animation (Property animator)
        val glowAnimator = AnimatorInflater.loadAnimator(this, R.animator.glowing_animation)
        glowAnimator.setTarget(binding.buyButton)
        glowAnimator.start()

        // Add click animation
        binding.buyButton.setOnClickListener {
            it.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    it.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .setInterpolator(OvershootInterpolator(1.5f))
                        .start()
                    showSubscriptionOptions()
                }
                .start()
        }
    }



    private fun showEmailAccountPicker(accounts: List<Account>) {
        val accountNames = accounts.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Email Account")
            .setItems(accountNames) { _, which ->
                // Call your method to open the email client with the selected account
                openEmailClient(accounts[which]) // This calls startActivityForResult
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun openGenericEmailPicker() {
        try {
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "message/rfc822"
            }
            startActivityForResult(intent, EmailProviderHelper.EMAIL_PICK_REQUEST) // Use constant from helper
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
            // Alternative approach for devices without email picker
            showAlternativeEmailOptions()
        }
    }
    private fun openEmailClient(account: Account? = null) {
        // The 'account' parameter is often not directly usable with a generic ACTION_PICK intent.
        // Email clients typically don't filter by a passed Account object for ACTION_PICK.
        // The main purpose here is to launch an email picker.
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "message/rfc822"
            // Passing 'account' as an extra is non-standard and likely ignored by most email apps for ACTION_PICK.
            // if (account != null) { intent.putExtra("account", account) }
        }
        try {
            startActivityForResult(intent, EmailProviderHelper.EMAIL_PICK_REQUEST) // Use constant from helper
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
        }
    }
    private fun showAlternativeEmailOptions() {
        AlertDialog.Builder(this)
            .setTitle("Email App Not Found")
            .setMessage("Would you like to install an email app or copy your email manually?")
            .setPositiveButton("Install Gmail") { dialog: DialogInterface, which: Int ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("market://details?id=com.google.android.gm")
                    })
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.gm")
                    })
                }
            }
            .setNegativeButton("Copy Manually") { dialog: DialogInterface, which: Int ->
                // Open chat with instructions
                openChatActivityWithMessage("Please paste your email content here for analysis.")
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun requestEmailPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissionNeeded = Manifest.permission.GET_ACCOUNTS
            if (ContextCompat.checkSelfPermission(this, permissionNeeded) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissionNeeded)) {
                    // Explain why you need the permission
                    AlertDialog.Builder(this)
                        .setTitle("Permission Needed")
                        .setMessage("This app needs access to your accounts to list available email accounts. This helps you select an email for AI-assisted responses. Your email content is processed locally for this purpose only and is not stored or shared.")
                        .setPositiveButton("Grant Permission") { dialog: DialogInterface, which: Int ->
                            ActivityCompat.requestPermissions(
                                this,
                                arrayOf(permissionNeeded),
                                EmailProviderHelper.EMAIL_PERMISSION_REQUEST_CODE // Use constant from helper
                            )
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    // No explanation needed; request the permission
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(permissionNeeded),
                        EmailProviderHelper.EMAIL_PERMISSION_REQUEST_CODE // Use constant from helper
                    )
                }
            }
            // Note: READ_CONTACTS is not strictly required for just listing accounts via AccountManager for this feature.
            // If you expand to use contact details associated with emails, you might add it.
            // For now, GET_ACCOUNTS is sufficient.
        }
    }


    // Handle permission results
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSION_REQUEST_CODE -> { // Existing storage permission
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    openFilePicker()
                } else {
                    Toast.makeText(this, "Storage permission denied.", Toast.LENGTH_SHORT).show()
                }
            }
            SPEECH_REQUEST_CODE -> { // Existing speech permission
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    launchSpeechRecognizer()
                } else {
                    Toast.makeText(this, "Audio recording permission denied.", Toast.LENGTH_SHORT).show()
                }
            }
            EmailProviderHelper.EMAIL_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permissions granted, try again
                    binding.btnExtractEmail.performClick()
                } else {
                    Toast.makeText(
                        this, "Permission to access accounts denied. Email feature may not work as expected.", Toast.LENGTH_LONG
                    ).show()
                }
            }
            // Handle other permission request codes if you have them
        }
    }
    private fun passEmailToChatFragment(subject: String, body: String) {
        val formattedMessage = "Email Subject: $subject\n\nEmail Content:\n$body"

        // Check if ChatFragment is currently visible
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

        if (currentFragment is ChatFragment) {
            currentFragment.setExtractedText(formattedMessage)
        } else {
            // Create new ChatFragment instance and pass the text
            val chatFragment = ChatFragment().apply {
                arguments = Bundle().apply {
                    putString("extracted_text", formattedMessage)
                }
            }

            // Replace current fragment with ChatFragment
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, chatFragment)
                .addToBackStack(null)
                .commit()
        }
    }
    private val jobImpactQuestions = listOf(
        "🤖 Will AI take my job? What roles are safest?",
        "🛡️ How can I make my job AI-proof?",
        "🌐 Which industries will AI disrupt the most?",
        "📚 What skills should I learn to work with AI?",
        "🚀 How can I use AI to do my job better?"
    )
    private fun showJobImpactQuestions() {
        val dialog = AlertDialog.Builder(this, R.style.BlueDialogTheme)
            .setTitle("AI Job impact questions")
            .setItems(jobImpactQuestions.toTypedArray()) { _, which ->
                val selectedQuestion = jobImpactQuestions[which]
                passQuestionToChatFragment(selectedQuestion)
            }
            .setNegativeButton("Cancel", null)
            .create()

        // This ensures the shadow appears
        dialog.window?.apply {
            setBackgroundDrawableResource(R.drawable.dialog_background_blue)
            // Add dim behind the dialog
            setDimAmount(0.3f)
        }

        dialog.show()

        // Customize title and buttons
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(this, R.color.white))
        dialog.findViewById<TextView>(android.R.id.title)?.apply {
            setTextColor(ContextCompat.getColor(context, R.color.white))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        }
    }
    private fun passQuestionToChatFragment(question: String) {
        val chatFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? ChatFragment
        if (chatFragment != null) {
            // ChatFragment is already visible
            chatFragment.setQuestionText(question)
        } else {
            // Open new ChatFragment with the question
            val bundle = Bundle().apply {
                putString("prefilled_question", question)
            }
            val newChatFragment = ChatFragment().apply {
                arguments = bundle
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, newChatFragment)
                .addToBackStack(null)
                .commit()
            
            // Update UI visibility
            handleFragmentChanges()
        }
    }
    private fun showQuickWorkQuestions() {
        val dialog = AlertDialog.Builder(this, R.style.BlueDialogTheme)
            .setTitle("Quick Work Questions")
            .setItems(quickWorkQuestions.toTypedArray()) { _, which ->
                val selectedQuestion = quickWorkQuestions[which]
                passQuestionToChatFragment(selectedQuestion)
            }
            .setNegativeButton("Cancel", null)
            .create()

        // This ensures the shadow appears
        dialog.window?.apply {
            setBackgroundDrawableResource(R.drawable.dialog_background_blue)
            // Add dim behind the dialog
            setDimAmount(0.3f)
        }

        dialog.show()

        // Customize title and buttons
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(this, R.color.white))
        dialog.findViewById<TextView>(android.R.id.title)?.apply {
            setTextColor(ContextCompat.getColor(context, R.color.white))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        }
    }
    private val quickWorkQuestions = listOf(
        "✉️ Help me draft a professional email",
        "📊 Suggest improvements for this work presentation",
        "🗓️ Generate a meeting agenda for our project",
        "💌 Write a polite follow-up message to a client",
        "📋 Create a checklist for onboarding new employees",
        "📝 Summarize these meeting notes into key points",
        "😊 Help me respond to this customer complaint",
        "🎯 Generate ideas for our team building activity",
        "👔 Help me write a job description for a developer",
        "📈 Draft a project status update for stakeholders"
    )

    private fun applyCurrentTheme() {
        // Apply current theme from ThemeManager
        val currentTheme = themeManager.getCurrentTheme()
        
        // Apply theme to the fixed background view instead of scrollable content
        val aiThemeBackground = findViewById<ImageView>(R.id.ai_theme_background)
        aiThemeBackground?.let { view ->
            Log.d("MainActivity", "Applying theme: ${currentTheme.themeName} to background view")
            try {
                // Set a bright test color first to confirm the view is visible
                view.setBackgroundColor(Color.RED)
                // Then apply the theme
                view.setImageResource(currentTheme.drawableRes)
                view.alpha = 1.0f
                view.visibility = View.VISIBLE
                Log.d("MainActivity", "Theme applied successfully - drawable res: ${currentTheme.drawableRes}")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to apply theme", e)
                // Fallback to colored background
                view.setBackgroundColor(Color.MAGENTA)
            }
        } ?: Log.e("MainActivity", "ai_theme_background view not found!")
    }

    /**
     * Retrieve or create a unique identifier for this app installation. This ID
     * will be used for syncing chat history with the web app.
     */
    private fun getOrCreateUserId(): String {
        val prefs = getSharedPreferences(USER_PREFS, MODE_PRIVATE)
        var id = prefs.getString(USER_ID_KEY, null)
        if (id == null) {
            id = java.util.UUID.randomUUID().toString()
            prefs.edit().putString(USER_ID_KEY, id).apply()
        }
        return id
    }

    /**
     * Launches the web version of the app and passes along the local user id so
     * that conversations can be linked across platforms.
     */
    private fun openWebApp() {
        val uri = Uri.parse("${WEB_APP_BASE_URL}?user_id=${getOrCreateUserId()}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(intent)
    }
    
    /**
     * Configure action bar with AI robot icon instead of text
     */
    private fun setupAiRobotActionBar() {
        supportActionBar?.apply {
            // Hide the default title text
            setDisplayShowTitleEnabled(false)
            
            // Enable custom view
            setDisplayShowCustomEnabled(true)
            
            // Create custom view with AI robot icon
            val customView = layoutInflater.inflate(R.layout.custom_action_bar, null)
            val robotIcon = customView.findViewById<ImageView>(R.id.actionBarIcon)
            
            // Set the AI robot icon
            robotIcon.setImageResource(R.drawable.ai_robot_icon)
            robotIcon.setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.ai_robot_primary))
            
            // Set the custom view
            setCustomView(customView)
            
            // Set action bar background to AI robot theme
            setBackgroundDrawable(ContextCompat.getDrawable(this@MainActivity, R.color.ai_robot_background))
        }
    }

    // AI Feature Implementation Methods

    /**
     * Start Voice Chat Session - Advanced conversational AI with voice input/output
     */
    private fun startVoiceChatSessionGeneral() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chat_mode", "live_voice")
            putExtra("suggested_message", "Starting live voice conversation...")
            putExtra("feature_name", "Live Voice Chat")
            putExtra("auto_start_live_voice", true)
            putExtra("voice_agent_type", "general_assistant")
        }
        startActivity(intent)
    }

    /**
     * Start Document Intelligence Session - AI-powered document analysis and extraction
     */
    private fun startDocumentIntelligenceSession() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chat_mode", "document")
            putExtra("suggested_message", "Upload a document for AI analysis")
            putExtra("feature_name", "Document Intelligence")
            putExtra("enable_file_upload", true)
            putExtra("ai_specialty", "document_analysis")
        }
        startActivity(intent)
    }

    /**
     * Start Image Analysis Session - Capture and analyze images with AI
     */
    private fun startImageAnalysisSession() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chat_mode", "image_analysis")
            putExtra("suggested_message", "Capture or select an image for AI analysis")
            putExtra("feature_name", "Image Analysis")
            putExtra("enable_image_capture", true)
            putExtra("auto_show_image_picker", true)
            putExtra("ai_specialty", "image_analysis")
        }
        startActivity(intent)
    }

    /**
     * Start Homework Helper Session - Extract text from docs, images, and PDFs for homework assistance
     */
    private fun startHomeworkHelperSession() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chat_mode", "homework_helper")
            putExtra("suggested_message", "Upload homework documents or capture images to extract text and get AI assistance")
            putExtra("feature_name", "Homework Helper")
            putExtra("enable_file_upload", true)
            putExtra("auto_show_document_picker", true)
            putExtra("ai_specialty", "homework_assistance")
        }
        startActivity(intent)
    }

    /**
     * Start Advanced Image Generation Session - Create and transform images with GPT Image 1
     */
    private fun startAdvancedImageGeneration() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chat_mode", "image_generation")
            putExtra("suggested_message", "Describe the image you want me to create and I'll generate it for you")
            putExtra("feature_name", "AI Image Generator")
            putExtra("selected_model", "gpt-image-1")
            putExtra("enable_image_generation", true)
            putExtra("auto_select_model", true)
        }
        startActivity(intent)
    }

    /**
     * Start Email Assistant Session - Smart email composition and analysis
     */
    private fun startEmailAssistantSession() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chat_mode", "email")
            putExtra("suggested_message", "Help me write a professional email")
            putExtra("feature_name", "Email Assistant")
            putExtra("ai_specialty", "email_writing")
            putExtra("enable_templates", true)
        }
        startActivity(intent)
    }

    /**
     * Start Math Solver Session - Advanced mathematical problem solving
     */
    private fun startMathSolverSession() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chat_mode", "math")
            putExtra("suggested_message", "Solve this math problem step by step:")
            putExtra("feature_name", "Math Solver")
            putExtra("ai_specialty", "mathematics")
            putExtra("enable_latex", true)
            putExtra("enable_step_by_step", true)
        }
        startActivity(intent)
    }

    /**
     * Start Science Assistant Session - Scientific analysis and explanations
     */
    private fun startScienceAssistantSession() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chat_mode", "science")
            putExtra("suggested_message", "Explain this scientific concept:")
            putExtra("feature_name", "Science Assistant")
            putExtra("ai_specialty", "science")
            putExtra("enable_diagrams", true)
            putExtra("enable_experiments", true)
        }
        startActivity(intent)
    }

    /**
     * Show Image Generation Subscription Dialog - Premium features for image creation
     */
    private fun showImageGenerationSubscriptionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Premium Image Generation")
            .setMessage("Unlock advanced AI image generation with DALL-E 3:\n\n• High-quality image creation\n• Style transfer and editing\n• Unlimited generations\n• Commercial usage rights")
            .setPositiveButton("Upgrade Now") { dialog: DialogInterface, which: Int ->
                showSubscriptionOptions()
            }
            .setNegativeButton("Try Basic") { dialog: DialogInterface, which: Int ->
                // Offer basic image generation
                startBasicImageGeneration()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    /**
     * Start Basic Image Generation - Free tier with limited features
     */
    private fun startBasicImageGeneration() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chat_mode", "image_generation_basic")
            putExtra("suggested_message", "Describe a simple image (Basic tier)")
            putExtra("feature_name", "Basic Image Generator")
            putExtra("selected_model", "dall-e-2")
            putExtra("generation_limit", 3)
        }
        startActivity(intent)
    }

    /**
     * Handle category tab selection and UI updates
     */
    private fun selectCategoryTab(category: String) {
        // Reset all tabs to unselected state
        binding.tabAllTools.isSelected = false
        binding.tabCreative.isSelected = false
        binding.tabAcademic.isSelected = false
        binding.tabProductivity.isSelected = false

        // Update background and colors for unselected state
        binding.tabAllTools.setBackgroundResource(R.drawable.category_tab_inactive_background)
        binding.tabAllTools.setTextColor(ContextCompat.getColor(this, R.color.ocean_text_secondary))
        binding.tabCreative.setBackgroundResource(R.drawable.category_tab_inactive_background)
        binding.tabCreative.setTextColor(ContextCompat.getColor(this, R.color.ocean_text_secondary))
        binding.tabAcademic.setBackgroundResource(R.drawable.category_tab_inactive_background)
        binding.tabAcademic.setTextColor(ContextCompat.getColor(this, R.color.ocean_text_secondary))
        binding.tabProductivity.setBackgroundResource(R.drawable.category_tab_inactive_background)
        binding.tabProductivity.setTextColor(ContextCompat.getColor(this, R.color.ocean_text_secondary))

        // Select the clicked tab and update its appearance
        when (category) {
            "all_tools" -> {
                binding.tabAllTools.isSelected = true
                binding.tabAllTools.setBackgroundResource(R.drawable.category_tab_background)
                binding.tabAllTools.setTextColor(ContextCompat.getColor(this, R.color.ocean_text_dark))
                showAllTools()
            }
            "creative" -> {
                binding.tabCreative.isSelected = true
                binding.tabCreative.setBackgroundResource(R.drawable.category_tab_background)
                binding.tabCreative.setTextColor(ContextCompat.getColor(this, R.color.ocean_text_dark))
                showCreativeTools()
            }
            "academic" -> {
                binding.tabAcademic.isSelected = true
                binding.tabAcademic.setBackgroundResource(R.drawable.category_tab_background)
                binding.tabAcademic.setTextColor(ContextCompat.getColor(this, R.color.ocean_text_dark))
                showAcademicTools()
            }
            "productivity" -> {
                binding.tabProductivity.isSelected = true
                binding.tabProductivity.setBackgroundResource(R.drawable.category_tab_background)
                binding.tabProductivity.setTextColor(ContextCompat.getColor(this, R.color.ocean_text_dark))
                showProductivityTools()
            }
        }
    }

    /**
     * Show all available AI tools (default view)
     */
    private fun showAllTools() {
        // Ensure main content is visible (restore default view)
        showMainContent()
        // Hide any academic interface that might be showing
        hideAcademicInterface()
        
        // Hide all dynamic content to keep it clean like the demo
        hideVoiceCommandShortcuts()
        hideAIEducationalTools()
        
        // Show only the main 6 tools like in the demo
        binding.quickActionsContainer.visibility = View.VISIBLE
        binding.additionalButtonsLayout.visibility = View.VISIBLE
        
        showCustomToast("Showing Main Tools")
    }

    /**
     * Show voice command shortcuts in the All Tools section
     */
    private fun showVoiceCommandShortcuts() {
        // Create voice command shortcuts container
        hideVoiceCommandShortcuts() // Remove any existing shortcuts first
        
        val voiceShortcutsContainer = LinearLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
        }
        
        // Add header with back button and title
        val headerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val headerParams = layoutParams as LinearLayout.LayoutParams
            headerParams.setMargins(0, 0, 0, 16.dpToPx())
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Back button
        val backButton = TextView(this).apply {
            text = "← Back"
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_primary))
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.montserrat_semi_bold)
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.category_tab_background)
            setPadding(12.dpToPx(), 8.dpToPx(), 12.dpToPx(), 8.dpToPx())
            
            // Add click listener to go back to All Tools
            setOnClickListener {
                selectCategoryTab("all_tools")
            }
            
            // Add some styling
            val buttonParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            buttonParams.marginEnd = 16.dpToPx()
            layoutParams = buttonParams
        }

        // Voice Commands title
        val voiceTitle = TextView(this).apply {
            text = "⚡ Productivity Tools"
            textSize = 22f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_text_primary))
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.montserrat_semi_bold)
            val titleParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = titleParams
        }

        headerContainer.addView(backButton)
        headerContainer.addView(voiceTitle)
        voiceShortcutsContainer.addView(headerContainer)
        
        // Create voice command cards container (2x2 grid)
        val voiceCardsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // First row: Set Alarm + Send Email
        val firstRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        firstRow.addView(createVoiceCommandCard("⏰", "Set Alarm", "Voice alarm setup", "alarm"))
        firstRow.addView(createVoiceCommandCard("📧", "Send Email", "Voice email compose", "email"))
        
        // Second row: Set Reminder + Voice Chat
        val secondRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val rowParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            rowParams.setMargins(0, 16.dpToPx(), 0, 0)
            layoutParams = rowParams
        }
        
        secondRow.addView(createVoiceCommandCard("📅", "Set Reminder", "Voice calendar reminder", "reminder"))
        secondRow.addView(createVoiceCommandCard("🎙️", "Voice Assistant", "Full voice interaction", "voice_chat"))
        
        voiceCardsContainer.addView(firstRow)
        voiceCardsContainer.addView(secondRow)
        voiceShortcutsContainer.addView(voiceCardsContainer)
        
        // Add to root layout
        binding.rootLayout.addView(voiceShortcutsContainer)
        currentVoiceContainer = voiceShortcutsContainer
    }
    
    // Variable to track current voice shortcuts container
    private var currentVoiceContainer: LinearLayout? = null
    
    /**
     * Hide voice command shortcuts
     */
    private fun hideVoiceCommandShortcuts() {
        currentVoiceContainer?.let { container ->
            binding.rootLayout.removeView(container)
        }
        currentVoiceContainer = null
    }
    
    // Variable to track current AI educational tools container
    private var currentAIToolsContainer: LinearLayout? = null
    
    /**
     * Show AI-powered educational tools in the All Tools section
     */
    private fun showAIEducationalTools() {
        // Create AI educational tools container
        hideAIEducationalTools() // Remove any existing tools first
        
        val aiToolsContainer = LinearLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
        }
        
        // Add header with back button and title
        val headerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val headerParams = layoutParams as LinearLayout.LayoutParams
            headerParams.setMargins(0, 16.dpToPx(), 0, 16.dpToPx())
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Back button
        val backButton = TextView(this).apply {
            text = "← Back"
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_primary))
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.montserrat_semi_bold)
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.category_tab_background)
            setPadding(12.dpToPx(), 8.dpToPx(), 12.dpToPx(), 8.dpToPx())
            
            // Add click listener to go back to All Tools
            setOnClickListener {
                selectCategoryTab("all_tools")
            }
            
            // Add some styling
            val buttonParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            buttonParams.marginEnd = 16.dpToPx()
            layoutParams = buttonParams
        }

        // AI Educational Tools title
        val aiToolsTitle = TextView(this).apply {
            text = "🎨 Creative AI Tools"
            textSize = 22f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_text_primary))
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.montserrat_semi_bold)
            val titleParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = titleParams
        }

        headerContainer.addView(backButton)
        headerContainer.addView(aiToolsTitle)
        aiToolsContainer.addView(headerContainer)
        
        // Create AI educational tools cards container (2x2 grid)
        val aiToolsCardsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // First row: Explain Concept + Create Quiz
        val firstRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        firstRow.addView(createAIEducationalCard("💡", "Explain Concept", "AI-powered explanations", "explain"))
        firstRow.addView(createAIEducationalCard("🧠", "Create Quiz", "Generate practice tests", "quiz"))
        
        // Second row: Homework Help + Study Plan
        val secondRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val rowParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            rowParams.setMargins(0, 16.dpToPx(), 0, 0)
            layoutParams = rowParams
        }
        
        secondRow.addView(createAIEducationalCard("📚", "Homework Help", "Step-by-step guidance", "homework"))
        secondRow.addView(createAIEducationalCard("📅", "Study Plan", "Personalized learning", "study_plan"))
        
        // Third row: Research Assistant + Examples Generator
        val thirdRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val rowParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            rowParams.setMargins(0, 16.dpToPx(), 0, 0)
            layoutParams = rowParams
        }
        
        thirdRow.addView(createAIEducationalCard("🔍", "Research Assistant", "Find learning resources", "research"))
        thirdRow.addView(createAIEducationalCard("🌟", "Generate Examples", "Real-world applications", "examples"))
        
        aiToolsCardsContainer.addView(firstRow)
        aiToolsCardsContainer.addView(secondRow)
        aiToolsCardsContainer.addView(thirdRow)
        aiToolsContainer.addView(aiToolsCardsContainer)
        
        // Add to root layout
        binding.rootLayout.addView(aiToolsContainer)
        currentAIToolsContainer = aiToolsContainer
    }
    
    /**
     * Hide AI educational tools
     */
    private fun hideAIEducationalTools() {
        currentAIToolsContainer?.let { container ->
            binding.rootLayout.removeView(container)
        }
        currentAIToolsContainer = null
    }
    
    /**
     * Create an AI educational tool card
     */
    private fun createAIEducationalCard(icon: String, title: String, description: String, tool: String): MaterialCardView {
        return MaterialCardView(this).apply {
            val cardParams = LinearLayout.LayoutParams(
                0,
                140.dpToPx(),
                1f
            )
            cardParams.setMargins(if (tool == "explain" || tool == "homework" || tool == "research") 0 else 8.dpToPx(), 0, if (tool == "quiz" || tool == "study_plan" || tool == "examples") 0 else 8.dpToPx(), 0)
            layoutParams = cardParams
            cardElevation = 8.dpToPx().toFloat()
            radius = 16.dpToPx().toFloat()
            setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.transparent))
            isClickable = true
            isFocusable = true
            // Use proper attribute resolution for selectableItemBackground
            val typedArray = this@MainActivity.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            foreground = typedArray.getDrawable(0)
            typedArray.recycle()
            
            val cardContent = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.floating_card_background)
                
                val iconText = TextView(this@MainActivity).apply {
                    text = icon
                    textSize = 28f
                    gravity = Gravity.CENTER
                    val iconParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    iconParams.setMargins(0, 0, 0, 8.dpToPx())
                    layoutParams = iconParams
                }
                
                val titleText = TextView(this@MainActivity).apply {
                    text = title
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_text_primary))
                    typeface = ResourcesCompat.getFont(this@MainActivity, R.font.montserrat_semi_bold)
                    gravity = Gravity.CENTER
                    val titleParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    titleParams.setMargins(0, 0, 0, 4.dpToPx())
                    layoutParams = titleParams
                }
                
                val descText = TextView(this@MainActivity).apply {
                    text = description
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_text_secondary))
                    typeface = ResourcesCompat.getFont(this@MainActivity, R.font.montserrat_medium)
                    gravity = Gravity.CENTER
                    maxLines = 2
                }
                
                addView(iconText)
                addView(titleText)
                addView(descText)
            }
            
            addView(cardContent)
            
            // Set click listener based on tool type
            setOnClickListener {
                when (tool) {
                    "explain" -> startAIEducationalTool("explain_concept")
                    "quiz" -> startAIEducationalTool("create_quiz")
                    "homework" -> startAIEducationalTool("homework_help")
                    "study_plan" -> startAIEducationalTool("study_plan")
                    "research" -> startAIEducationalTool("research_assistant")
                    "examples" -> startAIEducationalTool("generate_examples")
                }
            }
        }
    }
    
    /**
     * Start AI Educational Tool
     */
    private fun startAIEducationalTool(toolType: String) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chat_mode", "ai_educational_tool")
            putExtra("tool_type", toolType)
            putExtra("enable_function_calling", true)
            putExtra("enable_web_search", true) // Enable web search for educational content
            
            when (toolType) {
                "explain_concept" -> {
                    putExtra("suggested_message", "💡 AI Concept Explainer - What concept would you like me to explain?")
                    putExtra("feature_name", "AI Concept Explainer")
                    putExtra("system_prompt", "You are an AI educational assistant specialized in explaining concepts clearly and engagingly. Use analogies, examples, and step-by-step breakdowns. Adapt your explanation style to the user's level.")
                }
                "create_quiz" -> {
                    putExtra("suggested_message", "🧠 AI Quiz Generator - What topic would you like me to create a quiz for?")
                    putExtra("feature_name", "AI Quiz Generator")
                    putExtra("system_prompt", "You are an AI quiz generator. Create engaging, educational quizzes with multiple choice, true/false, and short answer questions. Include explanations for correct answers.")
                }
                "homework_help" -> {
                    putExtra("suggested_message", "📚 AI Homework Helper - Share your homework question and I'll guide you through it!")
                    putExtra("feature_name", "AI Homework Helper")
                    putExtra("system_prompt", "You are an AI homework assistant. Provide step-by-step guidance without giving direct answers. Focus on teaching the process and helping students understand concepts.")
                }
                "study_plan" -> {
                    putExtra("suggested_message", "📅 AI Study Planner - Tell me what you want to learn and I'll create a personalized study plan!")
                    putExtra("feature_name", "AI Study Planner")
                    putExtra("system_prompt", "You are an AI study planning assistant. Create structured, achievable study plans with clear milestones, time management, and learning resources recommendations.")
                }
                "research_assistant" -> {
                    putExtra("suggested_message", "🔍 AI Research Assistant - What topic would you like me to help you research?")
                    putExtra("feature_name", "AI Research Assistant")
                    putExtra("system_prompt", "You are an AI research assistant. Help users find credible educational resources, summarize key information, and suggest further reading materials. Use web search to find current information.")
                }
                "generate_examples" -> {
                    putExtra("suggested_message", "🌟 AI Examples Generator - What concept would you like me to provide examples for?")
                    putExtra("feature_name", "AI Examples Generator")
                    putExtra("system_prompt", "You are an AI examples generator. Provide diverse, relevant, and practical examples that help illustrate concepts. Use real-world applications and varied contexts.")
                }
            }
        }
        startActivity(intent)
        
        val toolName = when (toolType) {
            "explain_concept" -> "Concept Explainer"
            "create_quiz" -> "Quiz Generator"
            "homework_help" -> "Homework Helper"
            "study_plan" -> "Study Planner"
            "research_assistant" -> "Research Assistant"
            "generate_examples" -> "Examples Generator"
            else -> "Educational Tool"
        }
        showCustomToast("🤖 Starting AI $toolName...")
    }
    
    /**
     * Create a voice command shortcut card
     */
    private fun createVoiceCommandCard(icon: String, title: String, description: String, command: String): MaterialCardView {
        return MaterialCardView(this).apply {
            val cardParams = LinearLayout.LayoutParams(
                0,
                120.dpToPx(),
                1f
            )
            cardParams.setMargins(if (command == "alarm" || command == "reminder") 0 else 8.dpToPx(), 0, if (command == "email" || command == "voice_chat") 0 else 8.dpToPx(), 0)
            layoutParams = cardParams
            cardElevation = 8.dpToPx().toFloat()
            radius = 16.dpToPx().toFloat()
            setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.transparent))
            isClickable = true
            isFocusable = true
            // Use proper attribute resolution for selectableItemBackground
            val typedArray = this@MainActivity.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            foreground = typedArray.getDrawable(0)
            typedArray.recycle()
            
            val cardContent = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.floating_card_background)
                
                val iconText = TextView(this@MainActivity).apply {
                    text = icon
                    textSize = 24f
                    gravity = Gravity.CENTER
                    val iconParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    iconParams.setMargins(0, 0, 0, 8.dpToPx())
                    layoutParams = iconParams
                }
                
                val titleText = TextView(this@MainActivity).apply {
                    text = title
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_text_primary))
                    typeface = ResourcesCompat.getFont(this@MainActivity, R.font.montserrat_semi_bold)
                    gravity = Gravity.CENTER
                    val titleParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    titleParams.setMargins(0, 0, 0, 4.dpToPx())
                    layoutParams = titleParams
                }
                
                val descText = TextView(this@MainActivity).apply {
                    text = description
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_text_secondary))
                    typeface = ResourcesCompat.getFont(this@MainActivity, R.font.montserrat_medium)
                    gravity = Gravity.CENTER
                    maxLines = 2
                }
                
                addView(iconText)
                addView(titleText)
                addView(descText)
            }
            
            addView(cardContent)
            
            // Set click listener based on command type
            setOnClickListener {
                when (command) {
                    "alarm" -> showVoiceAlarmShortcut()
                    "email" -> showVoiceEmailShortcut()
                    "reminder" -> showVoiceReminderShortcut()
                    "voice_chat" -> startVoiceChatSession()
                }
            }
        }
    }
    
    /**
     * Voice Alarm Shortcut - Quick alarm setup with AI enhancement
     */
    private fun showVoiceAlarmShortcut() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("🎤 AI-Enhanced Voice Alarm Setup")
            .setMessage("Choose your preferred method to set an alarm:")
            .setPositiveButton("Quick Setup (7 AM)") { dialog: DialogInterface, which: Int ->
                setQuickAlarm(7, 0, "Morning Alarm")
            }
            .setNeutralButton("Custom Setup") { dialog: DialogInterface, which: Int ->
                showCustomAlarmDialog()
            }
            .setNegativeButton("AI Voice Command") { dialog: DialogInterface, which: Int ->
                startAIEnhancedVoiceCommand("alarm")
            }
            .create()
        
        dialog.show()
    }
    
    /**
     * Voice Email Shortcut - Quick email compose with AI enhancement
     */
    private fun showVoiceEmailShortcut() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("📧 AI-Enhanced Email Assistant")
            .setMessage("Choose your preferred method to compose an email:")
            .setPositiveButton("Quick Email") { dialog: DialogInterface, which: Int ->
                showQuickEmailDialog()
            }
            .setNeutralButton("AI Email Helper") { dialog: DialogInterface, which: Int ->
                startAIEnhancedVoiceCommand("email")
            }
            .setNegativeButton("Voice Compose") { dialog: DialogInterface, which: Int ->
                startVoiceEmailCommand()
            }
            .create()
        
        dialog.show()
    }
    
    /**
     * Voice Reminder Shortcut - Quick reminder setup with AI enhancement
     */
    private fun showVoiceReminderShortcut() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("📅 AI-Enhanced Reminder Assistant")
            .setMessage("Choose your preferred method to set a reminder:")
            .setPositiveButton("Quick Reminder") { dialog: DialogInterface, which: Int ->
                showQuickReminderDialog()
            }
            .setNeutralButton("AI Reminder Helper") { dialog: DialogInterface, which: Int ->
                startAIEnhancedVoiceCommand("reminder")
            }
            .setNegativeButton("Voice Setup") { dialog: DialogInterface, which: Int ->
                startVoiceReminderCommand()
            }
            .create()
        
        dialog.show()
    }
    
    /**
     * Start Voice Chat Session - Direct voice interaction with AI enhancement
     */
    private fun startVoiceChatSession() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chat_mode", "ai_voice_assistant")
            putExtra("suggested_message", "🎤 AI-Enhanced Voice Assistant Ready - Ask me anything!")
            putExtra("feature_name", "AI Voice Assistant")
            putExtra("auto_start_voice", true) // Flag to auto-start voice recording
            putExtra("enable_function_calling", true) // Enable AI function calling
            putExtra("enable_web_search", true) // Enable web search
        }
        startActivity(intent)
        showCustomToast("🎤 Starting AI-Enhanced Voice Assistant...")
    }
    
    /**
     * Start AI-Enhanced Voice Command for specific functions
     */
    private fun startAIEnhancedVoiceCommand(commandType: String) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chat_mode", "ai_voice_command")
            putExtra("command_type", commandType)
            putExtra("enable_function_calling", true)
            putExtra("enable_web_search", false) // No web search for voice commands
            putExtra("auto_start_voice", true)
            
            when (commandType) {
                "alarm" -> {
                    putExtra("suggested_message", "🎤 AI Alarm Assistant - Tell me when you want to wake up and I'll help set the perfect alarm")
                    putExtra("feature_name", "AI Alarm Assistant")
                    putExtra("system_prompt", "You are an AI alarm assistant. Help users set alarms intelligently by understanding natural language requests like 'wake me up early tomorrow' or 'set an alarm for my meeting'. Consider time zones, work schedules, and sleep patterns.")
                }
                "email" -> {
                    putExtra("suggested_message", "📧 AI Email Assistant - Tell me who you want to email and what about, I'll help compose the perfect message")
                    putExtra("feature_name", "AI Email Assistant")  
                    putExtra("system_prompt", "You are an AI email assistant. Help users compose professional, clear, and effective emails. Ask for recipient, subject, and key points, then suggest well-structured email content with appropriate tone and formatting.")
                }
                "reminder" -> {
                    putExtra("suggested_message", "📅 AI Reminder Assistant - Tell me what you need to remember and when, I'll help organize it perfectly")
                    putExtra("feature_name", "AI Reminder Assistant")
                    putExtra("system_prompt", "You are an AI reminder assistant. Help users create smart reminders by understanding natural language like 'remind me to call mom tomorrow' or 'don't let me forget the presentation next week'. Suggest optimal timing and follow-up reminders.")
                }
            }
        }
        startActivity(intent)
        
        val commandName = when (commandType) {
            "alarm" -> "Alarm Assistant"
            "email" -> "Email Assistant" 
            "reminder" -> "Reminder Assistant"
            else -> "Voice Command"
        }
        showCustomToast("🤖 Starting AI-Enhanced $commandName...")
    }
    
    // ==================== VOICE COMMAND HELPER FUNCTIONS ====================
    
    /**
     * Set a quick alarm with predefined time
     */
    private fun setQuickAlarm(hour: Int, minute: Int, message: String) {
        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false) // Show clock app for confirmation
            }
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                showCustomToast("⏰ Setting alarm for $hour:${minute.toString().padStart(2, '0')}")
            } else {
                showCustomToast("❌ No clock app found")
            }
        } catch (e: Exception) {
            showCustomToast("❌ Error setting alarm: ${e.message}")
        }
    }
    
    /**
     * Show custom alarm setup dialog
     */
    private fun showCustomAlarmDialog() {
        val timePickerView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32.dpToPx(), 16.dpToPx(), 32.dpToPx(), 16.dpToPx())
        }
        
        val hourPicker = android.widget.NumberPicker(this).apply {
            minValue = 0
            maxValue = 23
            value = 7 // Default to 7 AM
        }
        
        val minutePicker = android.widget.NumberPicker(this).apply {
            minValue = 0
            maxValue = 59
            value = 0 // Default to :00
        }
        
        val hourLabel = TextView(this).apply {
            text = "Hour (24-hour format)"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_text_primary))
        }
        
        val minuteLabel = TextView(this).apply {
            text = "Minutes"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_text_primary))
        }
        
        timePickerView.addView(hourLabel)
        timePickerView.addView(hourPicker)
        timePickerView.addView(minuteLabel)
        timePickerView.addView(minutePicker)
        
        AlertDialog.Builder(this)
            .setTitle("🎤 Custom Alarm Setup")
            .setView(timePickerView)
            .setPositiveButton("Set Alarm") { dialog: DialogInterface, which: Int ->
                setQuickAlarm(hourPicker.value, minutePicker.value, "Custom Alarm")
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * Start voice command for alarm setting
     */
    private fun startVoiceAlarmCommand() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chat_mode", "voice_alarm")
            putExtra("suggested_message", "🎤 Say: 'Set alarm for 7 AM tomorrow' or similar")
            putExtra("feature_name", "Voice Alarm")
            putExtra("auto_start_voice", true)
            putExtra("voice_prompt", "Please tell me when you want to set the alarm. For example: 'Set alarm for 7 AM' or 'Wake me up at 6:30 PM'")
        }
        startActivity(intent)
    }
    
    /**
     * Show quick email dialog
     */
    private fun showQuickEmailDialog() {
        val emailView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32.dpToPx(), 16.dpToPx(), 32.dpToPx(), 16.dpToPx())
        }
        
        val recipientInput = EditText(this).apply {
            hint = "Recipient email address"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_text_primary))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_text_secondary))
        }
        
        val subjectInput = EditText(this).apply {
            hint = "Subject"
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_text_primary))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_text_secondary))
        }
        
        val bodyInput = EditText(this).apply {
            hint = "Message body"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_text_primary))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_text_secondary))
        }
        
        emailView.addView(recipientInput)
        emailView.addView(subjectInput)
        emailView.addView(bodyInput)
        
        AlertDialog.Builder(this)
            .setTitle("🎤 Quick Email Compose")
            .setView(emailView)
            .setPositiveButton("Send Email") { dialog: DialogInterface, which: Int ->
                val recipient = recipientInput.text.toString()
                val subject = subjectInput.text.toString()
                val body = bodyInput.text.toString()
                
                if (recipient.isNotEmpty()) {
                    sendQuickEmail(recipient, subject, body)
                } else {
                    showCustomToast("❌ Please enter recipient email")
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * Send quick email using system email app
     */
    private fun sendQuickEmail(recipient: String, subject: String, body: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$recipient")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(Intent.createChooser(intent, "Send email using:"))
                showCustomToast("📧 Opening email app...")
            } else {
                showCustomToast("❌ No email app found")
            }
        } catch (e: Exception) {
            showCustomToast("❌ Error sending email: ${e.message}")
        }
    }
    
    /**
     * Start voice command for email composition
     */
    private fun startVoiceEmailCommand() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chat_mode", "voice_email")
            putExtra("suggested_message", "🎤 Say: 'Send email to john@example.com with subject Meeting Tomorrow'")
            putExtra("feature_name", "Voice Email")
            putExtra("auto_start_voice", true)
            putExtra("voice_prompt", "Please tell me the email details. For example: 'Send email to john@example.com with subject Meeting and message Let's meet at 3 PM'")
        }
        startActivity(intent)
    }
    
    /**
     * Show quick reminder dialog
     */
    private fun showQuickReminderDialog() {
        val reminderView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32.dpToPx(), 16.dpToPx(), 32.dpToPx(), 16.dpToPx())
        }
        
        val titleInput = EditText(this).apply {
            hint = "Reminder title"
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_text_primary))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_text_secondary))
        }
        
        val timeInfo = TextView(this).apply {
            text = "Reminder will be set for 1 hour from now"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_text_secondary))
            textSize = 12f
        }
        
        reminderView.addView(titleInput)
        reminderView.addView(timeInfo)
        
        AlertDialog.Builder(this)
            .setTitle("🎤 Quick Reminder Setup")
            .setView(reminderView)
            .setPositiveButton("Set Reminder") { dialog: DialogInterface, which: Int ->
                val title = titleInput.text.toString()
                if (title.isNotEmpty()) {
                    setQuickReminder(title)
                } else {
                    showCustomToast("❌ Please enter reminder title")
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * Set a quick reminder for 1 hour from now
     */
    private fun setQuickReminder(title: String) {
        try {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.HOUR_OF_DAY, 1) // 1 hour from now
            
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.Events.DESCRIPTION, "Quick reminder set via AI Teacher")
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, calendar.timeInMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, calendar.timeInMillis + (30 * 60 * 1000)) // 30 minutes duration
            }
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                showCustomToast("📅 Setting reminder: $title")
            } else {
                showCustomToast("❌ No calendar app found")
            }
        } catch (e: Exception) {
            showCustomToast("❌ Error setting reminder: ${e.message}")
        }
    }
    
    /**
     * Start voice command for reminder setting
     */
    private fun startVoiceReminderCommand() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chat_mode", "voice_reminder")
            putExtra("suggested_message", "🎤 Say: 'Remind me to call mom at 3 PM tomorrow'")
            putExtra("feature_name", "Voice Reminder")
            putExtra("auto_start_voice", true)
            putExtra("voice_prompt", "Please tell me what to remind you about and when. For example: 'Remind me to call mom at 3 PM tomorrow' or 'Set reminder for meeting at 2 PM'")
        }
        startActivity(intent)
    }

    /**
     * Show creative AI tools like image generation, creative writing, etc.
     */
    private fun showCreativeTools() {
        // Ensure main content is visible
        showMainContent()
        hideAcademicInterface()
        hideVoiceCommandShortcuts() // Hide voice shortcuts
        
        // Show creative tools: AI Image Generator, Voice Chat + AI Educational Tools
        binding.quickActionsContainer.visibility = View.VISIBLE
        binding.additionalButtonsLayout.visibility = View.GONE // Hide Elite Tools for Creative filter
        
        // Show AI-powered educational/creative tools
        showAIEducationalTools()
        
        showCustomToast("Showing Creative Tools")
    }

    /**
     * Show academic tools with full subjects/chapters/topics hierarchy
     */
    private fun showAcademicTools() {
        // Hide main content to show academic interface
        hideVoiceCommandShortcuts()
        hideAIEducationalTools() // Hide AI educational tools
        
        // Hide the main tool containers
        binding.quickActionsContainer.visibility = View.GONE
        binding.additionalButtonsLayout.visibility = View.GONE
        
        // Show academic hierarchy interface when Academic tab is selected
        showAcademicHierarchy()
        
        showCustomToast("Showing Academic Tools")
    }

    /**
     * Display the academic subjects hierarchy interface
     * @param specificSubject Optional subject to show directly (e.g., "Maths", "Physics")
     */
    private fun showAcademicHierarchy(specificSubject: String? = null) {
        // Hide main content and show academic interface
        hideMainContent()
        if (specificSubject != null && subjects.containsKey(specificSubject)) {
            // Show chapters for the specific subject directly
            val chapters = subjects[specificSubject] ?: emptyMap()
            showChapters(specificSubject, chapters)
        } else {
            // Show all subjects
            showAcademicInterface()
        }
    }

    /**
     * Hide main content sections to show academic interface
     */
    private fun hideMainContent() {
        // Hide the main scroll view content
        binding.mainScrollView.visibility = View.GONE
    }

    /**
     * Show main content sections (restore normal view)
     */
    private fun showMainContent() {
        binding.mainScrollView.visibility = View.VISIBLE
    }

    /**
     * Create and show academic interface with subjects hierarchy
     */
    private fun showAcademicInterface() {
        // Create a new layout for academic hierarchy
        val academicContainer = LinearLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        // Add back button
        val backButton = Button(this).apply {
            text = "← Back to Main"
            setOnClickListener {
                hideAcademicInterface()
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 16.dpToPx()
            layoutParams = params
        }
        academicContainer.addView(backButton)

        // Add title
        val title = TextView(this).apply {
            text = "Academic Subjects"
            textSize = 24f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_primary))
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.montserrat_semi_bold)
            val titleParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            titleParams.bottomMargin = 24.dpToPx()
            layoutParams = titleParams
        }
        academicContainer.addView(title)

        // Add subjects
        subjects.keys.forEach { subject ->
            val subjectCard = createSubjectCard(subject)
            academicContainer.addView(subjectCard)
        }

        // Add the academic container to root layout
        binding.rootLayout.addView(academicContainer)
        academicContainer.id = View.generateViewId()
        
        // Store reference for later removal
        this.currentAcademicContainer = academicContainer
    }

    // Store reference to current academic container
    private var currentAcademicContainer: LinearLayout? = null

    /**
     * Hide academic interface and return to main content
     */
    private fun hideAcademicInterface() {
        currentAcademicContainer?.let { container ->
            binding.rootLayout.removeView(container)
        }
        currentAcademicContainer = null
        showMainContent()
    }

    /**
     * Create a card for each subject
     */
    private fun createSubjectCard(subject: String): MaterialCardView {
        return MaterialCardView(this).apply {
            val cardParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            cardParams.setMargins(0, 8.dpToPx(), 0, 8.dpToPx())
            layoutParams = cardParams
            cardElevation = 8.dpToPx().toFloat()
            radius = 16.dpToPx().toFloat()
            
            val cardContent = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20.dpToPx(), 20.dpToPx(), 20.dpToPx(), 20.dpToPx())
                
                val subjectName = TextView(this@MainActivity).apply {
                    text = subject
                    textSize = 18f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_primary))
                    typeface = ResourcesCompat.getFont(this@MainActivity, R.font.montserrat_semi_bold)
                }
                addView(subjectName)
            }
            addView(cardContent)
            
            setOnClickListener {
                val chapters = subjects[subject] ?: emptyMap()
                showChapters(subject, chapters)
            }
        }
    }

    /**
     * Show chapters for a selected subject
     */
    private fun showChapters(subject: String, chapters: Map<String, Map<String, List<String>>>) {
        // Remove current academic container
        currentAcademicContainer?.let { container ->
            binding.rootLayout.removeView(container)
        }

        // Create chapters interface
        val chaptersContainer = LinearLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        // Add back button
        val backButton = Button(this).apply {
            text = "← Back to Subjects"
            setOnClickListener {
                hideAcademicInterface()
                showAcademicInterface()
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 16.dpToPx()
            layoutParams = params
        }
        chaptersContainer.addView(backButton)

        // Add title
        val title = TextView(this).apply {
            text = "$subject - Chapters"
            textSize = 24f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_primary))
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.montserrat_semi_bold)
            val titleParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            titleParams.bottomMargin = 24.dpToPx()
            layoutParams = titleParams
        }
        chaptersContainer.addView(title)

        // Add chapters
        chapters.keys.forEach { chapter ->
            val chapterCard = createChapterCard(subject, chapter, chapters[chapter] ?: emptyMap())
            chaptersContainer.addView(chapterCard)
        }

        // Add the container to root layout
        binding.rootLayout.addView(chaptersContainer)
        chaptersContainer.id = View.generateViewId()
        currentAcademicContainer = chaptersContainer
    }

    /**
     * Create a card for each chapter
     */
    private fun createChapterCard(subject: String, chapter: String, topics: Map<String, List<String>>): MaterialCardView {
        return MaterialCardView(this).apply {
            val cardParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            cardParams.setMargins(0, 8.dpToPx(), 0, 8.dpToPx())
            layoutParams = cardParams
            cardElevation = 8.dpToPx().toFloat()
            radius = 16.dpToPx().toFloat()
            
            val cardContent = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20.dpToPx(), 20.dpToPx(), 20.dpToPx(), 20.dpToPx())
                
                val chapterName = TextView(this@MainActivity).apply {
                    text = chapter
                    textSize = 18f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_primary))
                    typeface = ResourcesCompat.getFont(this@MainActivity, R.font.montserrat_semi_bold)
                }
                addView(chapterName)
            }
            addView(cardContent)
            
            setOnClickListener {
                showTopics(subject, chapter, topics)
            }
        }
    }

    /**
     * Show topics for a selected chapter
     */
    private fun showTopics(subject: String, chapter: String, topics: Map<String, List<String>>) {
        // Remove current academic container
        currentAcademicContainer?.let { container ->
            binding.rootLayout.removeView(container)
        }

        // Create topics interface
        val topicsContainer = LinearLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        // Add back button
        val backButton = Button(this).apply {
            text = "← Back to Chapters"
            setOnClickListener {
                val chapters = subjects[subject] ?: emptyMap()
                showChapters(subject, chapters)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 16.dpToPx()
            layoutParams = params
        }
        topicsContainer.addView(backButton)

        // Add title
        val title = TextView(this).apply {
            text = "$subject - $chapter - Topics"
            textSize = 24f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_primary))
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.montserrat_semi_bold)
            val titleParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            titleParams.bottomMargin = 24.dpToPx()
            layoutParams = titleParams
        }
        topicsContainer.addView(title)

        // Add topics
        topics.keys.forEach { topic ->
            val topicCard = createTopicCard(subject, chapter, topic, topics[topic] ?: emptyList())
            topicsContainer.addView(topicCard)
        }

        // Add the container to root layout
        binding.rootLayout.addView(topicsContainer)
        topicsContainer.id = View.generateViewId()
        currentAcademicContainer = topicsContainer
    }

    /**
     * Create a card for each topic
     */
    private fun createTopicCard(subject: String, chapter: String, topic: String, subtopics: List<String>): MaterialCardView {
        return MaterialCardView(this).apply {
            val cardParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            cardParams.setMargins(0, 8.dpToPx(), 0, 8.dpToPx())
            layoutParams = cardParams
            cardElevation = 8.dpToPx().toFloat()
            radius = 16.dpToPx().toFloat()
            
            val cardContent = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20.dpToPx(), 20.dpToPx(), 20.dpToPx(), 20.dpToPx())
                
                val topicName = TextView(this@MainActivity).apply {
                    text = topic
                    textSize = 18f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_primary))
                    typeface = ResourcesCompat.getFont(this@MainActivity, R.font.montserrat_semi_bold)
                }
                addView(topicName)
            }
            addView(cardContent)
            
            setOnClickListener {
                showSubtopics(subject, chapter, topic, subtopics)
            }
        }
    }

    /**
     * Show subtopics and launch AI chat for learning
     */
    private fun showSubtopics(subject: String, chapter: String, topic: String, subtopics: List<String>) {
        // Remove current academic container
        currentAcademicContainer?.let { container ->
            binding.rootLayout.removeView(container)
        }

        // Create subtopics interface
        val subtopicsContainer = LinearLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        // Add back button
        val backButton = Button(this).apply {
            text = "← Back to Topics"
            setOnClickListener {
                val chapters = subjects[subject] ?: emptyMap()
                val topics = chapters[chapter] ?: emptyMap()
                showTopics(subject, chapter, topics)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 16.dpToPx()
            layoutParams = params
        }
        subtopicsContainer.addView(backButton)

        // Add title
        val title = TextView(this).apply {
            text = "$subject - $chapter - $topic"
            textSize = 24f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_primary))
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.montserrat_semi_bold)
            val titleParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            titleParams.bottomMargin = 24.dpToPx()
            layoutParams = titleParams
        }
        subtopicsContainer.addView(title)

        // Add subtopics
        subtopics.forEach { subtopic ->
            val subtopicCard = createSubtopicCard(subject, chapter, topic, subtopic)
            subtopicsContainer.addView(subtopicCard)
        }

        // Add the container to root layout
        binding.rootLayout.addView(subtopicsContainer)
        subtopicsContainer.id = View.generateViewId()
        currentAcademicContainer = subtopicsContainer
    }

    /**
     * Create a card for each subtopic - launches AI chat when clicked
     */
    private fun createSubtopicCard(subject: String, chapter: String, topic: String, subtopic: String): MaterialCardView {
        return MaterialCardView(this).apply {
            val cardParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            cardParams.setMargins(0, 8.dpToPx(), 0, 8.dpToPx())
            layoutParams = cardParams
            cardElevation = 8.dpToPx().toFloat()
            radius = 16.dpToPx().toFloat()
            
            val cardContent = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20.dpToPx(), 20.dpToPx(), 20.dpToPx(), 20.dpToPx())
                
                val subtopicName = TextView(this@MainActivity).apply {
                    text = subtopic
                    textSize = 18f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_primary))
                    typeface = ResourcesCompat.getFont(this@MainActivity, R.font.montserrat_semi_bold)
                }
                addView(subtopicName)
                
                // Add AI chat indicator
                val aiIndicator = TextView(this@MainActivity).apply {
                    text = "🤖 Learn with AI"
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ocean_accent_coral))
                    val indicatorParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    indicatorParams.topMargin = 8.dpToPx()
                    layoutParams = indicatorParams
                }
                addView(aiIndicator)
            }
            addView(cardContent)
            
            setOnClickListener {
                // Launch AI chat for this specific subtopic
                launchAIChat(subject, chapter, topic, subtopic)
            }
        }
    }

    /**
     * Launch AI chat for specific academic content
     */
    private fun launchAIChat(subject: String, chapter: String, topic: String, subtopic: String) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chat_mode", "academic_learning")
            putExtra("suggested_message", "Teach me about $subtopic in $topic ($subject - $chapter)")
            putExtra("feature_name", "Academic Learning: $subtopic")
            putExtra("ai_specialty", "education")
            putExtra("subject", subject)
            putExtra("chapter", chapter)
            putExtra("topic", topic)
            putExtra("subtopic", subtopic)
        }
        startActivity(intent)
    }

    /**
     * Show productivity AI tools like email, documents, scheduling, etc.
     */
    private fun showProductivityTools() {
        // Ensure main content is visible
        showMainContent()
        hideAcademicInterface()
        hideAIEducationalTools() // Hide AI educational tools
        
        // Show productivity tools: Text Extractor, Email Assistant + Voice Commands
        binding.quickActionsContainer.visibility = View.GONE // Hide Quick Actions for Productivity filter
        binding.additionalButtonsLayout.visibility = View.VISIBLE
        
        // Show voice command shortcuts for productivity
        showVoiceCommandShortcuts()
        
        showCustomToast("Showing Productivity Tools")
    }

    /**
     * Show hamburger menu with important navigation options
     */
    private fun showHamburgerMenu() {
        val popupMenu = PopupMenu(this, binding.hamburgerMenu)
        popupMenu.menuInflater.inflate(R.menu.main_menu, popupMenu.menu)
        
        // Update profile menu item based on authentication status
        val profileMenuItem = popupMenu.menu.findItem(R.id.action_profile)
        lifecycleScope.launch {
            try {
                val isLoggedIn = firebaseAuthService.isSignedIn()
                profileMenuItem?.title = if (isLoggedIn) "👤 Profile" else "🔐 Sign In"
            } catch (e: Exception) {
                Log.e("MainActivity", "Error checking auth status for menu", e)
                profileMenuItem?.title = "👤 Profile"
            }
        }

        popupMenu.setOnMenuItemClickListener { menuItem ->
            handleMenuItemClick(menuItem)
        }

        popupMenu.show()
    }

    /**
     * Show quick actions menu from floating action button
     */
    private fun showQuickActionsMenu() {
        val options = arrayOf(
            "🤖 New AI Chat",
            "🎨 Generate Image", 
            "🎤 Voice Chat",
            "📄 Extract Text",
            "✉️ Email Assistant",
            "🧮 Math Helper",
            "🔬 Science Helper"
        )

        val builder = AlertDialog.Builder(this)
        builder.setTitle("⚡ Quick Actions")
        builder.setItems(options) { dialog: DialogInterface, which: Int ->
            when (which) {
                0 -> {
                    // New AI Chat
                    openChatActivityWithModel(currentModel)
                }
                1 -> {
                    // Generate Image
                    if (isUserSubscribed()) {
                        openChatActivityWithModel("dall-e-3")
                    } else {
                        //()
                    }
                }
                2 -> {
                    // Voice Chat
                    openVoiceChat()
                }
                3 -> {
                    // Extract Text
                    openTextExtractor()
                }
                4 -> {
                    // Email Assistant
                    openEmailAssistant()
                }
                5 -> {
                    // Math Helper
                    openChatActivityWithModel("gpt-4", "I need help with mathematics. Please be ready to solve math problems and explain concepts clearly.")
                }
                6 -> {
                    // Science Helper
                    openChatActivityWithModel("gpt-4", "I need help with science. Please be ready to explain scientific concepts and help with science problems.")
                }
            }
        }
        builder.show()
    }

    /**
     * Handle menu item clicks from hamburger menu
     */
    private fun handleMenuItemClick(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_new_conversation -> {
                startActivity(Intent(this, ChatActivity::class.java))
                true
            }
            R.id.action_change_background_color -> {
                showAIThemeSelectionDialog()
                true
            }
            R.id.action_profile -> {
                openProfileActivity()
                true
            }
            R.id.action_switch_to_webapp -> {
                openWebApp()
                true
            }
            R.id.action_redeem_promo -> {
                showPromoCodeDialog()
                true
            }
            else -> false
        }
    }

    /**
     * Open profile activity with authentication handling
     */
    private fun openProfileActivity() {
        val intent = Intent(this, ProfileActivity::class.java)
        startActivity(intent)
    }

    /**
     * Show promo code dialog for redeeming promotional codes
     */
    /*private fun showPromoCodeDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Redeem Promo Code")
        
        val input = EditText(this)
        input.hint = "Enter promo code"
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)

        builder.setPositiveButton("Redeem") { dialog: DialogInterface, which: Int ->
            val promoCode = input.text.toString().trim()
            if (promoCode.isNotEmpty()) {
                handlePromoCode(promoCode)
            } else {
                showCustomToast("Please enter a valid promo code")
            }
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }*/

    /**
     * Handle promo code redemption
     */
    private fun handlePromoCode(promoCode: String) {
        // Add promo code handling logic here
        when (promoCode.uppercase()) {
            "WELCOME2024" -> {
                showCustomToast("Welcome promo activated! Enjoy your benefits.")
                // Grant welcome benefits
            }
            "PREMIUM30" -> {
                showCustomToast("30-day premium trial activated!")
                // Grant premium trial
            }
            else -> {
                showCustomToast("Invalid promo code. Please try again.")
            }
        }
    }

    // Badge functionality disabled - stub methods to prevent crashes
    private fun updateBadgeAndText() {
        // Badge functionality disabled for cleaner design
    }

    /*private fun updateSubscriptionTimer() {
        // Badge functionality disabled for cleaner design
    }

    private fun updateBadgeForActivePlan(planType: String, daysRemaining: Int) {
        // Badge functionality disabled for cleaner design
    }*/

    private fun updateBadgeForFreeTier() {
        // Badge functionality disabled for cleaner design
    }

    /**
     * Open voice chat functionality
     */
    private fun openVoiceChat() {
        Intent(this, ChatActivity::class.java).apply {
            putExtra("selected_model", currentModel)
            putExtra("open_voice_chat", true)
            putExtra("is_ad_free", sharedPreferences.getBoolean(keyAdFree, false))
            putExtra("expiration_time", sharedPreferences.getLong(expirationTimeKey, 0))
        }.also { startActivity(it) }
    }

    /**
     * Open text extractor functionality
     */
    private fun openTextExtractor() {
        Intent(this, ChatActivity::class.java).apply {
            putExtra("selected_model", currentModel)
            putExtra("initial_message", "I need help extracting text from images or documents. Please help me with OCR or text extraction tasks.")
            putExtra("is_ad_free", sharedPreferences.getBoolean(keyAdFree, false))
            putExtra("expiration_time", sharedPreferences.getLong(expirationTimeKey, 0))
        }.also { startActivity(it) }
    }

    /**
     * Open email assistant functionality
     */
    private fun openEmailAssistant() {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("show_email_fragment", true)
        startActivity(intent)
    }
}