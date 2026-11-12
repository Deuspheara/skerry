# Emotes

What the chat shows and what the phone's emote picker offers, where each list comes from, and what was and was not verified.

## Sources

| Source | Lists | Call | Needs |
|---|---|---|---|
| Twitch | global, the channel's own | Helix `GET /chat/emotes/global`, `GET /chat/emotes?broadcaster_id=` | any token, no scope |
| Twitch | the viewer's own (subscriptions, bits tiers, other channels) | Helix `GET /chat/emotes/user?user_id=` (paged, up to 30 pages) | `user:read:emotes` |
| 7TV | global, channel (live updates over the 7TV event stream) | `7tv.io/v3/emote-sets/global`, `7tv.io/v3/users/twitch/{id}` | nothing |
| BetterTTV | global, channel + shared | `api.betterttv.net/3/cached/emotes/global`, `.../cached/users/twitch/{id}` | nothing |
| FrankerFaceZ | global, room | `api.frankerfacez.com/v1/set/global`, `.../v1/room/id/{id}` | nothing |

Twitch images use the documented CDN template `https://static-cdn.jtvnw.net/emoticons/v2/{id}/{static|animated}/dark/2.0`; `animated` only where the API lists it in `format`.
Third-party URLs and formats are described in [TWITCH_INTEGRATION.md](TWITCH_INTEGRATION.md#third-party-emotes-7tv-bttv-ffz).

## Code

- `shared/chat`: `EmoteProvider` (one per third-party source, each with its `EmoteSource`), `EmoteRepository` (merges them; `snapshots()` returns the merged name map chat resolves words with **and** the same emotes as picker lists), `ChatAssetsRepository` (adds Twitch's own lists, `buildTwitchLists`, and the viewer's emotes). A provider that fails loses only its own lists: chat and the other tabs are unaffected. Metadata is cached in memory (globals 1 h, channels 5 min, the viewer's emotes 10 min).
- `shared/presenters`: `PlayerPresenter.emotePicker` (`EmotePickerState`: groups, recents, access), `PlayerEvent.PickedEmote`, the pure `search`, `emoteQueryAt`, `insertEmote`, `completeEmote` (all unit-tested).
- `shared/core`: `SettingsRepository` keeps the 24 most recent emote names (`RecentEmotesRepository`); they are forgotten with the rest of the account's data when the session ends.
- `:phone`: `ChatComposer` (suggestion strip, message pill, picker), `EmotePanel` (search, tabs, grid, long-press preview), the chat line renderer (unchanged: it already draws still, animated and zero-width emotes inline).

## Picker behaviour

- The picker takes the keyboard's place (the panel is as tall as the keyboard was last seen, so the chat does not jump). The pill's emote button, a tap on the field, or Back brings the keyboard back; Back never leaves the player while the panel is open.
- Tabs: Recent (when there are recents), Twitch, 7TV, BTTV, FFZ (only sources that have emotes). The tab shown first stays until the viewer picks another. Search covers every list and ignores locked emotes.
- A tap inserts the name at the cursor with single spaces around it and stays open; a long press shows the emote large, animated unless animations are off (system animation scale 0 or the in-app setting), with its name, source and list, and an Insert button.
- Typing `:` at a word start plus two characters shows matching emotes above the pill (recents first); a tap completes the name.
- Nothing is sent but text. Sending is the existing `SendChat` path. Twitch emote names become pictures for everyone; **7TV, BTTV and FFZ names are plain words to Twitch**: they turn into pictures only for viewers who have that extension, and in this app, which draws them locally.

## What the viewer may use

- With `user:read:emotes` (requested at sign-in since this change): the channel's emotes the viewer owns are listed under Channel, the others under "Subscribe to use these" (dimmed, lock badge, not insertable), plus the subscriber emotes of other channels.
- A session from before that scope existed keeps working: every channel emote is listed as usable and the Twitch tab says to sign out and in again to see which ones the viewer can use.
- The 2024 Twitch changelog removed `broadcaster_id` from the user-emotes call and the list may not contain every emote type, so **follower emotes are never marked locked**, and anything else Twitch leaves out of the list would show as locked even if usable.
- Signed out, or if the call fails: only global and channel emotes, all selectable.

## Verified, and not

- Unit tests with fake HTTP (Ktor `MockEngine`) cover: the lists and their order, usable / locked / other-channel split, paging, the missing scope, a failing call (user, global, a provider), the CDN URLs, caching of the viewer's emotes across channels, and the pure search / completion logic. Instrumented tests on the demo chat cover the picker, search, locked emotes, `:` suggestions, Back, sending.
- **Not verified against the live services in this pass:** no real Twitch account was signed in, so the Helix emote calls and the paging of `chat/emotes/user` ran only against fake responses shaped from the documentation and developer-forum reports (the forums reported blank `emote_type` for bits emotes and an `emote_type` list that has grown, see below). The 7TV, BTTV and FFZ parsers were already tested against trimmed real payloads and still are; they were not re-hit live.
- 7TV's v3 event stream repository is archived in favour of a newer API ([github.com/SevenTV/EventAPI](https://www.github.com/SevenTV/EventAPI)); v3 still answered when the existing providers were written. If it stops, the periodic reload (10 min) still updates channel sets.
- Chatterino's `:` completion and popup layout could not be confirmed from documentation; the picker follows the common pattern of Twitch / Discord / 7TV (tabs per source, search, tap to insert, long press to preview).

Sources: [Twitch changelog and forum threads on Get User Emotes](https://discuss.dev.twitch.tv/t/getuseremotes-endpoint-update/32193), [Retrieve the emotes usable by a user](https://discuss.dev.twitch.tv/t/retrieve-the-emotes-usable-by-a-user/20165), [Twurple `HelixUserEmote`](https://twurple.js.org/reference/api/classes/HelixUserEmote.html), [Chatterino third party services](https://wiki.chatterino.com/Third_party_services/).
