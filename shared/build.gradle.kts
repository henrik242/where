import java.time.LocalDate

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
}

fun execGit(command: Array<String>): String {
    return try {
        val process = Runtime.getRuntime().exec(command)
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        output
    } catch (e: Exception) {
        println("Warning: Failed to execute '${command.joinToString(" ")}': ${e.message}")
        ""
    }
}

val generateBuildInfo by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/buildinfo")
    outputs.dir(outputDir)
    // Always re-run so git info stays current
    outputs.upToDateWhen { false }

    doLast {
        val gitCommitCount = execGit(arrayOf("git", "rev-list", "--count", "HEAD")).ifEmpty { "0" }
        val gitShortSha = execGit(arrayOf("git", "rev-parse", "--short", "HEAD")).ifEmpty { "unknown" }
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
            |}
            """.trimMargin()
        )
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidLibrary {
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

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
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
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.kmp.libs)
            implementation(libs.room.runtime)
            implementation(libs.androidx.datastore.preferences)
            @Suppress("DEPRECATION")
            implementation(compose.material3)
            @Suppress("DEPRECATION")
            implementation(compose.materialIconsExtended)
            @Suppress("DEPRECATION")
            implementation(compose.components.resources)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.android)
            implementation(libs.timber)
            implementation(libs.maplibre.android.sdk)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqlite.bundled)
            implementation(libs.koin.core)
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
    add("kspIosX64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}
