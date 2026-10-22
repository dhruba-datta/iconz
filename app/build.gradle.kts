import java.util.Properties

plugins {
    id("com.android.application")
}

// Release signing: key.properties next to settings.gradle.kts (gitignored, real copy in
// ../local/keys). Without it, release builds are signed with this Mac's debug key.
val keyProps = Properties().apply {
    rootProject.file("key.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val releasesRepo = (findProperty("iconz.releasesRepo") as String?).orEmpty()

android {
    namespace = "app.iconz"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.iconz"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "RELEASES_REPO", "\"$releasesRepo\"")
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
            isMinifyEnabled = true
            // Never shrink resources: launchers find icons by name through appfilter.xml,
            // so the shrinker would see them as unused and delete every icon.
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
