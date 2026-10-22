import java.util.Properties

plugins {
    id("com.android.application")
}

// Release signing: key.properties next to settings.gradle.kts (gitignored, real copy in
// ../local/keys). Without it, release builds are signed with this Mac's debug key.
val keyProps = Properties().apply {
    rootProject.file("key.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "app.iconz"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.iconz"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.0"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (keyProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // No code shrinking or obfuscation: the app is tiny, and readable code is
            // easier for Play Protect (and anyone) to check.
            isMinifyEnabled = false
            // Never shrink resources: launchers find icons by name through appfilter.xml,
            // so the shrinker would see them as unused and delete every icon.
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
