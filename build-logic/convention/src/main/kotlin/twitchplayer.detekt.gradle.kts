// detekt 2.x: the plain `detekt` task plus type-resolved tasks (detektDebug, detektDebugUnitTest, detektRelease on
// Android; detektMain/detektTest on JVM) and the Compose rules. All of them run in `check`.
// Existing findings live in per-task baselines (detekt-baseline*.xml); new code gets no suppressions.
// `./gradlew detektBaseline detektBaselineDebug ...` regenerates them, only to record legacy issues.
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask

plugins {
  id("dev.detekt")
}

val catalog = versionCatalogs.named("libs")

detekt {
  buildUponDefaultConfig = true
  parallel = true
  // The repo-wide rules, then an optional module-level detekt.yml layered on top (a module may relax a rule for its own code only).
  config.setFrom(listOfNotNull(rootProject.file("config/detekt/detekt.yml"), file("detekt.yml").takeIf { it.exists() }))
  source.setFrom("src")
}

dependencies {
  add("detektPlugins", catalog.findLibrary("detekt-compose-rules").get())
}

fun baselineName(suffix: String) = if (suffix.isEmpty()) "detekt-baseline.xml" else "detekt-baseline-${suffix.replaceFirstChar { it.lowercase() }}.xml"

tasks.withType<Detekt>().configureEach {
  baseline.set(layout.projectDirectory.file(baselineName(name.removePrefix("detekt"))))
}
tasks.withType<DetektCreateBaselineTask>().configureEach {
  baseline.set(layout.projectDirectory.file(baselineName(name.removePrefix("detektBaseline"))))
}

val typedTasks = setOf(
  "detektDebug",
  "detektDebugUnitTest",
  "detektRelease", // Android app
  "detektMainAndroid",
  "detektHostTestAndroid",
  "detektMainJvm",
  "detektTestJvm", // KMP library
)
tasks.named("check") {
  dependsOn(tasks.withType<Detekt>().matching { it.name in typedTasks })
}
