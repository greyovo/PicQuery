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
        classpath(libs.google.oss.licenses.plugin)  {
            exclude(group = "com.google.protobuf")
        }
        classpath("io.objectbox:objectbox-gradle-plugin:${libs.versions.objectboxGradlePlugin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.library).apply(false)
    alias(libs.plugins.kotlin.android).apply(false)
    alias(libs.plugins.ksp).apply(false)
    // Add ktlint plugin
    id("org.jlleitschuh.gradle.ktlint") version "11.5.1" apply false
    // Add detekt plugin
    id("io.gitlab.arturbosch.detekt") version "1.23.3" apply false
}

// Apply ktlint to all projects
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    
    // Configure ktlint
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        debug.set(true)
        android.set(true)
        outputToConsole.set(true)
        outputColorName.set("RED")
        ignoreFailures.set(false)
        enableExperimentalRules.set(true)
        filter {
            exclude("**/generated/**")
            include("**/kotlin/**")
        }
    }
}

// Remove the temporary .editorconfig approach since we now have a permanent file