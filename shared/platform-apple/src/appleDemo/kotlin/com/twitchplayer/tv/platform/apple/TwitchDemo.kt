package com.twitchplayer.tv.platform.apple

/**
 * Starts the app on the offline fictional data (launch argument `-demo`): screenshots, UI tests, no account and no network. [signedIn] false
 * shows the sign-in flow. This file and `AppleDemo.kt` live in the `appleDemo` source directory, which `apple/scripts/build-shared.sh release`
 * leaves out (`-PappleDemo=false`): the release XCFramework contains none of the demo code, and the Swift side only calls this under `#if DEBUG`.
 *
 * From Swift: `TwitchDemoKt.startDemo(TwitchApp.shared, signedIn: true)`.
 */
fun startDemo(app: TwitchApp, signedIn: Boolean): TwitchGraph = app.startWith("demo-client-id", listOf(appleDemoModule(signedIn)))
