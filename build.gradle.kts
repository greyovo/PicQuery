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
}