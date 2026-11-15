# Privacy

Skerry has no server, no account of its own, no advertising and no analytics or crash-reporting library. Nothing is
sent to the maintainer. This is what happens on your device.

## What stays on the device

- **Your Twitch sign-in token**, after you approve the device code on twitch.tv. It is stored in the Android Keystore or the
  Apple Keychain and is deleted when you sign out.
- **Settings** (language, chat options, player options) and **watch progress** for replays.
- **Short-lived snapshots** of lists you saw (to start faster). They are overwritten on refresh and removed when you clear the
  app's data.

## Who the app talks to

| Service | Why | Data sent |
|---|---|---|
| Twitch (`id.twitch.tv`, `api.twitch.tv`, EventSub, `static-cdn.jtvnw.net`) | sign-in, lists, chat, images | your token, the channel or category you open, chat messages you send |
| Twitch video hosts, only when you watch | the stream | what any player sends: IP address, device and player information |
| 7TV, BetterTTV, FrankerFaceZ | emotes in chat | the channel id and your IP address |
| The Android TV embed page host you configure (`twitchPlayerHost`) | the official embed | your IP address and the channel you watch |

Twitch's own privacy notice applies to everything you do on Twitch through the app. Messages you send are public chat.

## Your choices

Sign out in Settings to delete the token, and revoke the app on twitch.tv > Settings > Connections. Clearing the app's data
removes everything stored locally.

## Contact

Open an issue on the project's GitHub page. Do not post tokens or private data there.
