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
Home (stale-while-revalidate, refresh when the app comes back). The UI freezes the list on its first composition and merges a refresh with
`FeedMerge`: the pages up to the one the viewer rests on never move, reorder or disappear (a refresh that ranks the watched channel last, because
it counts as seen, changes nothing under them); only what is ahead follows the refreshed order, minus streams that ended. Tested in `FeedMergeTest`.

## Players (`phone/.../discover/DiscoverScreen.kt`)

* **Two** `NativeVideoPlayer` instances for the whole tab; the page index parity picks one. The page the viewer rests on plays; the player of the
  next page has already resolved and prepared its stream and waits paused. Swiping on is therefore a `play()` call, not a load.
* Previews ask for the `360p30` rendition (`QualityPolicy` falls back to its default when a stream does not offer it) and are muted until the viewer
  turns the sound on.
* A player never shows another page's stream: a page draws the video only when `NativeVideoPlayer.loaded` is its channel and the status is
  `Playing`; until then it draws the thumbnail.
* The previews pause when the full player sheet is open, when the app stops (the player's own lifecycle rule) and when the tab is left (the players are
  released with the composition).
* Chat is a **read-only, light connection** (`FeedChatPresenter`, `LiteChatAssets`), not the player's presenter: messages only, the last twelve
  lines kept; no emote picker flow, no Twitch emote lists, no paging of the viewer's own emotes, no room modes, no zap list, no channel lookup
  (the channel id comes with the stream), no live 7TV connection (the third-party emotes are read once, from the cache when fresh) and channel badges
  cached for the last twenty channels. It lives in a `ViewModelStore` of its own, cleared as soon as the viewer leaves the page: one chat connection
  at most (EventSub's 10-point budget).
* **Audio focus:** the preview players are built with `handleAudioFocus = false`, so opening Discover does not pause the viewer's music or podcast.
  Focus is taken when the viewer turns the sound on and given back when they mute or the full player opens (`FeedAudio.takesFocus`).

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

## Clips

A **Live | Clips** switch at the top of the page changes the pager's content. `TwitchRepository.feedClips()` takes the four busiest categories, asks
Helix for their most viewed clips of the last seven days (`GET /clips?game_id=&started_at=`), merges them (at most 40, each clip once) and resolves
the channels' logins and pictures in one batched users call. The clip's file is resolved page by page with the same `GqlClipResolver` as the clip
player (unofficial, see docs/TWITCH_INTEGRATION.md). Same rules as the live pager: two players, the next clip prepared paused, nothing fetched ahead
when autoplay is not allowed, looping and muted until the sound is turned on, a tap opens the clip in the full player.

## Swipe cost (reasoned from the code, not measured)
The perf report showed a P90 of about 32 ms for the swipe. What changed, to move work out of the swipe itself:
* The video surface (`PlayerView`, a `SurfaceView`: expensive to create) is only built for the page the viewer rests on, never while a page slides in:
  the swipe draws a picture and text, and the video appears as soon as the page settles (its player has been prepared since the previous page).
* A fling moves **one page** (`PagerSnapDistance.atMost(1)`): a long fling no longer composes and decodes pages that go by in a blink.
* Everything that starts a player, a chat or a network call is keyed on the **settled** page (`LaunchedEffect(settled, ...)`), not on the scroll
  position; the chat additionally waits two seconds.
* The previous page's player is the next page's: it is reloaded (not left running) as soon as the page settles, and both are released with the tab.
* Chat: the light connection above is a fraction of the player's chat work (no emote list requests, no live connection).
Re-measure on a device with the frame benchmark before claiming a number.

## Clips: address expiry and retries
A resolved clip address is kept 10 minutes (`ClipUrlCache`, wall clock) and asked for again after that; a failed resolution is retried three times with a
growing wait before the page settles for its thumbnail. The week's clips list (`feedClips()`) is cached for 10 minutes.

## Accessibility
Each page exposes its two gestures as TalkBack custom actions and click labels: open the stream with chat, pause / resume the preview.

## Not done

* Handing the preview's player to the full player sheet (it loads the stream again, at the viewer's own quality).
* `DefaultPreloadManager`: its behaviour on live and low-latency HLS is not documented; the "warm neighbour" above is the simple version of the same idea.
* Measurements of data and battery on a real phone (to do on a device with `dumpsys batterystats` and `NetworkStatsManager`).
