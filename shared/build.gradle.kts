import java.time.LocalDate
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
}

abstract class GenerateBuildInfoTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val trackingHint: Property<String>

    private fun execGit(vararg args: String): String {
        return try {
            val process = ProcessBuilder(*args)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            output
        } catch (e: Exception) {
            logger.warn("Failed to execute '${args.joinToString(" ")}': ${e.message}")
            ""
        }
    }

    @TaskAction
    fun generate() {
        val gitCommitCount = execGit("git", "rev-list", "--count", "HEAD").ifEmpty { "0" }
        val gitShortSha = execGit("git", "rev-parse", "--short", "HEAD").ifEmpty { "unknown" }
        val buildDate = LocalDate.now().toString()

        val dir = outputDir.get().asFile.resolve("no/synth/where")
        dir.mkdirs()
        dir.resolve("BuildInfo.kt").writeText(
            """
            |package no.synth.where
            |
            |object BuildInfo {
            |    const val GIT_COMMIT_COUNT = "$gitCommitCount"
            |    const val GIT_SHORT_SHA = "$gitShortSha"
            |    const val BUILD_DATE = "$buildDate"
            |    const val VERSION_INFO = "$gitCommitCount.$gitShortSha $buildDate"
            |    const val TRACKING_HINT = "${trackingHint.get()}"
            |}
            """.trimMargin()
        )
    }
}

val localPropertiesFile = rootProject.file("local.properties")
val trackingSecret = providers.environmentVariable("TRACKING_HINT").orElse(
    providers.provider {
        val props = Properties()
        if (localPropertiesFile.exists()) props.load(localPropertiesFile.inputStream())
        props.getProperty("TRACKING_HINT")
            ?: throw GradleException(
                "TRACKING_HINT is not set!\n" +
                    "Add it to local.properties:\n" +
                    "  TRACKING_HINT=your-secret-key\n" +
                    "Or set it as an environment variable.\n" +
                    "Generate a key with: openssl rand -base64 32"
            )
    }
)

val generateBuildInfo = tasks.register<GenerateBuildInfoTask>("generateBuildInfo") {
    outputDir.set(layout.buildDirectory.dir("generated/buildinfo"))
    trackingHint.set(trackingSecret)
    outputs.upToDateWhen { false }
}

// Gradle 9.7 turns "task consumes KSP-generated output without declaring a dependency" into a build
// failure. The Android lint model/analysis tasks read KSP-generated sources; wire them after KSP so
// `./gradlew build` (which runs lint) passes. Over-declaring across ksp tasks is safe (ordering only).
tasks.matching {
    it.name.startsWith("lintAnalyze") || (it.name.startsWith("generate") && it.name.endsWith("LintModel"))
}.configureEach {
    dependsOn(tasks.matching { it.name.startsWith("ksp") })
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "no.synth.where.shared"
        compileSdk = 37
        minSdk = 33

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }

        androidResources {
            enable = true
        }

        // Run commonTest on the JVM as host tests so CI / Android-only machines execute the
        // shared unit tests without an iOS simulator.
        withHostTest {}
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildInfo.map { it.outputs.files.singleFile })
        }
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.ktor.client.core)
            api(libs.ktor.client.websockets)
            api(libs.room.runtime)
            api(libs.androidx.datastore.preferences)
            implementation(libs.compose.material3)
            implementation(libs.kmp.zip)
            api(libs.compose.components.resources)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            // compose.material3 redirects android to an androidx material3 built against an older
            // compose; pin the one that matches composeBom so this module compiles against it too.
            implementation(libs.androidx.material3)
            api(libs.ktor.client.okhttp)
            api(libs.timber)
            api(libs.maplibre.android.sdk)
            implementation(libs.play.services.location)
            api(project.dependencies.platform(libs.firebase.bom))
            api(libs.firebase.crashlytics)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqlite.bundled)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "no.synth.where.resources"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}
