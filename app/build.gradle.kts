plugins {
    id("com.android.application")
    kotlin("android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.playstudio.aiteacher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.playstudio.aiteacher"
        minSdk = 28
        targetSdk = 34
        versionCode = 77
        versionName = "7.7"
        vectorDrawables.useSupportLibrary = true

        // Enable multidex
        multiDexEnabled = true

        // Read API key from gradle.properties
        val apiKey: String? = project.findProperty("API_KEY") as String?
        if (apiKey != null) {
            buildConfigField("String", "API_KEY", "\"$apiKey\"")
        } else {
            throw GradleException("API_KEY not found in gradle.properties")
        }
        // Read Anthropic API key for Claude models
        val anthropicKey: String? = project.findProperty("ANTHROPIC_API_KEY") as String?
        if (anthropicKey != null) {
            buildConfigField("String", "ANTHROPIC_API_KEY", "\"$anthropicKey\"")
        } else {
            throw GradleException("ANTHROPIC_API_KEY not found in gradle.properties")
        }
        // Read Google Vision API key from gradle.properties
        val googleVisionApiKey: String? = project.findProperty("GOOGLE_VISION_API_KEY") as String?
        if (googleVisionApiKey != null) {
            buildConfigField("String", "GOOGLE_VISION_API_KEY", "\"$googleVisionApiKey\"")
        } else {
            throw GradleException("GOOGLE_VISION_API_KEY not found in gradle.properties")


        }
    }

    buildFeatures {
        dataBinding = true
        viewBinding = true
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/LICENSE.txt")
            excludes.add("META-INF/license.txt")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/NOTICE.txt")
            excludes.add("META-INF/notice.txt")
            excludes.add("META-INF/LICENSE.md")
            excludes.add("META-INF/NOTICE.md")
            excludes.add("META-INF/ASL2.0")
            excludes.add("META-INF/*.kotlin_module")
            excludes.add("org/bouncycastle/x509/CertPathReviewerMessages.properties")
            excludes.add("org/bouncycastle/x509/CertPathReviewerMessages_de.properties")
            excludes.add("META-INF/INDEX.LIST") // Exclude the problematic INDEX.LIST

            // Add this line to fix the current error
            excludes += "mozilla/public-suffix-list.txt"
        }
    }
    ndkVersion = "27.0.11902837 rc2"
}

repositories {
    mavenCentral()
    maven { url = uri("https://alphacephei.com/maven/") }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.okhttp)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.databinding.ktx)
    implementation(libs.gson.v2101)
    implementation(libs.car.ui.lib)
    implementation(libs.play.services.ads.v2320)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.billingclient.billing.ktx)
    implementation(libs.androidx.multidex)
    implementation(libs.stripe.android) {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
        exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
    }

    implementation(libs.sun.android.mail)
    implementation(libs.android.activation)
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.client.gson)
    implementation(libs.google.api.client)
    implementation(libs.google.api.services.gmail.vv1rev1101250)
    implementation(libs.text.recognition)
    implementation(libs.play.services.mlkit.text.recognition)
    implementation(libs.retrofit)
    implementation(libs.squareup.converter.gson)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.http.client.jackson2)


    //implementation("org.vosk:android:0.3.32")
    //implementation("org.vosk:libvosk:0.3.32")
    // Add the dependencies for the Remote Config and Analytics libraries
    implementation("com.google.firebase:firebase-config")
    //implementation("com.google.firebase:firebase-analytics")
    implementation("com.android.billingclient:billing:7.1.1")

    implementation("com.google.firebase:firebase-dynamic-links:22.1.0")
    implementation("com.facebook.android:facebook-android-sdk:[8,9)")

    implementation("com.github.CanHub:Android-Image-Cropper:2.2.0") // Updated android-image-cropper dependency
    implementation("com.squareup.picasso:picasso:2.71828") // Correct version of Picasso

    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("com.airbnb.android:lottie:5.0.3")

    implementation ("com.google.android.material:material:1.6.0") // or latest version

    // To recognize Latin script
    implementation("com.google.mlkit:text-recognition:16.0.1")
    //implementation ("com.google.mlkit:text-recognition-latin:16.0.0")

    implementation ("com.google.ai.client.generativeai:generativeai:0.1.1")
    implementation ("org.java-websocket:Java-WebSocket:1.5.2")






    // ... other dependencies
    implementation("com.sun.mail:android-mail:1.6.7") // For mail protocols
    implementation("com.sun.mail:android-activation:1.6.7") // For data handlers
    //implementation("com.arthenica:ffmpeg-kit-full:6.0-2")
    // implementation ("org.deepspeech:libdeepspeech:0.9.3")
    //implementation ("com.arthenica:mobile-ffmpeg-min:4.4.LTS")
    //implementation ("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1") // For lifecycleScope
    // or use one of these if you need more features:
     //implementation ("com.arthenica:mobile-ffmpeg-min:4.4.LTS")
     //implementation ("com.arthenica:mobile-ffmpeg-full-gpl:4.4.LTs")
    //implementation("com.arthenica:mobile-ffmpeg-min:4.4.LTS")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2") // For lifecycleScope

    //implementation ("com.arthenica:ffmpeg-kit-full:6.0-2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation ("androidx.cardview:cardview:1.0.0")

    implementation("com.google.android.play:app-update:2.1.0") // For in-app updates
    implementation("com.google.android.play:app-update-ktx:2.1.0")
    // Apache POI for Excel and Word document generation
    implementation(libs.poi.ooxml)
    implementation(libs.poi)
    implementation(libs.poi.scratchpad)

    // PDFBox for PDF handling
    implementation(libs.pdfbox.tools)

    implementation ("com.google.code.gson:gson:2.10.1")

    // PdfBox-Android for PDF handling
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")
    implementation("com.google.firebase:firebase-messaging:23.0.0")
    implementation("androidx.fragment:fragment-ktx:1.6.1")
    implementation("org.apache.pdfbox:pdfbox:2.0.24")
    implementation("org.apache.pdfbox:pdfbox-tools:2.0.24")
    implementation("org.apache.pdfbox:fontbox:2.0.24")
    // Commons Compress for handling compressed files
    implementation(libs.commons.compress)

    implementation("com.github.bumptech.glide:glide:4.12.0")
    implementation(libs.firebase.config.ktx)
    implementation(libs.firebase.config.ktx)
    implementation(libs.google.firebase.crashlytics.buildtools)
    implementation(libs.core.ktx)
    implementation(libs.core.ktx)
    implementation(libs.core.ktx)
    implementation(libs.core.ktx)
    implementation(libs.core.ktx)
    implementation(libs.core.ktx)
    implementation(libs.core.ktx)
    implementation(libs.core.ktx)
    implementation(libs.core.ktx)
    implementation(libs.core.ktx)
    implementation(libs.core.ktx)
    implementation(libs.core)
    implementation(libs.firebase.vertexai)
    implementation(libs.common)
    implementation(libs.firebase.database.ktx)
    implementation(libs.androidx.core)
    implementation(libs.androidx.runner)
    //implementation(libs.play.services.measurement.api)
    annotationProcessor("com.github.bumptech.glide:compiler:4.12.0")


    // ARCore
    implementation("com.google.ar:core:1.48.0")
    // Bouncy Castle library (ensure only one version is included)
    implementation(libs.support.annotations)
    implementation(libs.androidx.palette.ktx)

    // Test and Debug Dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v121)
    androidTestImplementation(libs.androidx.espresso.core.v361)
    debugImplementation(libs.androidx.ui.tooling)
}

// Configure tasks dependencies
afterEvaluate {
    tasks.findByName("extractDeepLinksDebug")?.let {
        it.dependsOn("processDebugGoogleServices")
    }

    tasks.findByName("extractDeepLinksRelease")?.let {
        it.dependsOn("processReleaseGoogleServices")
    }
}

// Applying google-services plugin
apply(plugin = "com.google.gms.google-services")