import java.time.LocalDate
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "no.synth.where"
    compileSdk = 36
    compileSdkMinor = 1

    signingConfigs {
        create("release") {
            val localProperties = Properties()
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localProperties.load(localPropertiesFile.inputStream())
            }

            val storeFile = System.getenv("SIGNING_STORE_FILE")?.let { rootProject.file(it) }
                ?: localProperties.getProperty("SIGNING_STORE_FILE")?.let { rootProject.file(it) }
            val storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                ?: localProperties.getProperty("SIGNING_STORE_PASSWORD")
            val keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                ?: localProperties.getProperty("SIGNING_KEY_ALIAS")
            val keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
                ?: localProperties.getProperty("SIGNING_KEY_PASSWORD")

            if (storeFile?.exists() == true && !storePassword.isNullOrBlank() && !keyAlias.isNullOrBlank() && !keyPassword.isNullOrBlank()) {
                this.storeFile = storeFile
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "no.synth.where"
        minSdk = 33
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Load HMAC secret from environment variable or local.properties
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }

        val trackingHmacSecret = System.getenv("TRACKING_HMAC_SECRET")
            ?: localProperties.getProperty("TRACKING_HMAC_SECRET")
            ?: throw GradleException(
                "TRACKING_HMAC_SECRET is not set!\n" +
                        "Add it to local.properties:\n" +
                        "  TRACKING_HMAC_SECRET=your-secret-key\n" +
                        "Or set it as an environment variable.\n" +
                        "Generate a key with: openssl rand -base64 32"
            )

        if (trackingHmacSecret.isBlank()) {
            throw GradleException("TRACKING_HMAC_SECRET cannot be empty!")
        }

        buildConfigField("String", "TRACKING_HMAC_SECRET", "\"$trackingHmacSecret\"")

        // Generate version info from git
        fun execGit(command: Array<String>): String {
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

        val gitCommitCount = execGit(arrayOf("git", "rev-list", "--count", "HEAD")).ifEmpty { "0" }
        val gitShortSha =
            execGit(arrayOf("git", "rev-parse", "--short", "HEAD")).ifEmpty { "unknown" }
        val buildDate = LocalDate.now().toString()

        versionCode = gitCommitCount.toInt()
        versionName = "$gitCommitCount.$gitShortSha $buildDate"

        buildConfigField("String", "GIT_COMMIT_COUNT", "\"$gitCommitCount\"")
        buildConfigField("String", "GIT_SHORT_SHA", "\"$gitShortSha\"")
        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            ndk {
                debugSymbolLevel = "FULL"
            }
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
    implementation(project(":shared"))

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
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.location)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.timber)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.koin.test.junit4)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.mockk)
    testImplementation(libs.mockk.android)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation(libs.androidx.core)
    testImplementation(libs.robolectric)
}
