// Top-level build file. Shared configuration lives in build-logic/ convention plugins.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.android.test) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.android.kotlin.multiplatform.library) apply false
  alias(libs.plugins.spotless)
}

// Root scripts and the build-logic sources (module sources are covered by each module's own convention).
spotless {
  kotlinGradle {
    // Pruned walk (see twitchplayer.spotless): never descend into the modules or any build/ directory.
    target(
      fileTree(rootDir) {
        include("*.gradle.kts", "build-logic/**/*.gradle.kts")
        exclude("**/build/**", "app/**", "benchmark/**", "shared/**", ".git/**", ".gradle/**", ".kotlin/**")
      },
    )
    ktlint(libs.versions.ktlint.get())
  }
  kotlin {
    target(
      fileTree(rootDir) {
        include("build-logic/**/*.kt")
        exclude("**/build/**", "app/**", "benchmark/**", "shared/**", ".git/**", ".gradle/**", ".kotlin/**")
      },
    )
    ktlint(libs.versions.ktlint.get())
  }
}
