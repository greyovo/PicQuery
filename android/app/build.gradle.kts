plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "me.grey.picquery"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
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

    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packagingOptions {
        jniLibs {
            pickFirsts += listOf("lib/**/libc++_shared.so")
        }
    }

    val ndkDir = ndkDirectory
    val hostTag = when {
        System.getProperty("os.name").lowercase().contains("mac") -> "darwin-x86_64"
        System.getProperty("os.name").lowercase().contains("linux") -> "linux-x86_64"
        System.getProperty("os.name").lowercase().contains("win") -> "windows-x86_64"
        else -> error("Unsupported host OS")
    }
    val abiToTriple = mapOf(
        "arm64-v8a" to "aarch64-linux-android",
        "armeabi-v7a" to "arm-linux-androideabi",
        "x86_64" to "x86_64-linux-android",
        "x86" to "i686-linux-android"
    )
    val cxxLibDir = File(buildDir, "cxx_shared")
    abiToTriple.forEach { (abi, triple) ->
        val src = File(
            ndkDir, "toolchains/llvm/prebuilt/$hostTag/sysroot/usr/lib/$triple/libc++_shared.so"
        )
        if (src.exists()) {
            val dst = File(cxxLibDir, "$abi/libc++_shared.so")
            dst.parentFile.mkdirs()
            src.copyTo(dst, overwrite = true)
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(cxxLibDir)
        }
    }

}

flutter {
    source = "../.."
}
