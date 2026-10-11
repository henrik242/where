import org.gradle.jvm.toolchain.JvmVendorSpec
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

// CI checks out a PR as GitHub's merge commit, which exists on no branch; BUILD_GIT_SHA names the
// pushed commit so the version stamp is reproducible. Unset locally, where HEAD is correct.
val describedGitRef = providers.environmentVariable("BUILD_GIT_SHA")
    .orNull?.trim()?.takeIf { it.isNotEmpty() } ?: "HEAD"

val gitCommitCount = providers.exec {
    commandLine("git", "rev-list", "--count", describedGitRef)
}.standardOutput.asText.map { it.trim().ifEmpty { "0" } }.orElse("0")

kotlin {
    jvm()

    // MapLibre Compose desktop renders through the Java FFM API, which needs JDK 25.
    jvmToolchain(25)

    sourceSets {
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(compose.material3)
            implementation(project(":shared"))
            // The desktop app opens the SQLite database directly (JdbcSqliteDriver).
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.kotlinx.coroutines.core)
            // Dispatchers.Main on desktop (the AWT event thread), which the MapLibre map needs to
            // deliver its engine callbacks.
            implementation(libs.kotlinx.coroutines.swing)
            // The desktop map's presentation host is installed around the window here.
            implementation(libs.maplibre.compose)
        }
    }
}

compose.desktop {
    application {
        mainClass = "no.synth.where.desktop.MainKt"
        // MapLibre Compose calls native rendering code through the FFM API.
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        // Native packaging (jpackage) needs a full JDK, which the JBR used by the Android tooling
        // lacks. The bundled runtime must also run the compiled bytecode (Java 25), so package with
        // a non-JBR JDK 25 (BellSoft/Liberica ships jpackage).
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
            vendor.set(JvmVendorSpec.BELLSOFT)
        }.get().metadata.installationPath.asFile.absolutePath
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Where"
            // jpackage wants MAJOR.MINOR.BUILD with MAJOR > 0.
            packageVersion = "1.0.${gitCommitCount.get()}"
            // The packaged app also renders the map through the FFM API.
            jvmArgs += "--enable-native-access=ALL-UNNAMED"
            // The SQLite JDBC driver needs java.sql, which the minimized runtime image would strip.
            modules("java.sql")
            macOS {
                iconFile.set(project.file("packaging/AppIcon.icns"))
                // Not the iOS app's id: that one also runs on Apple Silicon Macs.
                bundleID = "no.synth.where.desktop"
                // Hardened-runtime entitlements for signing; signing itself is switched on with
                // -Pcompose.desktop.mac.sign=true (see docs/release-desktop.md).
                entitlementsFile.set(project.file("packaging/entitlements.plist"))
                runtimeEntitlementsFile.set(project.file("packaging/entitlements.plist"))
            }
        }
    }
}
