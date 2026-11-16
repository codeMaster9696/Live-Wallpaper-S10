// =====================================================================
//  Módulo :app — aplicativo + WallpaperService
//  Alvo de teste real: Samsung Galaxy S10 (SM-G973F), One UI 4.1 / Android 12
//  API 31, tela 1440 x 3040 px (19:9), Exynos 9820 / Mali-G76 MP12.
// =====================================================================
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hub.livewallpaper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hub.livewallpaper"
        // 24 = Android 7.0: menor API com PlaybackParams + EGL14 estável.
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Os vídeos ficam em assets/ — não comprimir MP4 (já comprimido)
        // e não comprimir GLSL (precisamos ler como texto puro).
        androidResources {
            noCompress += listOf("mp4", "frag", "vert", "glsl")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Assinatura de debug para gerar um APK instalável imediatamente
            // (uso pessoal). Troque por uma keystore própria se for distribuir.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
