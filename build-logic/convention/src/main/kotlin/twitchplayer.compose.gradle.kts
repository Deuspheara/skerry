// Compose compiler plugin (version = Kotlin version). `-PcomposeReports` writes stability reports.
plugins {
  id("org.jetbrains.kotlin.plugin.compose")
}

if (providers.gradleProperty("composeReports").isPresent) {
  extensions.configure<org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension> {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
  }
}
