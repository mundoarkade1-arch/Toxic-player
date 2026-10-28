plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.meuapp.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.meuapp.player"
        minSdk = 26
        targetSdk = 34
        // Em release, o GitHub Actions injeta a versão a partir da tag (ex.: v1.2.0)
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("APP_VERSION") ?: "1.0.0"
        // >>> TROQUE pelos dados do SEU repositório <<<
        buildConfigField("String", "GH_OWNER", "\"mundoarkade1-arch\"")
        buildConfigField("String", "GH_REPO", "\"Toxic-player\"")
    }

    // Assinatura: lê as 3 senhas das variáveis de ambiente (secrets do GitHub). Nada fixo no código.
    signingConfigs {
        create("release") {
            val pass = System.getenv("KEYSTORE_PASSWORD")
            if (pass != null) {
                storeFile = file("release.jks")
                storePassword = pass
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (System.getenv("KEYSTORE_PASSWORD") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
}
