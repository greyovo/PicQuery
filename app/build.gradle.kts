plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
    id("io.objectbox")
    id("io.gitlab.arturbosch.detekt")
}

abstract class StageMobileClipAssets : Sync() {
    @get:OutputDirectory
    abstract val assetDirectory: DirectoryProperty

    init {
        into(assetDirectory)
    }
}

android {
    namespace = "me.grey.picquery"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    defaultConfig {
        applicationId = "me.grey.picquery"
        minSdk = 29
        targetSdk = 35
        versionCode = 8
        versionName = "1.2.0"
        buildConfigField("String", "LITERT_VERSION", "\"${libs.versions.litert.asProvider().get()}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_static"
            }
        }
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += providers.gradleProperty("mobileclip2Abis").orNull
                ?.split(",") ?: listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        debug {}

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions += "inference"
    productFlavors {
        create("onnx") {
            dimension = "inference"
            applicationIdSuffix = ".mobileclip2.onnx"
            versionNameSuffix = "-mc2-onnx"
            buildConfigField("String", "MOBILECLIP2_BACKEND", "\"onnx\"")
            manifestPlaceholders["appLabel"] = "PicQuery MC2 ONNX"
        }
        create("tflite") {
            dimension = "inference"
            applicationIdSuffix = ".mobileclip2.tflite"
            versionNameSuffix = "-mc2-tflite"
            buildConfigField("String", "MOBILECLIP2_BACKEND", "\"tflite\"")
            manifestPlaceholders["appLabel"] = "PicQuery MC2 TFLite"
        }
    }

    // Match the existing app: FP32 image tower + dynamic INT8 text weights.
    sourceSets.getByName("main").assets.directories.clear()

    androidResources {
        noCompress += listOf("onnx", "tflite")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
}

// The XNNPACK options ABI is pinned to the existing runtime, including its defaults return type.
check(libs.versions.litert.asProvider().get() == "1.4.2") {
    "Revalidate the native XNNPACK headers and device parity before changing LiteRT."
}

androidComponents {
    onVariants { variant ->
        val backend = variant.productFlavors.single { it.first == "inference" }.second
        val modelFiles = when (backend) {
            "onnx" -> listOf("mobileclip2_s0_image.onnx", "mobileclip2_s0_text_int8.onnx")
            else -> listOf("image_model.tflite", "text_model_dynamic_wi8.tflite")
        }
        val modelAssets = tasks.register<StageMobileClipAssets>(
            "stage${variant.name.replaceFirstChar { it.uppercase() }}MobileClipAssets"
        ) {
            assetDirectory.set(layout.buildDirectory.dir("generated/mobileclip2Assets/${variant.name}"))
            from("src/main/assets") {
                include("bpe_vocab_gz", "mlkit/**")
                include(modelFiles)
            }
            doFirst {
                modelFiles.forEach { modelFile ->
                    check(file("src/main/assets/$modelFile").isFile) {
                        "Missing $modelFile. See script/model-MobileCLIP2/README.md for export instructions."
                    }
                }
            }
        }
        variant.sources.assets?.addGeneratedSourceDirectory(modelAssets, StageMobileClipAssets::assetDirectory)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val kotlinVersion = libs.versions.kotlin.asProvider().get()

configurations.matching { it.name == "composeMappingProducerClasspath" }.configureEach {
    resolutionStrategy.force("org.jetbrains.kotlin:compose-group-mapping:$kotlinVersion")
}

dependencies {
    // Bill of Materials
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Implementation dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.legacy)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.dataStore)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.splashscreen)

    // Compose
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // Accompanist
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.accompanist.permissions)

    // Koin
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.compose.navigation)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Serialization
    implementation(libs.kotlinx.serialization)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)

    // Logging
    implementation(libs.timber)

    // Image Loading
    implementation(libs.glide)
    implementation(libs.glide.compose)
    implementation(libs.coil)
    implementation(libs.coil.compose)

    // Other Libraries
    implementation(libs.zoomable)
    implementation(libs.permissionx)
    implementation(libs.work.runtime)

    // AI & ML
    implementation(libs.onnx.runtime)
    implementation(libs.mlkit.translate)

    // LiteRT
    implementation(libs.litert)
    implementation(libs.litert.support.api)
    implementation(libs.litert.gpu.api)
    implementation(libs.litert.gpu)

    // ObjectBox
    implementation(libs.objectbox.kotlin)

    // Debug implementation
    debugImplementation(libs.compose.ui.tooling)

    // Annotation processors
    annotationProcessor(libs.glide.compiler)

    // KSP
    ksp(libs.room.compiler)

    // Test implementation
    testImplementation(libs.junit)

    // Android test implementation
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.test.monitor)
}

detekt {
    toolVersion = "1.23.8"
    config.setFrom(files("${project.rootDir}/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    autoCorrect = false
    parallel = true
    ignoreFailures = true // Set to true to make detekt non-blocking
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
}
