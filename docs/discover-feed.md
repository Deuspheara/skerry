# Discover: the vertical live feed

The **Discover** tab (second tab of the phone bar) is one live stream per screen, swiped vertically, in the manner of the official app's
discovery feed. It is built on documented APIs only; what it cannot know (Twitch's own recommendation ranking) it replaces with a simple rule.

## What the viewer sees

* A full-bleed page per stream: the picture (muted by default), the streamer's picture and name, the title, the category, the viewer count.
* A rail on the right: sound on/off, chat on/off, open the channel page, open the full player with chat.
* A **tap** on the picture opens the full player sheet with chat (the same `PlaybackTarget.Live` as everywhere); a **long press** pauses or
  resumes the preview.
* The last four chat lines over the picture, once the viewer has stayed on a page for two seconds.

## Where the streams come from (`shared/presenters`, `FeedRanking`)

`FeedRanking.rank(followed, top, language, seen)`: the viewer's followed channels that are live (at most eight, most viewers first), then the
busiest streams (`TwitchRepository.recommended()`) in the viewer's language (a stream with an unknown language stays), each channel once, at most
40, channels already rested on during the session pushed to the end. Pure function, unit-tested. `FeedPresenter` wraps it in the same `Loader` as
Home (stale-while-revalidate, refresh when the app comes back). The UI freezes the list it started with once the viewer has swiped, so a refresh
never moves streams under them.

## Players (`phone/.../discover/DiscoverScreen.kt`)

* **Two** `NativeVideoPlayer` instances for the whole tab; the page index parity picks one. The page the viewer rests on plays; the player of the
  next page has already resolved and prepared its stream and waits paused. Swiping on is therefore a `play()` call, not a load.
* Previews ask for the `360p30` rendition (`QualityPolicy` falls back to its default when a stream does not offer it) and are muted until the viewer
  turns the sound on.
* A player never shows another page's stream: a page draws the video only when `NativeVideoPlayer.loaded` is its channel and the status is
  `Playing`; until then it draws the thumbnail.
* The previews pause when the full player sheet is open, when the app stops (the player's own lifecycle rule) and when the tab is left (the players are
  released with the composition).
* Chat connects through a normal `PlayerViewModel` kept in a `ViewModelStore` of its own, cleared as soon as the viewer leaves the page: one chat
  connection at most (EventSub's 10-point budget).

## Autoplay and data (`FeedAutoplay`, `NetworkStatus`)

Settings > Playback > **Discover previews**: *On Wi-Fi only* (default), *Always*, *Off, tap to play*. The connection comes from the platform
(`NetworkStatus.metered`, Android: `ConnectivityManager`, a network that cannot be read counts as metered). When autoplay is not allowed the page shows
the thumbnail and a "Tap to play" prompt, and **nothing is preloaded**.

## Ads and Twitch's terms: unconfirmed

Every preview is a live HLS session, and the native player plays whatever Twitch serves, which can include a pre-roll ad when a stream starts.
The app never skips, shortens or hides ads. The official app avoids pre-rolls in its previews by design; whether the Developer Services Agreement allows
a third-party client to autoplay several streams is **not confirmed** (the text could not be retrieved, see docs/twitch-feature-gap.md). The Settings help
text says so, autoplay is off mobile data by default, and "Off" is one tap away. The question should go to Twitch developer support before this is released
widely.

## Not done

* Handing the preview's player to the full player sheet (it loads the stream again, at the viewer's own quality).
* `DefaultPreloadManager`: its behaviour on live and low-latency HLS is not documented; the "warm neighbour" above is the simple version of the same idea.
* Measurements of data and battery on a real phone (to do on a device with `dumpsys batterystats` and `NetworkStatsManager`).
