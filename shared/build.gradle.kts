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

val generateBuildInfo by tasks.registering(GenerateBuildInfoTask::class) {
    outputDir.set(layout.buildDirectory.dir("generated/buildinfo"))
    trackingHint.set(trackingSecret)
    outputs.upToDateWhen { false }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "no.synth.where.shared"
        compileSdk = 36
        minSdk = 33

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }

        androidResources {
            enable = true
        }
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
            implementation(libs.kmp.zip)
            api(libs.room.runtime)
            api(libs.androidx.datastore.preferences)
            implementation(libs.compose.material3)
            api(libs.compose.components.resources)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            api(libs.ktor.client.okhttp)
            api(libs.timber)
            api(libs.maplibre.android.sdk)
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
