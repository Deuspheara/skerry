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
      api(libs.kotlinx.collections.immutable)
    }
    commonTest.dependencies {
      implementation(project(":shared:testing"))
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}
