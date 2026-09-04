// app/build.gradle.kts (Module Level)

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.br80.remote"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.br80.remote"
        minSdk = 26
        targetSdk = 34
        versionCode = 23 // x-release-please-versionCode
        versionName = "3.5.0" // x-release-please-version
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Popolato solo in CI (release-please/tag), tramite variabili d'ambiente decodificate
        // dai GitHub Secrets. In locale, senza queste env var, "release" non è firmabile:
        // per lo sviluppo quotidiano si continua a usare assembleDebug.
        create("release") {
            val keystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // Disattivato deliberatamente: la libreria del plugin Tasker legge le annotazioni
            // @TaskerInputField/@TaskerOutputVariable via reflection a runtime. Abilitare R8
            // senza regole di keep dedicate (non ancora scritte/testate) rischierebbe di
            // rompere silenziosamente il plugin Tasker già funzionante.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    // Libreria ufficiale di Tasker per costruire plugin veri (Evento/Azione/Condizione)
    // senza reimplementare a mano l'handshake Intent/BroadcastReceiver a messageID.
    implementation("com.joaomgcd:taskerpluginlibrary:0.4.10")
    // Mappature tasto/gesto (solo quelle, non le altre preference semplici): sostituisce
    // le chiavi composite in SharedPreferences, abilitando rename/delete-per-profilo reali.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
