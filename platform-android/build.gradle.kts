plugins {
  id("twitchplayer.android-library")
  id("twitchplayer.compose")
}

android {
  namespace = "com.twitchplayer.tv.platform"
  defaultConfig {
    // The OkHttp -dontwarn rules every consuming app needs (the app's own file keeps its Log stripping).
    consumerProguardFiles("consumer-rules.pro")
  }
  buildFeatures {
    compose = true
  }
  // The Koin graph-test helpers are shared with the apps' demo graph tests.
  testFixtures {
    enable = true
  }
}

dependencies {
  // Exported (api) so consumers resolve the unversioned Compose artifacts of this module's public API.
  val composeBom = platform(libs.androidx.compose.bom)
  api(composeBom)

  // Types in this module's public API (bindings, holder ViewModels, the Media3 player and its surface).
  api(project(":shared:core"))
  api(project(":shared:stream"))
  api(project(":shared:chat"))
  api(project(":shared:player-api"))
  api(project(":shared:presenters"))
  api(libs.koin.core)
  api(libs.androidx.lifecycle.viewmodel)
  api(libs.androidx.compose.ui)
  api(libs.androidx.media3.exoplayer)

  implementation(libs.androidx.compose.runtime)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.media3.exoplayer.hls)
  implementation(libs.androidx.media3.ui)
  // The emote image layers (still + animated decoder) shared by the TV and phone chat renderers.
  implementation(libs.coil.compose)
  implementation(libs.coil.gif)
  implementation(libs.koin.android)
  implementation(libs.koin.core.viewmodel)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.kotlinx.coroutines.android)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.koin.test)
  testImplementation(libs.androidx.datastore.preferences.core)

  testFixturesImplementation(composeBom)
  testFixturesApi(libs.koin.test)
  testFixturesImplementation(libs.androidx.lifecycle.viewmodel.savedstate)
  testFixturesImplementation(project(":shared:core"))
  testFixturesImplementation(project(":shared:chat"))
  testFixturesImplementation(project(":shared:presenters"))
  testFixturesImplementation(libs.kotlinx.coroutines.core)
  testFixturesImplementation(libs.androidx.datastore.preferences.core)
}
