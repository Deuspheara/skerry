# Legal notice

**Skerry is an unofficial, independent project.** It is not affiliated with, endorsed by, or sponsored by Twitch
Interactive, Inc. or Amazon.com, Inc. Twitch, the Twitch logo and the Glitch logo are trademarks of Twitch Interactive,
Inc. or its affiliates. "for Twitch" is used here only to say what the software is compatible with.

Skerry contains no Twitch logo, no Glitch, no Twitch emotes and no Twitch source code or assets. Its screenshots show
fictional channels, chat and a test-pattern clip (demo mode), never real streamers.

This file is a plain-language statement of how the project works. It is not legal advice, and it does not change what
Twitch's own terms say. You are responsible for following them.

## What the apps use

| Use | Interface | Documented by Twitch |
|---|---|---|
| Sign-in | OAuth Device Code flow with the developer's own registered public client id (there is no client secret) | yes |
| Channels, categories, follows, search, clips lists | Helix API | yes |
| Live chat | EventSub WebSocket | yes |
| Video, official | Twitch embedded player (`player.twitch.tv`) in a WebView, with Twitch's controls and ads untouched | yes |
| Video, native (optional, off by default) | The playback access-token request (`gql.twitch.tv`) and the playlist service (`usher.ttvnw.net`), the same calls Twitch's own web player makes | **no** |

### Native playback uses undocumented interfaces

Twitch's Developer Services Agreement says developers may access only documented interfaces without Twitch's written
permission, and Twitch's Terms of Service restrict getting around access controls. The native player calls
interfaces that are not documented. **Using it may violate those terms**, may stop working at any time, and may lead
Twitch to restrict the account or the client id involved.

For that reason:

- This repository ships **no client id for it**. `playbackClientId` is empty. Native playback only runs if you build the apps
  yourself and set the id yourself, and you do so at your own risk.
- The Android TV app uses the official embed by default. The phone and Apple apps have no embed engine, so without that
  id they cannot play video.
- Ads are never skipped, hidden, shortened or replaced. The app only reads the ad countdown from the playlist to tell the
  viewer an ad is running.
- No Twitch client-integrity token is generated or imitated, and none of Twitch's player code is copied.

If you are a rights holder and think something here should change, open an issue and it will be looked at promptly.

## Third-party content

- **Emotes** (7TV, BetterTTV, FrankerFaceZ) are fetched at runtime from each service's own servers and are never bundled
  or re-hosted. They belong to their creators and the services' terms apply. The demo emotes are drawn in code.
- **Chat, channel and video data** belong to Twitch and the streamers. The apps keep short-lived snapshots on the
  device only, to start faster, and do not run a server, a database or a logging service.

## Licence

The source code is licensed under the [Apache License 2.0](LICENSE) (see [NOTICE](NOTICE)). The licence grants no
rights to use trademarks, including "Skerry" if that name is later registered by the maintainer. The Inter typeface is under the
SIL Open Font License; libraries keep their own licences.

The software is provided "as is", without warranty of any kind (see sections 7 and 8 of the licence).
