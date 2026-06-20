import org.gradle.jvm.tasks.Jar
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val isAppleHost = System.getProperty("os.name") == "Mac OS X"
val desktopPackageVersion = "1.0.0"
val desktopPackageDisplayName = "Mayday Chat"
val desktopDiagnosticsEnabled = providers.gradleProperty("kalogDiagnostics")
    .orElse(
        if (gradle.startParameter.taskNames.any { taskName -> taskName.contains("Release", ignoreCase = true) }) {
            "false"
        } else {
            "true"
        },
    )
    .get()

fun String.asDesktopArtifactNamePart(): String =
    replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').ifBlank { "unknown" }

fun desktopArtifactFileName(versionName: String, buildType: String, extension: String): String =
    "${rootProject.name.asDesktopArtifactNamePart()}-v${versionName.asDesktopArtifactNamePart()}-${buildType.asDesktopArtifactNamePart()}.$extension"

fun desktopBuildTypeName(taskName: String): String =
    if (taskName.contains("Release")) "release" else "debug"

fun TargetFormat.fileExtensionOrNull(): String? =
    when (this) {
        TargetFormat.Deb -> "deb"
        TargetFormat.Dmg -> "dmg"
        TargetFormat.Exe -> "exe"
        TargetFormat.Msi -> "msi"
        TargetFormat.Pkg -> "pkg"
        TargetFormat.Rpm -> "rpm"
        TargetFormat.AppImage -> null
    }

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    androidLibrary {
        namespace = "org.debs.kalog"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        androidResources {
            enable = true
        }
    }

    jvm()
    if (isAppleHost) {
        listOf(
            iosArm64(),
            iosSimulatorArm64(),
        ).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "KALogIos"
                isStatic = true
            }
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        commonMain.dependencies {
            implementation(projects.core.crypto)
            implementation(projects.core.database)
            implementation(projects.core.network)
            implementation(projects.core.preferences)
            implementation(projects.feature.chat)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.koin.compose)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.jna.platform)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.androidx.ui.tooling)
}

compose.desktop {
    application {
        mainClass = "org.debs.kalog.MainKt"
        jvmArgs += listOf(
            "-Dkalog.diagnostics=$desktopDiagnosticsEnabled",
            "-Dkalog.diagnostics.file=true",
        )
        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = desktopPackageDisplayName
            packageVersion = desktopPackageVersion
            vendor = desktopPackageDisplayName
            description = desktopPackageDisplayName

            modules("java.sql")

            windows {
                iconFile.set(project.file("src/jvmMain/resources/icons/mayday-chat.ico"))
                dirChooser = true
                perUserInstall = true
                shortcut = true
                menuGroup = desktopPackageDisplayName
            }

            macOS {
                iconFile.set(project.file("src/jvmMain/resources/icons/mayday-chat.icns"))
                packageName = desktopPackageDisplayName
                dockName = desktopPackageDisplayName
                bundleID = "org.debs.kalog"
                appCategory = "public.app-category.social-networking"
                minimumSystemVersion = "11.0"
            }

            linux {
                iconFile.set(project.file("src/jvmMain/resources/icons/mayday-chat.png"))
            }
        }
    }
}

tasks.withType<Jar>().configureEach {
    when (name) {
        "packageUberJarForCurrentOS",
        "packageReleaseUberJarForCurrentOS" -> {
            archiveFileName.set(
                desktopArtifactFileName(
                    versionName = desktopPackageVersion,
                    buildType = desktopBuildTypeName(name),
                    extension = "jar",
                ),
            )
        }
    }
}

tasks.withType<AbstractJPackageTask>().configureEach {
    val extension = targetFormat.fileExtensionOrNull() ?: return@configureEach
    val buildType = desktopBuildTypeName(name)
    val renamedOutputFileName = desktopArtifactFileName(
        versionName = desktopPackageVersion,
        buildType = buildType,
        extension = extension,
    )

    doLast {
        val outputDir = destinationDir.asFile.get()
        val outputFile = outputDir
            .listFiles()
            ?.filter { it.isFile && it.extension.equals(extension, ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }

        if (outputFile != null) {
            val renamedFile = outputDir.resolve(renamedOutputFileName)
            if (outputFile.canonicalFile != renamedFile.canonicalFile) {
                if (renamedFile.exists()) {
                    renamedFile.delete()
                }
                if (!outputFile.renameTo(renamedFile)) {
                    outputFile.copyTo(renamedFile, overwrite = true)
                    outputFile.delete()
                }
            }
            logger.lifecycle("The JVM package is written to ${renamedFile.canonicalPath}")
        }
    }
}
