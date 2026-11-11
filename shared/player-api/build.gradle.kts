plugins {
  id("twitchplayer.kmp-library")
}

kotlin {
  android {
    namespace = "com.twitchplayer.tv.shared.playerapi"
  }
  sourceSets {
    commonMain.dependencies {
      // Public API: VideoPlayer exposes StateFlow.
      api(libs.kotlinx.coroutines.core)
      // Implementation detail: VideoQualities.parse reads the embed page's JSON answer.
      implementation(libs.kotlinx.serialization.json)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}
