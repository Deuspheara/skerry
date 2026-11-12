// Kotlin Multiplatform library that only exists on Apple platforms (the Apple implementations of the platform seams and the
// `TwitchShared` XCFramework). Same targets as `twitchplayer.kmp-library` (arm64 / simulatorArm64 only); no Android or JVM
// target, so it must never be a dependency of an Android module.
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("twitchplayer.test")
  id("twitchplayer.detekt")
  id("twitchplayer.spotless")
}

extensions.configure<KotlinMultiplatformExtension> {
  jvmToolchain(17)
  iosArm64()
  iosSimulatorArm64()
  macosArm64()
  tvosArm64()
  tvosSimulatorArm64()
  compilerOptions {
    allWarningsAsErrors.set(true)
    freeCompilerArgs.add("-Xexpect-actual-classes")
    // Foundation / Security / Network calls are all "foreign API"; this module is where that boundary lives.
    optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
    optIn.add("kotlinx.cinterop.BetaInteropApi")
  }
}

// See twitchplayer.kmp-library: the tvOS simulator needs a runtime that is not installed everywhere.
if (providers.gradleProperty("tvosSimulatorTests").orNull != "true") {
  tasks.matching { it.name == "tvosSimulatorArm64Test" }.configureEach { enabled = false }
}
