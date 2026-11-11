plugins {
  id("twitchplayer.kmp-library")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  android {
    namespace = "com.twitchplayer.tv.shared.chat"
  }
  sourceSets {
    commonMain.dependencies {
      // Public API: chat / emote models, HttpClient and Logger in constructors, Koin's Module.
      api(project(":shared:core"))
      // WebSocket sessions (EventSub chat) are a Ktor plugin the chat client installs itself.
      implementation(libs.ktor.client.websockets)
      implementation(libs.ktor.client.content.negotiation)
      implementation(libs.ktor.serialization.kotlinx.json)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.ktor.client.mock)
    }
  }
}
