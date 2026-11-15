plugins {
  id("twitchplayer.android-application")
  id("twitchplayer.compose")
  id("twitchplayer.shared-strings")
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.twitchplayer.phone"
  defaultConfig {
    applicationId = "com.twitchplayer.phone"
    // Same floor as the TV app and the libraries (Material 3 and Navigation 3 need 23).
    minSdk = 24
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    val twitchClientId = providers.gradleProperty("twitchClientId").orElse("").get()
    val playbackClientId = providers.gradleProperty("playbackClientId").orElse("").get()
    buildConfigField("String", "TWITCH_CLIENT_ID", "\"$twitchClientId\"")
    buildConfigField("String", "PLAYBACK_CLIENT_ID", "\"$playbackClientId\"")
  }

  // Declares the translated languages to Android 13+ (per-app language settings); the language folders come from the
  // TV app's generated strings (see sharedStrings below) and the unqualified language from res/resources.properties.
  androidResources {
    generateLocaleConfig = true
  }
  // The language can be switched in the app (AppLocales): keep every translation in the base APK.
  bundle {
    language {
      enableSplit = false
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      isDebuggable = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    // Release-like (same R8 rules, not debuggable) but debug-signed and always on the demo data: what the phone's startup and frame
    // numbers are measured on (docs/phone-log.md). Never shipped.
    create("benchmark") {
      initWith(getByName("release"))
      signingConfig = signingConfigs.getByName("debug")
      matchingFallbacks += listOf("release")
    }
    // Same code without R8: Baseline Profiles must be recorded against original (un-obfuscated) names (benchmark-phone).
    create("nonMinified") {
      initWith(getByName("release"))
      isMinifyEnabled = false
      isShrinkResources = false
      signingConfig = signingConfigs.getByName("debug")
      matchingFallbacks += listOf("release")
    }
  }
  sourceSets {
    // The nonMinified variant also runs on the demo data: it reuses the benchmark variant's DebugModules.
    getByName("nonMinified").kotlin.directories.add("src/benchmark/kotlin")
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
    shaders = false
  }
  // The libraries run their own lint with their own baselines (CI runs every module's `check`); the phone module has no
  // baseline at all, so it must not re-analyse their legacy findings through checkDependencies.
  lint {
    checkDependencies = false
    // No baseline for a new module (lint would otherwise create one on its first failing run and hide the findings).
    baseline = null
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

// The 36 translations of the TV app's strings.xml (plus plurals) are generated into this app; nothing is copied into git.
sharedStrings {
  fromModule(":app")
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  implementation(project(":platform-android"))
  // Fictional demo data (no network, no account): debug only, never release.
  debugImplementation(project(":demo-android"))
  "benchmarkImplementation"(project(":demo-android"))
  "nonMinifiedImplementation"(project(":demo-android"))

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  // Installs the packaged Baseline Profile when the app first runs (sideloaded installs are not compiled by Play).
  implementation(libs.androidx.profileinstaller)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui.tooling.preview)
  debugImplementation(libs.androidx.compose.ui.tooling)
  implementation(libs.kotlinx.collections.immutable)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)

  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.exoplayer.hls)

  implementation(libs.coil.compose)
  implementation(libs.coil.network.okhttp)
  implementation(libs.coil.gif)
  implementation(libs.koin.android)
  implementation(libs.koin.compose)
  implementation(libs.koin.compose.viewmodel)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.koin.test)
  testImplementation(libs.androidx.datastore.preferences.core)
  testImplementation(project(":shared:testing"))
  testImplementation(testFixtures(project(":platform-android")))

  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(project(":demo-android"))
}
