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
      // Public API: Flow / CoroutineScope, @Serializable models and KSerializer parameters, Koin's Module, DataStore and
      // HttpClient / engine factory in signatures.
      api(libs.kotlinx.coroutines.core)
      api(libs.kotlinx.serialization.json)
      api(libs.koin.core)
      api(libs.ktor.client.core)
      api(libs.androidx.datastore.preferences.core)
      // Implementation details.
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
