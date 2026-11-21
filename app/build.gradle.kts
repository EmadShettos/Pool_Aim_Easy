plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

base {
    archivesName.set("EmadShettos-PoolAim")
}

android {
    namespace = "com.example.poolaim"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.poolaim"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("emadShettos") {
            val ksPath = System.getenv("KEYSTORE_PATH")
            if (ksPath != null && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        create("emadShettosDebug") {
            initWith(getByName("debug"))
            isDebuggable = true
            signingConfig = signingConfigs.getByName("emadShettos")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
