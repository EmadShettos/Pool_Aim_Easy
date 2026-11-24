plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// القناة: "test" فقط عند البناء من فرع Test (يضبطها ملف build.yml)، وأي حالة أخرى = "latest" (النسخة الرسمية)
val updateChannel: String = if (System.getenv("CHANNEL") == "test") "test" else "latest"

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

        // نسخة التجربة: اسم حزمة مختلف لتُثبَّت بجانب النسخة الرسمية دون أن تمسّها
        applicationIdSuffix = if (updateChannel == "test") ".test" else ""
        manifestPlaceholders["appLabel"] =
            if (updateChannel == "test") "Pool Aim Shettos TEST" else "Pool Aim Shettos"
        buildConfigField("String", "UPDATE_TAG", "\"$updateChannel\"")
    }

    buildFeatures {
        buildConfig = true
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
