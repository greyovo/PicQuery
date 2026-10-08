@file:Suppress("DEPRECATION")

import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use(keystoreProperties::load)
}
val isReleaseBuild = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true)
}
if (isReleaseBuild && !keystorePropertiesFile.exists()) {
    throw GradleException(
        "Missing android/key.properties. Release builds require an Android signing key."
    )
}

android {
    namespace = "me.grey.picquery"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        applicationId = "me.grey.picquery"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        jniLibs {
            pickFirsts += listOf("lib/**/libc++_shared.so")
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(layout.buildDirectory.dir("cxx_shared").get().asFile)
        }
    }
}

// Resolve the NDK when the task runs, after AGP has finalized ndkVersion.
// Reading android.ndkDirectory inside the DSL can resolve the default NDK too early.
val copyNdkCxxShared by tasks.registering(Sync::class) {
    val hostTag = when {
        System.getProperty("os.name").lowercase().contains("mac") -> "darwin-x86_64"
        System.getProperty("os.name").lowercase().contains("linux") -> "linux-x86_64"
        System.getProperty("os.name").lowercase().contains("win") -> "windows-x86_64"
        else -> error("Unsupported host OS")
    }
    val ndkDirectory = androidComponents.sdkComponents.ndkDirectory
    val abiToTriple = mapOf(
        "arm64-v8a" to "aarch64-linux-android",
        "armeabi-v7a" to "arm-linux-androideabi",
        "x86_64" to "x86_64-linux-android",
        "x86" to "i686-linux-android"
    )
    abiToTriple.forEach { (abi, triple) ->
        from(ndkDirectory.map {
            it.file("toolchains/llvm/prebuilt/$hostTag/sysroot/usr/lib/$triple/libc++_shared.so")
        }) {
            into(abi)
        }
    }
    into(layout.buildDirectory.dir("cxx_shared"))
}

tasks.named("preBuild") {
    dependsOn(copyNdkCxxShared)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

flutter {
    source = "../.."
}
