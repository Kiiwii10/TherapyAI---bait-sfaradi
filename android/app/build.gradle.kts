plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.googleGmsGoogleServices)
}

android {
    namespace = "com.example.therapyai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.therapyai"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true // Enables resource shrinking (removes unused resources)

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            isMinifyEnabled = true
            isShrinkResources = true // Enables resource shrinking (removes unused resources)

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.recyclerview) // force merge problems to settle on new version
    implementation(libs.dotsindicator)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.core)
    implementation(libs.input.mask.android)

    // ZXing barcode scanner
    implementation(libs.zxing.android.embedded)

    // Room local db
    implementation(libs.room.runtime)
    implementation(libs.biometric)
    implementation(libs.annotations)
    implementation(libs.firebase.messaging)
    implementation(libs.swiperefreshlayout)
    implementation(libs.glide.v4151)
    implementation(libs.lifecycle.process)
    annotationProcessor(libs.room.compiler)

    implementation(platform("com.google.firebase:firebase-bom:33.12.0"))
    implementation("com.google.firebase:firebase-analytics")

    // api calls
    implementation(libs.retrofit)
    implementation(libs.converter.gson)

    // Signature pad
    implementation ("com.github.gcacace:signature-pad:1.3.1")

    // For charting - maybe theres something else
    implementation ("com.github.PhilJay:MPAndroidChart:3.1.0")

//    implementation(libs.glide) // TODO: maybe use this for profile pictures

    // for audio visualizer
    implementation("com.cleveroad:audiovisualization:1.0.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}