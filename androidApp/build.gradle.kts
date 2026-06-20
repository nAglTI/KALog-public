import com.android.build.api.variant.impl.VariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.Instant

val buildVersionCode = (Instant.now().epochSecond / 60).toInt()

fun String.asApkFileNamePart(): String =
    replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').ifBlank { "unknown" }

fun apkFileName(versionName: String, versionCode: Int, buildType: String): String =
    "${rootProject.name.asApkFileNamePart()}-v${versionName.asApkFileNamePart()}-$versionCode-${buildType.asApkFileNamePart()}.apk"

plugins {
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

android {
    namespace = "org.debs.kalog"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.debs.kalog"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = buildVersionCode
        versionName = "1.0.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val variantOutput = output as VariantOutputImpl
            variantOutput.outputFileName.set(
                output.versionName.zip(output.versionCode) { versionName, versionCode ->
                    apkFileName(
                        versionName = versionName,
                        versionCode = versionCode,
                        buildType = variant.buildType.orEmpty(),
                    )
                }
            )
        }
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(projects.feature.chat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.koin.android)

    debugImplementation(libs.androidx.ui.tooling)
}
