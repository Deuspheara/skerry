plugins {
  id("twitchplayer.android-library")
}

android {
  namespace = "com.twitchplayer.tv.demo"
}

// Deterministic fictional data (no network, no account) for screenshots, UI tests and benchmarks. Apps depend on this
// module for their debug and benchmark variants only; a release build must not contain it.
dependencies {
  implementation(project(":platform-android"))
  implementation(project(":shared:core"))
  implementation(project(":shared:stream"))
  implementation(project(":shared:chat"))
  implementation(project(":shared:presenters"))
  implementation(libs.coil.core)
  implementation(libs.koin.android)
  implementation(libs.kotlinx.coroutines.android)
}
