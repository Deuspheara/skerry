# Official Twitch player page hosting

The app serves native Compose UI. Only the Twitch video embed runs in WebView. The packaged page at `player-site/index.html` renders Twitch's official player iframe; it never requests or transforms a stream URL. Video and ads are delivered by Twitch.

## Deploy

Host `player-site/index.html` as the root `index.html` on any static HTTPS host you control. No backend or video proxy is needed. Set Android's `TwitchPlayerConfig` to the public HTTPS origin and exact hostname (for example, origin `https://tv.example.org`, parent `tv.example.org`). The app opens `/?channel=<login>&parent=<hostname>`.

### Cloudflare Pages

This repository has no Git remote configured, so the prepared deployment is a static Direct Upload. In Cloudflare Pages, create a project with **Direct Upload** and upload `player-site/index.html` as the root `index.html`, or upload the prepared `build/zavori-pages-upload.zip`. Do not upload the Android project. Then add `zavori.app` under the Pages project's **Custom domains**. If Cloudflare requires the domain to be added to the account first, add/activate its DNS zone and let Pages create the required DNS record. Cloudflare Pages serves the player page at the site root.

Cloudflare documents that a project created with Direct Upload cannot later be converted into a Git-integrated project; automatic Git deploys would require creating a separate project. Direct Upload is suitable for this static embed page and can be redeployed with a replacement archive.

The page verifies that its `parent` equals `location.hostname`, validates the channel login, and constructs `https://player.twitch.tv/` with the required parent parameter. It contains no controls that obscure the embed.

## Still required before release

- Obtain Twitch's confirmation that this standalone Android TV/WebView distribution model is acceptable; the published embed docs do not explicitly address it.
- Deploy to the domain and verify the page returns HTTPS with correct `parent` on an actual Android TV device.
- Configure `TwitchPlayerConfig` with that origin/hostname and verify D-pad remote behavior, fullscreen, audio, ads, lifecycle, and channel switching.


## Quality menu and persistence (hosted page contract)

The app drives the page through `window.tp` (via `evaluateJavascript`, no bridge): `play()`, `pause()`, `toggleMute()`,
`getQualities()` and `setQuality(id)`. `getQualities()` returns JSON text `{"qualities":[{"id","name"}],"selected"}` (or `null`
before the stream plays); `id` is the quality `group` that Twitch's `setQuality` takes (`chunked` = source, `auto`).
The chosen id is stored in the page's `localStorage` and re-applied once each stream is playing, so 1080p stays 1080p across
channels and launches. **Redeploy `player-site/index.html`** for the quality button to work: with an older page the app
just shows "Qualities not available yet".

## Mini player

The player is hosted above the navigation stack (`PlayerHost`), so the same WebView keeps playing when the viewer goes to
another screen from the dock. It then shows as a 534x300 dp viewport scaled to 60 % in the bottom-right corner (the embed keeps
its 400x300 minimum viewport). Back from the full-screen player ends the session; the dock does not.
