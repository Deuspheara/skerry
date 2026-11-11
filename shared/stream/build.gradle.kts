plugins {
  id("twitchplayer.kmp-library")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  android {
    namespace = "com.twitchplayer.tv.shared.stream"
  }
  sourceSets {
    commonMain.dependencies {
      // Public API: ResolvedStream / StreamVariant models, HttpClient and Logger in constructors, Koin's Module.
      api(project(":shared:core"))
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.ktor.client.mock)
    }
  }
}
