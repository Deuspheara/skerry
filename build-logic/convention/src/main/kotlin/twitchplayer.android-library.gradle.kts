// Common setup for Android library modules that are NOT Kotlin Multiplatform (platform glue shared by the TV and phone apps):
// Android Gradle Plugin (built-in Kotlin), Java 17, Lint and the quality conventions. Shared, platform-free code belongs
// in a `shared/*` KMP module (`twitchplayer.kmp-library`) instead. Libraries have no BuildConfig values: the apps pass them in.
import com.android.build.api.dsl.LibraryExtension

plugins {
  id("com.android.library")
  id("twitchplayer.test")
  id("twitchplayer.detekt")
  id("twitchplayer.spotless")
}

extensions.configure<LibraryExtension> {
  compileSdk = 37
  defaultConfig {
    minSdk = 24
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  lint {
    warningsAsErrors = true
    abortOnError = true
    baseline = file("lint-baseline.xml")
  }
}

extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension> {
  jvmToolchain(17)
}
