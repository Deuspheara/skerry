package com.twitchplayer.tv.demo

import android.content.Context
import com.twitchplayer.tv.feature.player.data.ChatAssets
import com.twitchplayer.tv.feature.player.data.ChatAssetsSource
import com.twitchplayer.tv.feature.player.data.ChatSource
import com.twitchplayer.tv.feature.player.data.chat.ChannelNotice
import com.twitchplayer.tv.feature.player.data.chat.ChatBadge
import com.twitchplayer.tv.feature.player.data.chat.ChatCheer
import com.twitchplayer.tv.feature.player.data.chat.ChatEmote
import com.twitchplayer.tv.feature.player.data.chat.ChatEvent
import com.twitchplayer.tv.feature.player.data.chat.ChatFragment
import com.twitchplayer.tv.feature.player.data.chat.ChatMention
import com.twitchplayer.tv.feature.player.data.chat.ChatMessage
import com.twitchplayer.tv.feature.player.data.chat.ChatMessageContent
import com.twitchplayer.tv.feature.player.data.chat.ChatReply
import com.twitchplayer.tv.feature.player.data.chat.NoticeKind
import com.twitchplayer.tv.feature.player.data.chat.RoomState
import com.twitchplayer.tv.feature.player.data.emotes.Emote
import com.twitchplayer.tv.feature.player.data.emotes.EmoteGroup
import com.twitchplayer.tv.feature.player.data.emotes.EmoteSection
import com.twitchplayer.tv.feature.player.data.emotes.EmoteSource
import com.twitchplayer.tv.feature.player.data.emotes.UserEmoteAccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.File

/** Messages the viewer "sends" in the demo come back through chat like real ones. */
internal object DemoBus {
    val events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 16)
}

/** Demo extras (moderation, room modes, raid, clips): `files/demo-events` or `settings put global twitchtv_demo_events 1`. */
internal fun demoEvents(context: Context): Boolean = File(context.filesDir, "demo-events").exists() || android.provider.Settings.Global.getInt(context.contentResolver, "twitchtv_demo_events", 0) == 1

/**
 * Fictional chat with third-party style emotes, to see and measure the real renderer without an account.
 * Normal mode: ~1 message/s. Heavy mode (`adb shell run-as com.twitchplayer.tv touch files/demo-chat-heavy`, or `adb shell settings put global twitchtv_demo_chat_heavy 1`): ~16/s, most with
 * several emotes, half of them animated: what a busy chat looks like.
 */
internal class DemoChat(private val context: Context) : ChatSource {
    private val users = listOf(
        "mireille_88" to "#BF94FF",
        "Tom_Lefebvre" to "#EFEFF1",
        "ClaraPlays" to "#FF7F50",
        "nuit_blanche" to "#0000FF",
        "Gaspard" to "#2E8B57",
        "petitpois" to null,
        "Lagom_TV" to "#1E1E8C",
        "zouzou" to "#FF69B4",
    )
    private val lines = listOf(
        "Salut tout le monde", "Quelqu'un sait quelle musique est en fond ?", "GG bien joué !", "On est combien ce soir ?",
        "La qualité du flux est top aujourd'hui", "Merci pour le live, ça fait du bien après le boulot", "Allez, on y croit",
        "Salut depuis Lyon !", "J'ai raté le début, un résumé ?", "Belle ambiance ici", "Ce passage était incroyable, on le refait ?",
        "Je mets le son plus fort", "Première fois ici, c'est super bien fait",
    )
    private val thirdParty = listOf("heartPop", "gg", "peepoHey", "catJam", "clapClap", "dancePls", "spinHype")
    private val animatedNames = setOf("catJam", "clapClap", "dancePls", "spinHype")

    override fun events(broadcasterId: String, userId: String): Flow<ChatEvent> = channelFlow {
        launch { DemoBus.events.collect { send(it) } }
        val extras = demoEvents(context)
        // A marker file works on debug builds; the (non-debuggable) benchmark build is switched with
        // `adb shell settings put global twitchtv_demo_chat_heavy 1`.
        val heavy = File(context.filesDir, "demo-chat-heavy").exists() ||
            android.provider.Settings.Global.getInt(context.contentResolver, "twitchtv_demo_chat_heavy", 0) == 1
        var n = 0
        if (extras) send(ChatEvent.Room(RoomState(slowSeconds = 30, subscribersOnly = true)))
        repeat(if (heavy) 30 else 14) { send(event(n++, heavy)) }
        while (true) {
            delay(if (heavy) 60 else 1_000)
            send(event(n++, heavy))
            if (extras) {
                when (n) {
                    16 -> send(ChatEvent.Deleted("demo-15"))
                    18 -> send(ChatEvent.UserCleared("petitpois"))
                    20 -> send(ChatEvent.Channel(ChannelNotice.Updated("Nouveau titre du live", "Échecs")))
                    24 -> send(ChatEvent.Channel(ChannelNotice.Raid("Valmoor", "Lagom_TV", DemoData.recommended.first().login, 342)))
                    40 -> send(ChatEvent.Channel(ChannelNotice.Offline))
                }
            }
        }
    }

    private fun event(n: Int, heavy: Boolean): ChatEvent {
        if (!heavy && n % 17 == 16) return ChatEvent.Notice("demo-notice-$n", "Marlou vient de s'abonner pour 6 mois !", NoticeKind.Resub)
        if (!heavy && n % 23 == 22) return ChatEvent.Notice("demo-gift-$n", "ClaraPlays offre 5 abonnements à la communauté !", NoticeKind.GiftCommunity)
        if (!heavy && n % 29 == 28) return ChatEvent.Notice("demo-announce-$n", "Le live continue jusqu'à minuit, n'oubliez pas de vous hydrater.", NoticeKind.Announcement, "BLUE")
        return ChatEvent.Message(message(n, heavy))
    }

    private fun previousReply(n: Int): ChatReply {
        val (parent, _) = users[(n - 1) % users.size]
        return ChatReply("demo-${n - 1}", "Quelqu'un sait quelle musique est en fond ?", parent, parent.lowercase())
    }

    private fun message(n: Int, heavy: Boolean): ChatMessage {
        val (name, color) = users[n % users.size]
        var seed = n * 7919 + 17
        fun rnd(bound: Int): Int {
            seed = seed * 1103515245 + 12345
            return ((seed ushr 16) and 0x7FFF) % bound
        }
        val fragments = ArrayList<ChatFragment>()
        val emoteCount = if (heavy) {
            1 + rnd(4)
        } else if (rnd(10) < 6) {
            1 + rnd(2)
        } else {
            0
        }
        val builder = StringBuilder(lines[rnd(lines.size)])
        if (!heavy && n % 11 == 5) {
            fragments += ChatFragment("mention", "@Camille", mention = ChatMention("camille"))
            builder.insert(0, "@Camille ")
        }
        // Words: text, then emotes spread through it (third-party names are plain words, Twitch emotes are fragments).
        fragments += ChatFragment("text", builder.toString().let { if (fragments.isEmpty()) it else it.removePrefix("@Camille") })
        repeat(emoteCount) {
            when (rnd(6)) {
                0 -> fragments += ChatFragment("emote", "smileTV", ChatEmote("demo-smile", format = listOf("static")))

                1 -> fragments += ChatFragment("emote", "starTV", ChatEmote("demo-star", format = listOf("static")))

                else -> {
                    // Heavy mode: half of the third-party emotes are animated.
                    val pool = if (heavy && rnd(2) == 0) thirdParty.filter { it in animatedNames } else thirdParty
                    fragments += ChatFragment("text", " ${pool[rnd(pool.size)]} ")
                }
            }
            // Now and then an overlay (zero-width) emote right after: rain on the previous emote, or sparkles.
            if (rnd(5) == 0) fragments += ChatFragment("text", if (rnd(2) == 0) " rainOverlay " else " sparkleZW ")
        }
        val text = fragments.joinToString("") { it.text }
        val badges = buildList {
            if (n % 3 == 0) add(ChatBadge("subscriber", "1"))
            if (n % 8 == 1) add(ChatBadge("moderator", "1"))
            if (n % 13 == 4) add(ChatBadge("vip", "1"))
            if (n % 29 == 0) add(ChatBadge("broadcaster", "1"))
        }
        return ChatMessage(
            id = "demo-$n", broadcasterId = "b", userId = "u${n % users.size}", username = name, login = name.lowercase(),
            content = ChatMessageContent(text, fragments), badges = badges, color = color,
            messageType = if (n % 19 == 7) "channel_points_highlighted" else "text",
            cheer = if (n % 23 == 9) ChatCheer(100) else null,
            // Every seventh message answers the one before it, so the reply line has something to show.
            reply = if (!heavy && n > 0 && n % 7 == 3) previousReply(n) else null,
        )
    }
}

/** Badges and third-party emotes of the demo channel (the demo analogue of Helix badges + 7TV/BTTV/FFZ). */
internal object DemoChatAssets : ChatAssetsSource {
    private fun still(name: String, animated: Boolean = false, zeroWidth: Boolean = false, label: String = name) = Emote(
        name = label,
        url = DemoEmoteArt.url(name, false),
        animatedUrl = if (animated) DemoEmoteArt.url(name, true) else null,
        zeroWidth = zeroWidth,
        aspect = DemoEmoteArt.specs.getValue(name).first,
    )

    private val thirdParty = listOf(
        still("heart", label = "heartPop"), still("gg", label = "gg"), still("smile", label = "peepoHey"),
        still("bounce", animated = true, label = "catJam"), still("clap", animated = true, label = "clapClap"),
        still("wave", animated = true, label = "dancePls"), still("spin", animated = true, label = "spinHype"),
        still("rain", animated = true, zeroWidth = true, label = "rainOverlay"), still("sparkle", zeroWidth = true, label = "sparkleZW"),
    )

    /** The picker's lists in the demo: the same artwork under other names, so every tab, section and the locked list has something. */
    private val groups = listOf(
        EmoteGroup(EmoteSource.Twitch, EmoteSection.Channel, listOf(still("heart", label = "demoHeart"), still("clap", animated = true, label = "demoClap"), still("star", label = "demoStar"))),
        EmoteGroup(EmoteSource.Twitch, EmoteSection.Locked, listOf(still("spin", animated = true, label = "demoTierThree"), still("wave", animated = true, label = "demoTierTwo"))),
        EmoteGroup(EmoteSource.Twitch, EmoteSection.Global, listOf(still("smile", label = "Kappa"), still("star", label = "PogChamp"), still("heart", label = "<3"))),
        EmoteGroup(EmoteSource.Twitch, EmoteSection.OtherChannels, listOf(still("gg", label = "otherSubGG"), still("bounce", animated = true, label = "otherSubJam"))),
        EmoteGroup(EmoteSource.SevenTv, EmoteSection.Channel, thirdParty),
        EmoteGroup(EmoteSource.SevenTv, EmoteSection.Global, listOf(still("smile", label = "EZ"), still("gg", label = "Clap"), still("star", label = "Pog"), still("heart", label = "peepoLove"), still("bounce", animated = true, label = "pepeJam"))),
        EmoteGroup(EmoteSource.Bttv, EmoteSection.Global, listOf(still("smile", label = "OMEGALUL"), still("gg", label = "monkaS"), still("star", label = "Sadge"))),
        EmoteGroup(EmoteSource.Ffz, EmoteSection.Global, listOf(still("heart", label = "ZrehplaR"), still("smile", label = "LilZ"))),
    )

    private val assets = ChatAssets(
        badges = mapOf(
            "subscriber:1" to DemoEmoteArt.url("badge-sub", false),
            "moderator:1" to DemoEmoteArt.url("badge-mod", false),
            "vip:1" to DemoEmoteArt.url("badge-vip", false),
            "broadcaster:1" to DemoEmoteArt.url("badge-bc", false),
        ),
        emotes = thirdParty.associateBy { it.name },
        groups = groups,
        access = UserEmoteAccess.Known,
    )

    override fun assets(broadcasterId: String): Flow<ChatAssets> = flowOf(assets)
}
