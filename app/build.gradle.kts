plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.axel.mycontrol"
    compileSdk = 35

    // Numéro de version = numéro de compilation GitHub Actions : chaque APK
    // publié est plus récent que le précédent, ce qui permet à l'appli de
    // détecter et d'installer ses propres mises à jour.
    val numero = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

    defaultConfig {
        applicationId = "fr.axel.mycontrol"
        minSdk = 26
        targetSdk = 35
        versionCode = numero
        versionName = "1.$numero"
    }

    // Clé de signature FIXE (stockée dans les secrets GitHub). Indispensable :
    // Android n'accepte une mise à jour que si elle est signée avec la même
    // clé que l'appli installée. Sans elle, chaque compilation aurait une clé
    // différente et il faudrait désinstaller à chaque fois.
    val keystore = System.getenv("MYCONTROL_KEYSTORE")
    val signatureFixe = if (keystore != null && file(keystore).exists()) {
        signingConfigs.create("fixe") {
            storeFile = file(keystore)
            storePassword = System.getenv("MYCONTROL_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("MYCONTROL_KEY_ALIAS") ?: "mycontrol"
            keyPassword = System.getenv("MYCONTROL_KEY_PASSWORD")
                ?: System.getenv("MYCONTROL_KEYSTORE_PASSWORD")
        }
    } else null

    buildTypes {
        debug {
            if (signatureFixe != null) signingConfig = signatureFixe
        }
        release {
            isMinifyEnabled = false
            if (signatureFixe != null) signingConfig = signatureFixe
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
