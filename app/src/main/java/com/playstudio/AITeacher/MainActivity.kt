package com.playstudio.aiteacher

import android.Manifest
import android.animation.*
import android.app.*
import android.content.ActivityNotFoundException
import android.content.Context
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
import androidx.activity.result.contract.ActivityResultContracts
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
import android.text.TextUtils
import android.util.TypedValue
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

class MainActivity : AppCompatActivity(), PurchasesUpdatedListener, ChatFragment.OnSubscriptionClickListener {


    // Add these at class level

    private val skuDetailsList = mutableListOf<SkuDetails>()
    private lateinit var subscriptionStatusText: TextView
    private lateinit var subscriptionTimer: TextView
    private var timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    // Initialize EmailProviderHelper correctly
    private val emailProviderHelper by lazy { EmailProviderHelper(this) }
    // private val EMAIL_PROVIDER_REQUEST = 1004 // This seems unused, EmailProviderHelper.EMAIL_PICK_REQUEST is used

    private lateinit var btnExtractText: Button
    private lateinit var remoteConfig: FirebaseRemoteConfig
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

    private var versionTapCount = 0
    private var lastVersionTapTime = 0L
    // Declare LottieAnimationView and overlay View references
    private lateinit var question1AnimationView: LottieAnimationView
    private lateinit var question1Overlay: View
    private lateinit var question2AnimationView: LottieAnimationView
    private lateinit var question2Overlay: View
    private lateinit var question3AnimationView: LottieAnimationView
    private lateinit var question3Overlay: View
    private lateinit var question4AnimationView: LottieAnimationView
    private lateinit var question4Overlay: View
    private lateinit var lastQuestion1AnimationView: LottieAnimationView
    private lateinit var lastQuestion1Overlay: View
    private lateinit var lastQuestion2AnimationView: LottieAnimationView
    private lateinit var lastQuestion2Overlay: View

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

    // Map to store the relationship between LottieAnimationView IDs and questions
    private val lottieViewToQuestionMap = mutableMapOf<Int, String>()

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




        // Initialize Data Binding
        binding = ActivityMainBinding.inflate(layoutInflater) // Replace YourLayoutBinding
        setContentView(binding.root)

        // Remove this line:
        // subscriptionTimer = findViewById(R.id.subscriptionTimer)

        // And use the binding reference instead:
        binding.subscriptionTimer.visibility = View.VISIBLE // or View.GONE

        val badgeImageView = binding.badgeImageView
        val badgeTextView = binding.badgeTextView
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

        // Set the custom action bar background
        supportActionBar?.setBackgroundDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.action_bar_background
            )
        )
        // Set up custom action bar with clickable title
        supportActionBar?.setDisplayShowCustomEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val actionBarLayout = layoutInflater.inflate(R.layout.custom_action_bar, null)
        val actionBarTitle = actionBarLayout.findViewById<TextView>(R.id.action_bar_title)
        actionBarTitle.text = ""

        supportActionBar?.customView = actionBarLayout

        // Set up promo code detection
        setupPromoCodeDetection(actionBarTitle)
        binding.buttonNew2.setOnClickListener {
            startSpeechToText()
        }

        // Add this after initializing other buttons
        findViewById<Button>(R.id.jobQuestionsButton).setOnClickListener {
            showJobImpactQuestions() // This is where we call the function
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

            findViewById<Button>(R.id.quickWorkButton).setOnClickListener {
                showQuickWorkQuestions()
            }
            binding.buttonNew1.setOnClickListener {
                Log.d("ButtonTest", "Button clicked - attempting to open file picker")
                if (checkAndRequestPermissions()) {
                    openFilePicker()
                }
            }
            binding.buttonNew3.setOnClickListener {
                // Check subscription first
                if (sharedPreferences.getBoolean(keyAdFree, false) &&
                    System.currentTimeMillis() < sharedPreferences.getLong(expirationTimeKey, 0)) {
                    // User is subscribed - switch to DALL-E 3
                    openChatActivityWithModel("dall-e-3")
                } else {
                    // Show subscription required dialog
                    showDalle3SubscriptionRequiredDialog()
                }
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
            findViewById<ImageButton>(R.id.btnExtractEmail).setOnClickListener {
                if (emailProviderHelper.hasEmailPermissions()) {
                    val accounts = emailProviderHelper.getAvailableEmailAccounts()
                    if (accounts.isNotEmpty()) {
                        showEmailAccountPicker(accounts)
                    } else {
                        openGenericEmailPicker() // This will call startActivityForResult
                    }
                } else {
                    requestEmailPermissions() // This is MainActivity's existing method, will be adapted
                }
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

            // Show the "Thank You for Downloading" dialog if it hasn't been shown before
            if (!isThankYouDialogShown()) {
                showThankYouDialog()
                setThankYouDialogShown(true)
            }
            // Add this to your onCreate() after setContentView()
            subscriptionStatusText = findViewById(R.id.subscriptionStatusText)


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

        // Handle intent action for subscription purchase
        handleIntentAction(intent)


    }

    private fun showDalle3SubscriptionRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("Premium Feature Required")
            .setMessage("DALL-E 3 image generation requires a premium subscription. Subscribe now to unlock this feature!")
            .setPositiveButton("Subscribe") { _, _ ->
                showSubscriptionOptions()
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

        // Show subjects container
        findViewById<HorizontalScrollView>(R.id.subjectsScrollView).visibility = View.VISIBLE

        val subjectsLayout = findViewById<LinearLayout>(R.id.subjectsLayout)
        subjectsLayout.removeAllViews()

        // Create subject cards
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

            subjectsLayout.addView(subjectCard)
        }
    }


    private fun setupChapters(subject: String, chapters: Map<String, Map<String, List<String>>>) {
        // Hide subjects and other containers
        findViewById<HorizontalScrollView>(R.id.subjectsScrollView).visibility = View.GONE
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
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx()
            }
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
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx()
            }
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
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx()
            }
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
        intent?.getStringExtra("action")?.let { action ->
            if (action == "buy_subscription") {
                showSubscriptionOptions()
            }
        }
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
            binding.messageEditText.setOnTouchListener { _, event ->
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
        binding.messageEditText.setOnTouchListener { _, event ->
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
        binding.buttonNew3.setOnClickListener {
            if (isUserSubscribed()) {
                openChatActivityWithModel("dall-e-3")
            } else {
                showDalle3SubscriptionDialog()
            }
        }
    }




    private fun isUserSubscribed(): Boolean {
        val currentTime = System.currentTimeMillis()
        return sharedPreferences.getBoolean(keyAdFree, false) &&
                currentTime < sharedPreferences.getLong(expirationTimeKey, 0)
    }

    // In your Activity (not Fragment):
    private fun showDalle3SubscriptionDialog() {
        val dialog = Dialog(this).apply {  // 'this' refers to Activity context
            setContentView(R.layout.dialog_dalle_subscription)
            window?.setBackgroundDrawableResource(android.R.color.transparent)

            setCancelable(true)
            setCanceledOnTouchOutside(true)

            // Configure benefits
            val benefitsLayout = findViewById<LinearLayout>(R.id.benefitsLayout)
            listOf(
                "Generate high-resolution AI images (1024x1024)",
                "Create unlimited DALL-E 3 artwork",
                "Priority generation queue access",
            ).forEach { benefit ->
                TextView(this@MainActivity).apply {  // Use activity context
                    text = "• $benefit"
                    setTextColor(ContextCompat.getColor(context, R.color.white))
                    textSize = 16f
                    typeface = ResourcesCompat.getFont(context, R.font.montserrat_medium)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = resources.getDimensionPixelSize(R.dimen.benefit_item_margin) // Define in dimens.xml
                    }
                    benefitsLayout.addView(this)
                }
            }

            findViewById<Button>(R.id.btnBuy).setOnClickListener {
                showSubscriptionOptions()
                dismiss()
            }

            findViewById<TextView>(R.id.btnClose).setOnClickListener {
                dismiss()
            }
        }
        dialog.show()
    }


    private fun openChatActivityWithModel(model: String) {
        Intent(this, ChatActivity::class.java).apply {
            putExtra("selected_model", model)
            putExtra("is_ad_free", sharedPreferences.getBoolean(keyAdFree, false))
            putExtra("expiration_time", sharedPreferences.getLong(expirationTimeKey, 0))
        }.also { startActivity(it) }
    }









    private fun handleFragmentChanges() {
        val actionBarTitle: TextView? = supportActionBar?.customView?.findViewById(R.id.action_bar_title)
        val leftTitleContainer: RelativeLayout? = supportActionBar?.customView?.findViewById(R.id.left_title_container)

        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

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


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.let {
            menuInflater.inflate(R.menu.main_menu, it)

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
                showColorPickerDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
                        updateSubscriptionTimer() // Start the timer
                    }

                    "monthly_subscription" -> {
                        showCustomToast("Monthly subscription purchased")
                        setSubscriptionTypeAndBadge("silver", "Pro")
                        setAdFree(true)
                        saveSubscriptionExpiration(System.currentTimeMillis() + 30 * 24 * 60 * 60 * 1000L) // 1 month
                        updateChatFragmentSubscriptionStatus()
                        updateSubscriptionTimer() // Start the timer
                    }

                    "yearly_subscription" -> {
                        showCustomToast("Yearly subscription purchased")
                        setSubscriptionTypeAndBadge("gold", "Pro")
                        setAdFree(true)
                        saveSubscriptionExpiration(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L) // 1 year
                        updateChatFragmentSubscriptionStatus()
                        updateSubscriptionTimer() // Start the timer
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

    private fun setSubscriptionTypeAndBadge(badge: String, text: String) {
        sharedPreferences.edit().apply {
            putString(subscriptionTypeKey, badge)
            apply()
        }

        updateBadgeAndText()
    }

    private fun updateBadgeAndText() {
        val subscriptionType = sharedPreferences.getString(subscriptionTypeKey, null)
        val expirationTime = sharedPreferences.getLong(expirationTimeKey, 0)
        val currentTime = System.currentTimeMillis()

        if (currentTime < expirationTime) {
            // User is subscribed - premium state
            when (subscriptionType) {
                "bronze" -> {
                    binding.badgeImageView.setImageResource(R.drawable.bronze_badge)
                    binding.badgeTextView.text = "Pro"
                    binding.subscriptionStatusText.text = "Premium Active\nBronze Tier Benefits"
                    binding.subscriptionStatusText.setTextColor(ContextCompat.getColor(this, R.color.green))
                }
                "silver" -> {
                    binding.badgeImageView.setImageResource(R.drawable.silver_badge)
                    binding.badgeTextView.text = "Pro"
                    binding.subscriptionStatusText.text = "Premium Active\nSilver Tier Benefits"
                    binding.subscriptionStatusText.setTextColor(ContextCompat.getColor(this, R.color.green))
                }
                "gold" -> {
                    binding.badgeImageView.setImageResource(R.drawable.gold_badge)
                    binding.badgeTextView.text = "Pro"
                    binding.subscriptionStatusText.text = "Premium Active\nGold Tier Benefits"
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
            updateSubscriptionTimer()
        } else {
            // Free tier state
            binding.badgeImageView.setImageResource(R.drawable.bronze_badge)
            binding.badgeTextView.text = "Light"
            binding.subscriptionStatusText.text = "Upgrade for:\n• No ads\n• Better AI models\n• Image generation"
            binding.subscriptionStatusText.setTextColor(ContextCompat.getColor(this, R.color.red))
            binding.subscriptionTimer.visibility = View.GONE
        }
    }
    private fun saveSubscriptionExpiration(expirationTime: Long) {
        sharedPreferences.edit().putLong(expirationTimeKey, expirationTime).apply()
        updateChatFragmentSubscriptionStatus()
    }


    private fun updateChatFragmentSubscriptionStatus() {
        // Call this function to notify ChatFragment of the new subscription status
        val isAdFree = sharedPreferences.getBoolean(keyAdFree, false)
        val expirationTime = sharedPreferences.getLong(expirationTimeKey, 0)
        subscriptionViewModel.updateSubscriptionStatus(isAdFree, expirationTime)
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


    private fun checkSubscriptionStatus() {
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
                            updateSubscriptionTimer() // Add this line
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
            updateBadgeAndText() // Ensure the badge and text are updated
        }
    }





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
            .setPositiveButton("Apply") { _, _ ->
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

            binding.messageEditText.setTextColor(textColor)

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
        checkSubscriptionStatus() // Check subscription status on resume
        updateBadgeAndText() // Update badge and text on resume
        // Cancel existing reminders and schedule new one with current time
        cancelReminder()
        updateLastInteractionTime()
        scheduleReminder() // Add this line to reschedule
        updateSubscriptionTimer() // Add this line
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

    private fun showSubscriptionOptions() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_subscription, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Add explanation text
        val explanationTextView = TextView(this).apply {
            text = "Limited time offers! Your plan won't auto-renew. manually renew anytime."
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.black))
            setPadding(16, 16, 16, 16)
            textSize = 14f
        }

        val layout = dialogView.findViewById<LinearLayout>(R.id.dialogLayout)
        layout.addView(explanationTextView, 0)

        // Get references to price views
        val weeklyPrice = dialogView.findViewById<TextView>(R.id.weeklyPrice)
        val weeklyOriginalPrice = dialogView.findViewById<TextView>(R.id.weeklyOriginalPrice)
        val monthlyPrice = dialogView.findViewById<TextView>(R.id.monthlyPrice)
        val monthlyOriginalPrice = dialogView.findViewById<TextView>(R.id.monthlyOriginalPrice)
        val yearlyPrice = dialogView.findViewById<TextView>(R.id.yearlyPrice)
        val yearlyOriginalPrice = dialogView.findViewById<TextView>(R.id.yearlyOriginalPrice)

        // Modified price display in showSubscriptionOptions()
        skuDetailsList.forEach { skuDetails ->
            when (skuDetails.sku) {
                "subscription_7days" -> {
                    weeklyPrice.text = "${skuDetails.price}/week (50% OFF)"
                    weeklyOriginalPrice.text = "Was: ${calculateOriginalPrice(skuDetails.price, 0.5)}/week"
                }
                "monthly_subscription" -> {
                    monthlyPrice.text = "${skuDetails.price}/month (65% OFF)"
                    monthlyOriginalPrice.text = "Was: ${calculateOriginalPrice(skuDetails.price, 0.65)}/month"
                }
                "yearly_subscription" -> {
                    yearlyPrice.text = "${skuDetails.price}/year (70% OFF)"
                    yearlyOriginalPrice.text = "Was: ${calculateOriginalPrice(skuDetails.price, 0.7)}/year"
                }
            }
        }


        // Apply strikethrough
        applyStrikethrough(weeklyOriginalPrice, weeklyOriginalPrice.text.toString())
        applyStrikethrough(monthlyOriginalPrice, monthlyOriginalPrice.text.toString())
        applyStrikethrough(yearlyOriginalPrice, yearlyOriginalPrice.text.toString())

        // Highlight the yearly plan as best value
        val yearlyPlanContainer = dialogView.findViewById<View>(R.id.yearlySubscription)
        yearlyPlanContainer.setBackgroundColor(ContextCompat.getColor(this, R.color.best_value_background))
        val bestValueTextView = TextView(this).apply {
            text = "BEST VALUE"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.green))
            textSize = 12f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
        }
        (yearlyPlanContainer as? ViewGroup)?.addView(bestValueTextView)

        // Set click listeners
        dialogView.findViewById<View>(R.id.weeklySubscription).setOnClickListener {
            startPurchaseFlow("subscription_7days")
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.monthlySubscription).setOnClickListener {
            startPurchaseFlow("monthly_subscription")
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.yearlySubscription).setOnClickListener {
            startPurchaseFlow("yearly_subscription")
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun calculateOriginalPrice(currentPrice: Double, discount: Double): String {
        val originalPrice = currentPrice / (1 - discount)
        val format = NumberFormat.getCurrencyInstance()
        format.maximumFractionDigits = 2
        format.currency = Currency.getInstance("USD") // Adjust as needed
        return format.format(originalPrice)
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

        // Rest of your existing dialog setup...
        val weeklySubscription = dialogView.findViewById<View>(R.id.weeklySubscription)
        val monthlySubscription = dialogView.findViewById<View>(R.id.monthlySubscription)
        val yearlySubscription = dialogView.findViewById<View>(R.id.yearlySubscription)
        val btnBuy = dialogView.findViewById<Button>(R.id.btnBuy)
        val btnClose = dialogView.findViewById<TextView>(R.id.btnClose)

        var selectedSubscription: String? = null

        // Subscription option click listeners
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
        }

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
        weeklySubscription.performClick()

        dialog.show()
    }

    // Helper function to update subscription UI state
    private fun updateSubscriptionUI(
        selectedView: View,
        unselectedView1: View,
        unselectedView2: View
    ) {
        selectedView.background = ContextCompat.getDrawable(this, R.drawable.subscription_option_selected)
        unselectedView1.background = ContextCompat.getDrawable(this, R.drawable.subscription_option_unselected)
        unselectedView2.background = ContextCompat.getDrawable(this, R.drawable.subscription_option_unselected)
    }

    // Helper function to update price display
    private fun updatePriceDisplay(dialogView: View) {
        val weeklyPrice = dialogView.findViewById<TextView>(R.id.weeklyPrice)
        val weeklyOriginalPrice = dialogView.findViewById<TextView>(R.id.weeklyOriginalPrice)
        val monthlyPrice = dialogView.findViewById<TextView>(R.id.monthlyPrice)
        val monthlyOriginalPrice = dialogView.findViewById<TextView>(R.id.monthlyOriginalPrice)
        val yearlyPrice = dialogView.findViewById<TextView>(R.id.yearlyPrice)
        val yearlyOriginalPrice = dialogView.findViewById<TextView>(R.id.yearlyOriginalPrice)

        skuDetailsList.forEach { skuDetails ->
            when (skuDetails.sku) {
                "subscription_7days" -> {
                    weeklyPrice.text = skuDetails.price
                    weeklyOriginalPrice.text = "Original: ${calculateOriginalPrice(skuDetails.price, 0.5)}"
                }
                "monthly_subscription" -> {
                    monthlyPrice.text = skuDetails.price
                    monthlyOriginalPrice.text = "Original: ${calculateOriginalPrice(skuDetails.price, 0.65)}"
                }
                "yearly_subscription" -> {
                    yearlyPrice.text = skuDetails.price
                    yearlyOriginalPrice.text = "Original: ${calculateOriginalPrice(skuDetails.price, 0.7)}"
                }
            }
        }

        applyStrikethrough(weeklyOriginalPrice, weeklyOriginalPrice.text.toString())
        applyStrikethrough(monthlyOriginalPrice, monthlyOriginalPrice.text.toString())
        applyStrikethrough(yearlyOriginalPrice, yearlyOriginalPrice.text.toString())
    }

    // Helper function to check first launch
    /*private fun isFirstLaunch(): Boolean {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("is_first_launch", true)
        if (isFirstLaunch) {
            prefs.edit().putBoolean("is_first_launch", false).apply()
        }
        return isFirstLaunch
    }*/


    private fun querySkuDetails() {
        val skuList = listOf(
            "subscription_7days",
            "monthly_subscription",
            "yearly_subscription"
        )

        val params = SkuDetailsParams.newBuilder()
            .setSkusList(skuList)
            .setType(BillingClient.SkuType.SUBS)
            .build()

        billingClient.querySkuDetailsAsync(params) { billingResult, skuDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
                this.skuDetailsList.clear()
                this.skuDetailsList.addAll(skuDetailsList)

                // Refresh UI if dialog is showing
                refreshSubscriptionDialogs()
            }
        }
    }
    // Helper function to calculate original price
    private fun calculateOriginalPrice(price: String, discount: Double): String {
        // Extract numeric value from price string (e.g., "$5.99" -> 5.99)
        val numericValue = price.replace("[^\\d.]".toRegex(), "").toDouble()
        val originalPrice = numericValue / (1 - discount)

        val format = NumberFormat.getCurrencyInstance()
        format.maximumFractionDigits = 2
        return format.format(originalPrice)
    }
    private fun refreshSubscriptionDialogs() {
        // Implement if you need to refresh dialogs that are already showing
    }

    private fun applyStrikethrough(textView: TextView, text: String) {
        val spannableString = SpannableString(text)
        spannableString.setSpan(StrikethroughSpan(), 0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        textView.text = spannableString
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



    private fun updateSubscriptionTimer() {
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
    }



    private fun updateTimerText(expirationTime: Long) {
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
            updateBadgeAndText()

            // Show what user is missing
            binding.subscriptionStatusText.text = "You're missing:\n- Ad-free experience\n- Premium models"
        }
    }

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
            .setPositiveButton("Install Gmail") { _, _ ->
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
            .setNegativeButton("Copy Manually") { _, _ ->
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
                        .setPositiveButton("Grant Permission") { _, _ ->
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
                    findViewById<ImageButton>(R.id.btnExtractEmail).performClick()
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
}
