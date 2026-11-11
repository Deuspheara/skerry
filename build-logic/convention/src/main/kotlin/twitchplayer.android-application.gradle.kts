// Common setup for the Android application module: Android Gradle Plugin (built-in Kotlin), Java 17, Lint and the
// quality conventions. R8 / minification / profiles stay in the app's own build file on purpose (see AGENTS.md).
import com.android.build.api.dsl.ApplicationExtension

plugins {
  id("com.android.application")
  id("twitchplayer.test")
  id("twitchplayer.detekt")
  id("twitchplayer.spotless")
}

extensions.configure<ApplicationExtension> {
  compileSdk = 37
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  lint {
    warningsAsErrors = true
    abortOnError = true
    checkDependencies = true
    baseline = file("lint-baseline.xml")
  }
}

extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension> {
  jvmToolchain(17)
}
