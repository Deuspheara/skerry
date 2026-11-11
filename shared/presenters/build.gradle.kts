plugins {
  id("twitchplayer.kmp-library")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  android {
    namespace = "com.twitchplayer.tv.shared.presenters"
  }
  sourceSets {
    commonMain.dependencies {
      // Public API: presenters take repositories and expose StateFlow of models from core; UI states hold ImmutableList.
      api(project(":shared:core"))
      // The player presenter exposes the chat models (ChatEvent, ChannelNotice, Emote...) in its public API.
      api(project(":shared:chat"))
      api(libs.kotlinx.collections.immutable)
      // ChatLine formats each line's arrival time in the device's time zone.
      implementation(libs.kotlinx.datetime)
    }
    commonTest.dependencies {
      implementation(project(":shared:testing"))
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}
