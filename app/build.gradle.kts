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

val gitCommitCount = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.map { it.trim().ifEmpty { "0" } }.orElse("0")

val gitShortSha = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.map { it.trim().ifEmpty { "unknown" } }.orElse("unknown")

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

        val commitCount = gitCommitCount.get()
        val shortSha = gitShortSha.get()
        val buildDate = LocalDate.now().toString()

        versionCode = commitCount.toInt()
        versionName = "$commitCount.$shortSha $buildDate"

        buildConfigField("String", "GIT_COMMIT_COUNT", "\"$commitCount\"")
        buildConfigField("String", "GIT_SHORT_SHA", "\"$shortSha\"")
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
        unitTests.isReturnDefaultValues = true
        unitTests.all { test ->
            test.testLogging {
                showStandardStreams = true
                events("passed", "failed", "skipped")
            }
            test.exclude("no/synth/where/integration/**")
        }
    }
}

afterEvaluate {
    val debugUnitTest = tasks.named<Test>("testDebugUnitTest").get()
    tasks.register<Test>("integrationTest") {
        group = "verification"
        description = "Runs integration tests (configure URLs in local.properties)"
        dependsOn("compileDebugUnitTestKotlin")
        testClassesDirs = debugUnitTest.testClassesDirs
        classpath = debugUnitTest.classpath
        filter { includeTestsMatching("no.synth.where.integration.*") }
        testLogging {
            showStandardStreams = true
            events("passed", "failed", "skipped")
        }
        val localProps = Properties()
        rootProject.file("local.properties").takeIf { it.exists() }
            ?.inputStream()?.use { localProps.load(it) }
        localProps.stringPropertyNames().forEach { key ->
            environment(key, localProps.getProperty(key))
        }
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
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.play.services.location)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.mockk)
    testImplementation(libs.mockk.android)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation(libs.androidx.core)
}
