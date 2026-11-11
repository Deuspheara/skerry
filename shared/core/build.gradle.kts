plugins {
  id("twitchplayer.kmp-library")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  android {
    namespace = "com.twitchplayer.tv.shared.core"
  }
  sourceSets {
    commonMain.dependencies {
      api(libs.kotlinx.coroutines.core)
      api(libs.kotlinx.serialization.json)
      api(libs.kotlinx.collections.immutable)
      api(libs.koin.core)
      api(libs.ktor.client.core)
      implementation(libs.ktor.client.content.negotiation)
      implementation(libs.ktor.client.websockets)
      implementation(libs.ktor.serialization.kotlinx.json)
      api(libs.androidx.datastore.preferences.core)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.ktor.client.mock)
    }
  }
}
