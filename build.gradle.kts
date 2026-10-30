// Top-level build file. Plugin versions live in gradle/libs.versions.toml.
// Android Gradle Plugin 9 compiles Kotlin natively, so the kotlin-android plugin is not applied.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
