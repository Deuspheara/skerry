import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFrameworkConfig

plugins {
  id("twitchplayer.kmp-apple-library")
  alias(libs.plugins.skie)
}

// SKIE turns suspend functions into `async`, StateFlow into AsyncSequence and sealed classes into Swift enums; it also uploads
// build analytics by default, which this build switches off.
skie {
  analytics {
    disableUpload.set(true)
    enabled.set(false)
  }
}

// The Apple implementations of the platform seams, the Koin entry point for Swift and the `TwitchShared` XCFramework that
// bundles every shared module (see apple/scripts/build-shared.sh and docs/APPLE_PLAN.md).
// The offline demo (fictional data for screenshots and tests) is a separate source directory so a release XCFramework can leave it out:
// `apple/scripts/build-shared.sh release` passes -PappleDemo=false. Everything else (check, debug builds) compiles and tests it.
val includeDemo = providers.gradleProperty("appleDemo").map { it.toBoolean() }.getOrElse(true)

kotlin {
  sourceSets {
    if (includeDemo) appleMain { kotlin.srcDir("src/appleDemo/kotlin") }
    commonMain.dependencies {
      // Everything Swift sees is exported through the umbrella framework (the `export` calls below need `api`).
      api(project(":shared:core"))
      api(project(":shared:stream"))
      api(project(":shared:chat"))
      api(project(":shared:player-api"))
      api(project(":shared:presenters"))
      implementation(libs.ktor.client.darwin)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
    }
  }

  val xcFramework = XCFrameworkConfig(project, "TwitchShared")
  targets.withType<KotlinNativeTarget>().configureEach {
    binaries.framework {
      baseName = "TwitchShared"
      isStatic = true
      export(project(":shared:core"))
      export(project(":shared:stream"))
      export(project(":shared:chat"))
      export(project(":shared:player-api"))
      export(project(":shared:presenters"))
      export(libs.kotlinx.coroutines.core)
      export(libs.kotlinx.collections.immutable)
      xcFramework.add(this)
    }
  }
}
