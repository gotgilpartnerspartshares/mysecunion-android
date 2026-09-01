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
        // Phase 1 개발/테스트 반복 중 — 정식 배포 시점에 1.0.0으로 올릴 것.
        // 릴리스마다 둘 다 올린다: versionCode는 매번 +1(값 자체는 의미 없음, 순증가만 보장),
        // versionName은 Remote Config `latest_version`/`min_supported_version`과 반드시 일치시킬 것 (Appendix B).
        versionCode = 3
        versionName = "0.3.0"

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

// Output APK filename -> mySecUnion_v<versionName>[-debug].apk instead of the default
// app-debug.apk / app-release.apk, so a tester can tell versions apart at a glance
// without opening the app (matters since this is sideloaded, no Play Store version list).
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            // outputFileName isn't on the public VariantOutput interface in AGP 8.6, only on
            // the impl class it's actually backed by — this cast is the documented workaround.
            val impl = output as com.android.build.api.variant.impl.VariantOutputImpl
            val suffix = if (variant.buildType == "debug") "-debug" else ""
            impl.outputFileName.set(
                output.versionName.map { versionName -> "mySecUnion_v${versionName}$suffix.apk" }
            )
        }
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
