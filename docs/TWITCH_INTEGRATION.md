# Twitch integration research

Research snapshot: 2026-11-06. Twitch documentation can change; recheck it before implementation and release. This is an unaffiliated third-party client concept.

## Decision and open gate

Use only documented Helix, OAuth, EventSub, and official embedded player interfaces. Do not obtain media URLs, suppress ads, alter quality through hidden interfaces, scrape chat, or emulate undocumented endpoints.

**Player approval remains unverified.** Twitch's embedding documentation is framed around websites and requires an SSL domain and matching `parent`; it also forbids obscuring the embed and sets a 400×300 minimum for video. It does not explicitly state that a standalone Android TV application embedding a remotely hosted page inside Android WebView is an approved distribution model. A public Twitch developer issue reports WebView player errors, but that historical report is not definitive policy. Obtain written Twitch developer support confirmation and test on target Android TV hardware before treating this architecture as production viable. If Twitch declines, stop this player path and pursue a documented Twitch-approved option; do not substitute direct stream playback.

## OAuth Device Code

The Twitch OAuth docs explicitly identify Device Code Grant for limited-input devices such as set-top boxes and game consoles. Register a **public** client; no secret is required or shipped. Start with `POST https://id.twitch.tv/oauth2/device` using `client_id` and URL-encoded, space-delimited `scopes`. Display the returned `user_code` and `verification_uri`. Poll `POST https://id.twitch.tv/oauth2/token` with `client_id`, scopes, `device_code`, and grant type `urn:ietf:params:oauth:grant-type:device_code`, respecting `interval`, `expires_in`, and `authorization_pending`. Device and refresh codes are one-time use; public-client refresh tokens rotate and have 30-day inactivity expiry per current docs. Persist replacement refresh token immediately. Twitch says third-party apps maintaining OAuth sessions must call `/validate`.

### Scope plan

Request scopes only when needed and verify each endpoint's current authorization table during implementation:

| Feature | Candidate documented scope |
|---|---|
| Followed channels/streams | `user:read:follows` |
| Read chat via `channel.chat.message` | `user:read:chat` |
| Send chat via Helix | `user:write:chat` |
| User-specific emotes | `user:read:emotes` (only if using Get User Emotes) |

Public discovery (top categories, search, streams) generally uses app access where applicable; a public mobile client cannot safely hold a client secret to mint app tokens. Confirm which public endpoints accept user access tokens and whether app-token-only calls require an approved backend/client arrangement. Do not request candidate scopes speculatively. Global/channel emotes and badges have their own endpoint requirements; implement only after verifying them in the live API reference.

For this client, configure the Twitch app as a public client and serialize token refresh in one place. The general refresh-token page documents a `client_secret` request example but says public client refresh tokens expire 30 days after generation; the Device Code page describes public-client refresh without a secret and one-time refresh-token use. Follow the Device Code flow's public-client parameters, URL-encode refresh token form data, and atomically persist each replacement. The exact rotation behavior is a high-risk integration detail to verify with a live POC before shipping.

## Helix mapping

Following or unfollowing a channel is not possible from a third-party app: Twitch removed the Helix follow/unfollow
endpoint for third parties, and only "Get Followed Channels" is available. The phone app's "Follow" button therefore
opens the channel page on twitch.tv, and the TV app has no follow action.

Base URL `https://api.twitch.tv/helix`. Typed client uses Twitch's documented request headers and response envelopes. Relevant documented operations include Get Users, Get Followed Channels, Get Followed Streams, Get Streams, Get Top Games, Search Channels, Search Categories, Get Channel Information, Get Global/Channel Chat Emotes, Get Global/Channel Chat Badges, and Send Chat Message. Use `after` cursors for pagination and return cursors to the UI repository. Expand thumbnail template dimensions only using documented template placeholders. Handle empty data as offline/no-results; tolerate absent artwork. Cache small, short-lived discovery results and cancel obsolete search requests. Respect rate-limit headers and avoid per-card detail requests when list responses already contain fields.

Endpoint availability, required token type, user scope and condition fields are endpoint-specific; consult current reference at implementation. Do not infer fields or scopes from names.

## EventSub chat

Connect to `wss://eventsub.wss.twitch.tv/ws`. The first frame is `session_welcome`; subscribe promptly using the session ID. `channel.chat.message` version `1` is the documented message event. For an installed chat client using a user access token, Twitch documents `user:read:chat`; subscription condition requires `broadcaster_user_id` for the room and `user_id` for the account reading chat. Get both IDs from authenticated Get Users and selected channel API data. An app-token chatbot path has additional `user:bot` and broadcaster `channel:bot`/moderator authorization and is unnecessary for this client. WebSocket transport specifically uses the user's token; app-token-only bot examples are not interchangeable.

Handle welcome, notification, keepalive, reconnect, revocation, and close frames. Reconnect URLs are used as supplied; retain old socket until the migrated socket welcomes. After an unplanned socket loss, recreate subscriptions; Twitch states events lost during recovery are not replayed. Deduplicate by metadata `message_id` because notifications can be resent. Parse rich message fragments, badges, color, IDs, and timestamps; do not flatten fragments to a string. Bound in-memory messages. EventSub is not a historical chat archive.

Send through documented Helix `POST /chat/messages`; current reference requires `broadcaster_id`, `sender_id`, and `message`, with `user:write:chat` for a user token. Do not display a sent message as confirmed until the response arrives: inspect the response's `is_sent` and any `drop_reason` as well as HTTP success. Handle response status, failure reasons, and rate limits.

## Phase-two POC acceptance checklist

1. **Player:** after Twitch confirms the standalone app/WebView distribution model, load one official channel embed from the actual controlled HTTPS `parent` host on a physical Android TV. Check 400×300 minimum, unobscured player, controls, ads, audio, remote keys, channel switch, background/foreground and destruction/recreation. Emulator or desktop success is not acceptance.
2. **Device code:** use a registered public client; verify device request and interval polling, pending/denied/expired/approved paths, `/validate` at startup and hourly, refresh and token replacement, and revoke/logout behavior. Never check secrets or live tokens into the repo.
3. **EventSub chat:** authorize only `user:read:chat` for receive POC; connect, receive welcome, create the v1 subscription with both IDs, decode one real message with fragments/badges/color, dedupe, process keepalive/reconnect/revocation and confirm no replay after disconnect. Add `user:write:chat` only when separately validating send path. Avoid storing chat content beyond the bounded in-memory buffer.

The OAuth and chat POCs require a registered Twitch Client ID and a user who can authorize the app. The repository currently has neither, so no real authenticated Twitch call has been validated.

## Embedded video

Official embed: `https://player.twitch.tv/` with a channel or video identifier plus `parent=<controlled-hostname>`. Serve the page from HTTPS on a controlled domain; do not use the app package name as `parent`. Keep player window at least 400×300 and fully visible/unobscured, preserve Twitch controls and ads, and allow user-initiated playback where autoplay is restricted. Keep native controls outside the player rectangle. Do not treat `parent` as an authorization for native-app distribution: it verifies the embedding domain, not business/policy approval.

Investigation required before POC: get direct policy confirmation for WebView on Android TV; establish whether a hosted page can be used by a standalone distributed client; confirm current mobile/WebView behavior, fullscreen and remote key handling, autoplay/audio constraints; then validate physical TV lifecycle, playback, and channel switching. The docs alone do not settle those questions.

### Native WebView boundary in this repository

The Android playback component accepts an explicit controlled HTTPS hosted-page URL and exact parent hostname; it never substitutes the APK id. Its `player` route receives a channel login and the configured `parent` query values. The page at that route must itself load Twitch's official player and meet Twitch's embed requirements. No app JavaScript bridge is exposed; the WebView only allows top-level navigation to the configured host and disables file/content access and automatic window creation. The player component draws no native overlay over the embed and requires a user gesture for media playback. A host is intentionally absent from defaults, so playback remains unavailable until deployment config supplies a real controlled HTTPS origin and Twitch approval is obtained.

This implementation boundary is not evidence that WebView distribution is approved or that Twitch playback works on a TV. The hosting domain, hosted page behavior, Twitch confirmation, and real-TV compatibility remain release gates.

## Sources

- [OAuth tokens and Device Code Grant](https://dev.twitch.tv/docs/authentication/getting-tokens-oauth)
- [OAuth scopes](https://dev.twitch.tv/docs/authentication/scopes/)
- [Refreshing tokens](https://dev.twitch.tv/docs/authentication/refresh-tokens/)
- [Validating tokens](https://dev.twitch.tv/docs/authentication/validate-tokens/)
- [Helix API reference](https://dev.twitch.tv/docs/api/reference/)
- [Chat authentication](https://dev.twitch.tv/docs/chat/authenticating/)
- [EventSub WebSocket handling](https://dev.twitch.tv/docs/eventsub/handling-websocket-events/)
- [EventSub subscription types](https://dev.twitch.tv/docs/eventsub/eventsub-subscription-types/)
- [Video and Clips embeds](https://dev.twitch.tv/docs/embed/video-and-clips/)
- [Embedding requirements](https://dev.twitch.tv/docs/embed/)
- [Twitch embed issue concerning Android WebView](https://github.com/twitchdev/issues/issues/149) (historical implementation report, not policy)


## Third-party emotes (7TV, BTTV, FFZ)

Not Twitch APIs and not authenticated; each is public and provider-owned. Verified on 2026-11-06 against live responses and the providers' open-source clients (github.com/SevenTV, github.com/night/betterttv, FrankerFaceZ API docs):

| Provider | Global | Channel (Twitch user id) | Live updates | Notes |
|---|---|---|---|---|
| 7TV v3 | `GET https://7tv.io/v3/emote-sets/global` | `GET https://7tv.io/v3/users/twitch/{id}` (404 = no 7TV account) | EventAPI v3 server-sent events: `GET https://events.7tv.io/v3@emote_set.update<object_id={setId}>` (documented as the recommended transport; heartbeat ~46 s) | Channel payload is large (about 2.3 MB for a 960-emote set); it is decoded off the main thread once per 5 min at most. CDN `https://cdn.7tv.app/emote/{id}/2x.webp` and `2x_static.webp`. |
| BTTV v3 | `GET https://api.betterttv.net/3/cached/emotes/global` | `GET https://api.betterttv.net/3/cached/users/twitch/{id}` (`channelEmotes` + `sharedEmotes`) | none used (periodic reload) | CDN `https://cdn.betterttv.net/emote/{id}/2x.webp` (animated if the emote is) and `.../static/2x.webp`. Some animated 2x files are several MB: stills first, animation only on budget. |
| FFZ v1 | `GET https://api.frankerfacez.com/v1/set/global` (`default_sets`) | `GET https://api.frankerfacez.com/v1/room/id/{id}` (`room.set`) | none used (periodic reload) | `urls` are stills, `animated` (WebP) only on animated emotes. `modifier` emotes are skipped. |

These providers can change or disappear without notice (7TV is mid-migration to a newer API; v3 still answered on the date above). Every provider failure is isolated and silent: that provider's emotes render as plain text.

Twitch's own emotes need no lookup to be drawn in a message: `channel.chat.message` fragments carry the emote id and formats, and the image URL is the documented template `https://static-cdn.jtvnw.net/emoticons/v2/{id}/{static|animated}/dark/2.0`. The emote picker of the phone app does list them, so it calls Helix Get Global / Channel Chat Emotes (no scope) and, with `user:read:emotes`, Get User Emotes: see [EMOTES.md](EMOTES.md). `channel.chat.notification` needs the same `user:read:chat` scope as the message subscription.


## Viewer experience features (2026-11-07)

Checked against the live documentation on that date (dev.twitch.tv: EventSub subscription types, manage subscriptions, WebSocket events, Helix reference). Recheck before release.

### Scopes

The device flow now asks for `user:read:follows`, `user:read:chat`, `user:write:chat` and `user:read:emotes` (the last one only feeds the emote picker; a session without it works, see [EMOTES.md](EMOTES.md)). A session created before `user:write:chat` existed keeps working for everything else; sending a message answers "sign out and in again" (`SessionRepository.hasScope`, and a 401/403 from Helix).

### EventSub on the watched channel

One WebSocket per watched channel. `channel.chat.message` is required; everything else is best effort (a failed or revoked bonus subscription is silent):

| Type (version) | Condition | Scope | Used for |
|---|---|---|---|
| `channel.chat.notification` (1), `channel.chat.message_delete` (1), `channel.chat.clear_user_messages` (1), `channel.chat.clear` (1), `channel.chat_settings.update` (1) | `broadcaster_user_id` + `user_id` | `user:read:chat` | notices, removing moderated lines, room modes |
| `stream.offline` (1) | `broadcaster_user_id` | none | "stream ended" banner |
| `channel.update` (2) | `broadcaster_user_id` | none | live title / category, banner |
| `channel.raid` (1) | `from_broadcaster_user_id` | none | raid banner, optional "follow raids" setting |

Cost: a WebSocket allows 10 points; types that name a user but need no authorization (`stream.offline`, `channel.update`, `channel.raid`) cost 1 each with a viewer token, so a watched channel uses 3. The chat types cost 0 because the viewer granted `user:read:chat`.

**Not possible for a viewer:** polls, predictions, hype train and ad breaks (`channel.poll.*`, `channel.prediction.*`, `channel.hype_train.*`, `channel.ad_break.begin`) need `channel:read:*` scopes of the **broadcaster**, so they cannot be used for another channel. And `stream.online` for all follows would exceed the 10 points with a handful of channels: "X is live now" instead polls the follows list every 90 s (`LiveWatcher`), only while the app is in front.

### Helix additions

- `GET /chat/settings` (no scope): initial room modes; `channel.chat_settings.update` keeps them live.
- `POST /chat/messages` (`user:write:chat`): the message is only reported as sent when `is_sent` is true; otherwise `drop_reason.message` is shown. The message is never added locally: it comes back through chat like everyone's.
- `GET /schedule` (no scope; 404 = no schedule): next streams on the channel page. The response's `data` is one object holding `segments`.
- `tags` and `language` on `GET /streams`.
- `GET /clips` (no scope) lists a channel's most viewed clips (the Helix `id` is the slug). Helix gives no playable URL, so playback uses the same unofficial GraphQL interface as the live resolver (`GqlClipResolver`): one query returns an access token and the MP4 address of each quality, and the file only answers with `?sig=...&token=...` appended (checked against the live service on 2026-11-07: unsigned 401, signed 206). If Twitch changes it, the clip screen says the clip is unavailable; nothing else depends on it. Starting a clip ends the live session.

### Playback statistics and ad marker (native player)

ExoPlayer provides latency (`currentLiveOffset`, from the playlist's program date-time), buffer, bitrate, bandwidth estimate, dropped frames and the playing format; Settings > Playback stats shows them over the video. The ad countdown is read from the media playlist's `#EXT-X-DATERANGE` tags (`CLASS="twitch-stitched-ad"` / `ID="stitched-ad-..."`, `START-DATE`, `DURATION`). These tags are **not documented by Twitch** and may change: anything unexpected is ignored, and the app only tells the viewer an ad is on; it never skips, hides or shortens it. This extends the earlier rule: the native player already reads Twitch's HLS (see NATIVE_PLAYER.md); ads stay in the stream untouched.
