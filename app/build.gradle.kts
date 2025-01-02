plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.playstudio.aiteacher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.playstudio.aiteacher"
        minSdk = 28
        targetSdk = 34
        versionCode = 46
        versionName = "4.6"

        // Enable multidex
        multiDexEnabled = true

        // Read API key from gradle.properties
        val apiKey: String? = project.findProperty("API_KEY") as String?
        if (apiKey != null) {
            buildConfigField("String", "API_KEY", "\"$apiKey\"")
        } else {
            throw GradleException("API_KEY not found in gradle.properties")
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
        }
    }
    ndkVersion = "27.0.11902837 rc2"
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



    // Replace "VERSION" with the actual version number of the library, e.g., 2.2.0
    //implementation("com.google.cloud:google-cloud-vision:3.51.0")
    //implementation("com.google.code.gson:gson:2.10.1") // Gson for JSON processing
    //implementation("com.google.protobuf:protobuf-java:3.22.3")  // Check for the latest version

    // Other dependencies...
    implementation("com.github.CanHub:Android-Image-Cropper:2.2.0") // Updated android-image-cropper dependency
    implementation("com.squareup.picasso:picasso:2.71828") // Correct version of Picasso

    implementation("com.airbnb.android:lottie:5.0.3")


    // To recognize Latin script
    implementation ("com.google.mlkit:text-recognition:16.0.1")

    // Apache POI for Excel and Word document generation
    implementation(libs.poi.ooxml)
    implementation(libs.poi)
    implementation(libs.poi.scratchpad)

    // PDFBox for PDF handling
    implementation(libs.pdfbox.tools)

    // PdfBox-Android for PDF handling
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Commons Compress for handling compressed files
    implementation(libs.commons.compress)


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