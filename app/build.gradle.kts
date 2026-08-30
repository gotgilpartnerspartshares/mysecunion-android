plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    // 3rd-party wrapper app for secunion.co.kr — must NOT use their trademark as package/app id.
    namespace = "com.mysecunion.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mysecunion.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0" // matches Remote Config `latest_version` (Appendix B)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // NFR-309/310: no debug logging, obfuscate release builds
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Firebase BOM - manages versions for all Firebase libraries
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-config-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    // SwipeRefreshLayout for pull-to-refresh WebView (FR-107)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Splash screen (FR-201)
    implementation("androidx.core:core-splashscreen:1.0.1")

    // AndroidX / UI
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
