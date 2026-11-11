// ktlint through Spotless. `./gradlew spotlessApply` fixes, `spotlessCheck` (part of `check`) verifies.
// Rules come from the repo's .editorconfig.
plugins {
  id("com.diffplug.spotless")
}

val ktlintVersion = versionCatalogs.named("libs").findVersion("ktlint").get().requiredVersion

spotless {
  kotlin {
    target("src/**/*.kt")
    targetExclude("**/build/**")
    ktlint(ktlintVersion)
  }
  kotlinGradle {
    // A file tree that prunes build/ and src/ while walking: a plain "*.gradle.kts" pattern walks the whole module,
    // build/ included, and fails with "Could not read path" when a task rewrites build/ at the same time.
    target(
      fileTree(projectDir) {
        include("*.gradle.kts")
        exclude("build/**", "src/**")
      },
    )
    ktlint(ktlintVersion)
  }
}
