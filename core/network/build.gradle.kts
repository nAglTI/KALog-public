import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val isAppleHost = System.getProperty("os.name") == "Mac OS X"
val localNetworkPropertiesFile = rootProject.layout.projectDirectory.file("local.properties").asFile
val localNetworkProperties = Properties().apply {
    if (localNetworkPropertiesFile.isFile) {
        localNetworkPropertiesFile.inputStream().use(::load)
    }
}

fun localNetworkProperty(key: String): String? {
    return localNetworkProperties.getProperty(key)?.takeIf(String::isNotBlank)
}

val networkBaseUrl = localNetworkProperty("kalog.network.baseUrl")
    ?: providers.environmentVariable("KALOG_NETWORK_BASE_URL").orNull
    ?: ""

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.serialization)
    alias(libs.plugins.buildKonfig)
}

kotlin {
    androidLibrary {
        namespace = "org.debs.kalog.core.network"
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
            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            implementation(projects.core.crypto)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        if (isAppleHost) {
            iosMain.dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}

buildkonfig {
    packageName = "org.debs.kalog.core.network.config"
    objectName = "NetworkBuildKonfig"

    defaultConfigs {
        buildConfigField(STRING, "NETWORK_BASE_URL", networkBaseUrl)
    }
}
