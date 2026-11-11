// Shared Kotlin Multiplatform library: `com.android.kotlin.multiplatform.library` (AGP 9) + `jvm()` for fast common
// tests. Never combine with com.android.library / application in the same module. Apple, desktop and wasm targets are
// added later (arm64 / simulatorArm64 only for Apple).
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
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
  }
  compilerOptions {
    allWarningsAsErrors.set(true)
    freeCompilerArgs.add("-Xexpect-actual-classes")
  }
}
