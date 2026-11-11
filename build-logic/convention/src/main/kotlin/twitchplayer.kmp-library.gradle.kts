// Shared Kotlin Multiplatform library: `com.android.kotlin.multiplatform.library` (AGP 9) + `jvm()` for fast common
// tests. Never combine with com.android.library / application in the same module. Apple, desktop and wasm targets are
// added later (arm64 / simulatorArm64 only for Apple).
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
