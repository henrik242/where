import java.time.LocalDate
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "no.synth.where"
    compileSdkVersion("android-36.1")
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "no.synth.where"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Load HMAC secret from environment variable or local.properties
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }

        val trackingHmacSecret = System.getenv("TRACKING_HMAC_SECRET")
            ?: localProperties.getProperty("TRACKING_HMAC_SECRET")
            ?: throw org.gradle.api.GradleException(
                "TRACKING_HMAC_SECRET is not set!\n" +
                "Add it to local.properties:\n" +
                "  TRACKING_HMAC_SECRET=your-secret-key\n" +
                "Or set it as an environment variable.\n" +
                "Generate a key with: openssl rand -base64 32"
            )

        if (trackingHmacSecret.isBlank()) {
            throw org.gradle.api.GradleException("TRACKING_HMAC_SECRET cannot be empty!")
        }

        buildConfigField("String", "TRACKING_HMAC_SECRET", "\"$trackingHmacSecret\"")

        // Generate version info from git
        fun execGit(command: String): String {
            return try {
                val process = Runtime.getRuntime().exec(command)
                val output = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()
                output
            } catch (e: Exception) {
                println("Warning: Failed to execute '$command': ${e.message}")
                ""
            }
        }
        
        val gitCommitCount = execGit("git rev-list --count HEAD").ifEmpty { "0" }
        val gitShortSha = execGit("git rev-parse --short HEAD").ifEmpty { "unknown" }
        val buildDate = LocalDate.now().toString()
        
        buildConfigField("String", "GIT_COMMIT_COUNT", "\"$gitCommitCount\"")
        buildConfigField("String", "GIT_SHORT_SHA", "\"$gitShortSha\"")
        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    lint {
        abortOnError = false
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.maplibre.android.sdk)
    implementation(libs.gson)
    implementation(libs.play.services.location)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.mockk)
    testImplementation(libs.mockk.android)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation(libs.androidx.core)
    testImplementation(libs.robolectric)
}