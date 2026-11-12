// Shared Kotlin Multiplatform library: `com.android.kotlin.multiplatform.library` (AGP 9) + `jvm()` for fast common
// tests + the Apple targets (arm64 / simulatorArm64 only). Never combine with com.android.library / application in the
// same module. Desktop and wasm targets are added later.
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("twitchplayer.test")
  id("twitchplayer.detekt")
  id("twitchplayer.spotless")
}

extensions.configure<KotlinMultiplatformExtension> {
  jvmToolchain(17)
  jvm()
  // Apple: arm64 and simulatorArm64 only (AGENTS.md), no x64 slices.
  iosArm64()
  iosSimulatorArm64()
  macosArm64()
  tvosArm64()
  tvosSimulatorArm64()
  extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
    compileSdk = 37
    minSdk = 24
    withHostTest { }
    // Rules needed by this module's code travel with it (never as broad -keep rules in the app).
    if (file("consumer-rules.pro").exists()) {
      optimization {
        consumerKeepRules.publish = true
        consumerKeepRules.file("consumer-rules.pro")
      }
    }
  }
  compilerOptions {
    allWarningsAsErrors.set(true)
    freeCompilerArgs.add("-Xexpect-actual-classes")
  }
}

// Native tests run on this Mac in `check`: the host (macosArm64) and the iOS simulator. The tvOS simulator needs a tvOS runtime
// (Xcode > Settings > Components), which is not installed everywhere: its tests are opt-in (`-PtvosSimulatorTests=true`).
// Off a Mac the Kotlin plugin disables every Apple task by itself.
if (providers.gradleProperty("tvosSimulatorTests").orNull != "true") {
  tasks.matching { it.name == "tvosSimulatorArm64Test" }.configureEach { enabled = false }
}

// commonMain only compiles for JVM-based targets today, so JVM/Android APIs would slip through the compiler. This check
// keeps the architecture rule true for the Apple, desktop and web targets added later (AGENTS.md: no Android or java.*
// imports in commonMain).
abstract class VerifyCommonMainIsPlatformFree : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sources: ConfigurableFileCollection

  @TaskAction
  fun verify() {
    val forbidden = Regex(
      """^\s*import (java|javax|android)\.|(?<![\w.])(java|javax|android)\.[a-z]|\bSystem\.(currentTimeMillis|nanoTime)\b|""" +
        """\bString\.format\b|\bSimpleDateFormat\b|\bConcurrentHashMap\b|@Synchronized\b|(?<![\w.])Math\.""",
    )
    val offences = sources.files.filter { it.extension == "kt" }.flatMap { file ->
      file.readLines().withIndex().filter { forbidden.containsMatchIn(it.value) }.map { "${file.name}:${it.index + 1}: ${it.value.trim()}" }
    }
    check(offences.isEmpty()) { "Platform-specific API in commonMain:\n" + offences.joinToString("\n") }
  }
}

val verifyCommonMain = tasks.register<VerifyCommonMainIsPlatformFree>("verifyCommonMain") {
  group = "verification"
  description = "Fails when commonMain uses java.*, android.* or other JVM-only APIs."
  sources.from(fileTree("src/commonMain"))
}
tasks.named("check") { dependsOn(verifyCommonMain) }
