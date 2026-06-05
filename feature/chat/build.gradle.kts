import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val isAppleHost = System.getProperty("os.name") == "Mac OS X"

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
}

kotlin {
    androidLibrary {
        namespace = "org.debs.kalog.feature.chat"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    jvm()
    if (isAppleHost) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.ui)
        }
        commonMain.dependencies {
            implementation(projects.core.crypto)
            implementation(projects.core.network)
            implementation(projects.core.preferences)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.koin.compose)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.testJunit)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        jvmMain.dependencies {
            implementation(libs.jna.platform)
            implementation("org.openjfx:javafx-base:21.0.5:win")
            implementation("org.openjfx:javafx-graphics:21.0.5:win")
            implementation("org.openjfx:javafx-media:21.0.5:win")
            implementation("org.openjfx:javafx-swing:21.0.5:win")
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.androidx.ui.tooling)
}
