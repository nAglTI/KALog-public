import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val isAppleHost = System.getProperty("os.name") == "Mac OS X"

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    androidLibrary {
        namespace = "org.debs.kalog.core.preferences"
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
            implementation(libs.datastore.prefs)
        }
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)
            implementation(libs.multiplatform.settings.datastore)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
