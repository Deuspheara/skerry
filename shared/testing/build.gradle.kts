plugins {
  id("twitchplayer.kmp-library")
}

// Fakes shared by the unit tests of every module (fake repositories, session, settings). Depends on the models and
// repository interfaces only; never ship it in a release build (tests consume it with testImplementation / commonTest).
kotlin {
  android {
    namespace = "com.twitchplayer.tv.shared.testing"
  }
  sourceSets {
    commonMain.dependencies {
      // Public API: the fakes implement core's repository interfaces and expose its models.
      api(project(":shared:core"))
    }
  }
}
