// Top-level build file. Shared configuration lives in build-logic/ convention plugins.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.test) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.spotless)
}

// Root scripts and the build-logic sources (module sources are covered by each module's own convention).
spotless {
  kotlinGradle {
    target("*.gradle.kts", "build-logic/**/*.gradle.kts")
    targetExclude("**/build/**")
    ktlint(libs.versions.ktlint.get())
  }
  kotlin {
    target("build-logic/**/*.kt")
    targetExclude("**/build/**")
    ktlint(libs.versions.ktlint.get())
  }
}
