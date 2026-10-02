plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// En GitHub Actions, LAVARA_VERSION_CODE es el número de ejecución del workflow:
// cada APK nuevo tiene un número mayor y Android lo instala como actualización.
val lavaraVersionCode = System.getenv("LAVARA_VERSION_CODE")?.toIntOrNull() ?: 1

// La llave de firma nunca está en el repo. CI la decodifica desde GitHub Secrets
// a un archivo temporal y pasa su ruta y contraseña por variables de entorno.
val keystoreFile = System.getenv("LAVARA_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
val keystorePassword = System.getenv("LAVARA_KEYSTORE_PASSWORD")

android {
    namespace = "com.lavara"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.lavara"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = lavaraVersionCode
        versionName = "0.1.$lavaraVersionCode"
    }

    signingConfigs {
        if (keystoreFile != null && !keystorePassword.isNullOrEmpty()) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = "lavara"
                keyPassword = keystorePassword
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
