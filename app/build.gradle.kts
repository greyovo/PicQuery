plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    id("io.objectbox")
    id("io.gitlab.arturbosch.detekt")
}

android {
    namespace = "me.grey.picquery"
    compileSdk = 36

    defaultConfig {
        applicationId = "me.grey.picquery"
        minSdk = 29
        targetSdk = 35
        versionCode = 8
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
    buildToolsVersion = "34.0.0"
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
    implementation(libs.accompanist.navigation.animation)
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

    // Other Libraries
    implementation(libs.zoomable)
    implementation(libs.permissionx)
    implementation(libs.work.runtime)

    // AI & ML
    implementation(libs.onnx.runtime)
    implementation(libs.mlkit.translate)

    // LiteRT
    implementation(libs.litert)
    implementation(libs.litert.support)
    implementation(libs.litert.gpu.api)
    implementation(libs.litert.gpu)

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
    androidTestImplementation(libs.androidx.test.ext)
}

detekt {
    toolVersion = "1.23.3"
    config.setFrom(files("${project.rootDir}/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    autoCorrect = true
    parallel = true
    ignoreFailures = true // Set to true to make detekt non-blocking
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
}

tasks.register<Exec>("installGitHooks") {
    workingDir = rootProject.rootDir
    commandLine("cmd", "/c", "${rootProject.rootDir}/scripts/install-hooks.bat")

    doLast {
        println("Git hooks installed successfully")
    }
}

tasks.named("preBuild") {
    dependsOn("installGitHooks")
}
