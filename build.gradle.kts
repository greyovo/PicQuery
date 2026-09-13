buildscript {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven { url = uri("https://plugins.gradle.org/m2/") }
    }
    dependencies {
        // Keep AGP's built-in Kotlin and legacy kapt aligned with the compiler plugins.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.asProvider().get()}")
        classpath(libs.google.oss.licenses.plugin)  {
            exclude(group = "com.google.protobuf")
        }
        classpath("io.objectbox:objectbox-gradle-plugin:${libs.versions.objectboxGradlePlugin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.library).apply(false)
    alias(libs.plugins.kotlin.kapt).apply(false)
    alias(libs.plugins.ksp).apply(false)
    // Add ktlint plugin
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
    // Add detekt plugin
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

// Apply ktlint to all projects
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    
    // Configure ktlint
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.8.0")
        debug.set(true)
        android.set(true)
        outputToConsole.set(true)
        outputColorName.set("RED")
        ignoreFailures.set(false)
        baseline.set(file("config/ktlint/baseline.xml"))
        enableExperimentalRules.set(true)
        filter {
            exclude("**/generated/**")
            include("**/*.kt", "**/*.kts")
        }
    }
}
