plugins {
  alias(libs.plugins.android.test)
  id("twitchplayer.detekt")
  id("twitchplayer.spotless")
}

android {
  namespace = "com.twitchplayer.phone.benchmark"
  compileSdk = 37

  defaultConfig {
    minSdk = 28
    targetSdk = 36
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // Emulators give indicative numbers only; a real phone must be used for release decisions.
    testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildTypes {
    // "benchmark" mirrors :phone's R8 build (measurements); "nonMinified" records Baseline Profiles.
    for (name in listOf("benchmark", "nonMinified")) {
      create(name) {
        isDebuggable = true
        signingConfig = signingConfigs.getByName("debug")
        matchingFallbacks += listOf("release")
      }
    }
  }

  targetProjectPath = ":phone"
  experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation(libs.androidx.test.ext.junit)
  implementation(libs.androidx.test.uiautomator)
  implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
  beforeVariants(selector().all()) { it.enable = it.buildType in listOf("benchmark", "nonMinified") }
}
