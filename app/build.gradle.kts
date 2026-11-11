plugins {
  id("twitchplayer.android-application")
  id("twitchplayer.compose")
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.twitchplayer.tv"
  defaultConfig {
    applicationId = "com.twitchplayer.tv"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    val twitchClientId = providers.gradleProperty("twitchClientId").orElse("").get()
    val twitchPlayerHost = providers.gradleProperty("twitchPlayerHost").orElse("").get()
    val twitchPlayerParent = providers.gradleProperty("twitchPlayerParent").orElse("").get()
    buildConfigField("String", "TWITCH_CLIENT_ID", "\"$twitchClientId\"")
    buildConfigField("String", "TWITCH_PLAYER_HOST", "\"$twitchPlayerHost\"")
    buildConfigField("String", "TWITCH_PLAYER_PARENT", "\"$twitchPlayerParent\"")
    // Player engines. `-PnativePlayer=false` compiles the Media3 player out (R8 removes it): WebView only.
    // `-PplayerEngine=webview|native` is the engine a fresh install starts with; Settings > Player engine overrides it.
    val nativePlayer = providers.gradleProperty("nativePlayer").orElse("true").get().toBooleanStrict()
    val playerEngine = providers.gradleProperty("playerEngine").orElse("native").get().also { require(it == "native" || it == "webview") { "playerEngine must be native or webview" } }
    buildConfigField("boolean", "NATIVE_PLAYER_ENABLED", nativePlayer.toString())
    buildConfigField("String", "PLAYER_ENGINE_DEFAULT", "\"$playerEngine\"")
  }

  // Declares the translated languages to Android 13+ (per-app language settings); read from res/resources.properties
  // and the values-* folders.
  androidResources {
    generateLocaleConfig = true
  }
  // The language can be switched in the app: keep every translation in the base APK (no per-language bundle split).
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
    // Release-like (R8, not debuggable) but debug-signed and on demo data: what benchmarks measure.
    create("benchmark") {
      initWith(getByName("release"))
      signingConfig = signingConfigs.getByName("debug")
      matchingFallbacks += listOf("release")
    }
    // Same code without R8: Baseline Profiles must be recorded against original (un-obfuscated) names.
    create("nonMinified") {
      initWith(getByName("release"))
      isMinifyEnabled = false
      isShrinkResources = false
      signingConfig = signingConfigs.getByName("debug")
      matchingFallbacks += listOf("release")
    }
  }
  sourceSets {
    // Fictional demo data (no network, no account) is shared by the debug and benchmark variants only.
    getByName("debug").kotlin.directories.add("src/demo/kotlin")
    getByName("debug").assets.directories.add("src/demo/assets")
    for (name in listOf("benchmark", "nonMinified")) {
      getByName(name).kotlin.directories.add("src/demo/kotlin")
      getByName(name).kotlin.directories.add("src/demoAlways/kotlin")
      getByName(name).assets.directories.add("src/demo/assets")
    }
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  implementation(project(":shared:core"))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.profileinstaller)
  implementation(libs.androidx.datastore.preferences)

  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.tv.material)
  implementation(libs.kotlinx.collections.immutable)
  debugImplementation(libs.androidx.compose.ui.tooling)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.coil.compose)
  implementation(libs.coil.network.okhttp)
  implementation(libs.coil.gif)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.client.websockets)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.koin.android)
  implementation(libs.koin.compose)
  implementation(libs.koin.compose.viewmodel)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.exoplayer.hls)
  implementation(libs.androidx.media3.ui)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.ktor.client.mock)

  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
}
