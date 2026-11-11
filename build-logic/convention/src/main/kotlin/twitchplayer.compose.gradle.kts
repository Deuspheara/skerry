// Compose compiler plugin (version = Kotlin version). `-PcomposeReports` writes stability reports.
plugins {
  id("org.jetbrains.kotlin.plugin.compose")
}

// Immutable data types that live in shared (non-Compose) modules, see the file header.
extensions.configure<org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension> {
  stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("config/compose/stability.conf"))
}

if (providers.gradleProperty("composeReports").isPresent) {
  extensions.configure<org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension> {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
  }
}
