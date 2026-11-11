plugins {
  `kotlin-dsl`
}

kotlin {
  jvmToolchain(17)
}

gradlePlugin {
  plugins {
    register("sharedStrings") {
      id = "twitchplayer.shared-strings"
      implementationClass = "com.twitchplayer.buildlogic.SharedStringsPlugin"
    }
  }
}

dependencies {
  compileOnly(libs.android.gradle.plugin)
  compileOnly(libs.kotlin.gradle.plugin)
  compileOnly(libs.compose.compiler.gradle.plugin)
  compileOnly(libs.kotlin.serialization.gradle.plugin)
  implementation(libs.spotless.gradle.plugin)
  implementation(libs.detekt.gradle.plugin)
  implementation(libs.kover.gradle.plugin)

  testImplementation(libs.junit)
  testImplementation(libs.android.gradle.plugin)
}
