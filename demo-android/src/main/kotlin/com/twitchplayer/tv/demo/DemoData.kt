package com.twitchplayer.tv.demo

import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.data.model.CategoryGroup
import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.data.model.LiveStream

/** Fictional data mirroring the validated prototype (no real channels, no brand art). */
internal object DemoData {
    private fun art(seed: Int, style: Int) = "demoart://x/$seed/$style"

    private class Cat(val id: String, val name: String, val group: CategoryGroup, val viewers: Int, val seed: Int, val style: Int)

    private val cats = listOf(
        Cat("valmoor", "Valmoor", CategoryGroup.Games, 42300, 11, 2), Cat("racers", "Pixel Racers", CategoryGroup.Games, 31800, 12, 3),
        Cat("kestrel", "Kestrel Ops", CategoryGroup.Games, 28400, 13, 0), Cat("discussion", "Discussion", CategoryGroup.Irl, 18500, 14, 1),
        Cat("speedrun", "Speedruns", CategoryGroup.Games, 12500, 15, 2), Cat("musique", "Musique & DJ", CategoryGroup.Music, 9800, 16, 3),
        Cat("arcana", "Arcana Deck", CategoryGroup.Games, 9100, 17, 0), Cat("simulation", "Bâtisseurs", CategoryGroup.Games, 6200, 18, 1),
        Cat("cuisine", "Cuisine", CategoryGroup.Irl, 4900, 19, 2), Cat("echecs", "Échecs", CategoryGroup.Games, 9050, 20, 0),
        Cat("art", "Art", CategoryGroup.Creative, 4100, 21, 3), Cat("sport", "Sport", CategoryGroup.Irl, 3400, 22, 1),
        Cat("concerts", "Concerts", CategoryGroup.Music, 3300, 23, 2), Cat("voyages", "Voyages", CategoryGroup.Irl, 2600, 24, 3),
        Cat("peinture", "Peinture", CategoryGroup.Creative, 2200, 25, 0), Cat("artisanat", "Artisanat", CategoryGroup.Creative, 1500, 26, 1),
    )

    val categories: List<Category> = cats.sortedByDescending { it.viewers }.map { Category(it.id, it.name, art(it.seed, it.style), it.group) }

    private class Chan(val id: String, val name: String, val title: String, val cat: String, val viewers: Int, val live: Boolean, val seed: Int, val style: Int)

    private val chans = listOf(
        Chan("lunaria", "Lunaria", "Run nocturne : boss final sans dégâts", "valmoor", 14820, true, 101, 2),
        Chan("maitrekoi", "MaîtreKoi", "Ouverture sicilienne, on analyse vos parties", "echecs", 6340, true, 102, 0),
        Chan("ines", "Cuisine d'Inès", "Gratin dauphinois, la version de ma grand-mère", "cuisine", 3185, true, 103, 1),
        Chan("oriel", "DJ Oriel", "Set deep house, vinyles uniquement", "musique", 5470, true, 104, 3),
        Chan("kestrelfr", "Kestrel FR", "Ranked Diamant : on pousse vers Élite", "kestrel", 22910, true, 105, 0),
        Chan("manon", "Pixel Manon", "Peinture numérique : portrait au pinceau doux", "art", 1820, true, 106, 3),
        Chan("zero", "Vitesse Zéro", "Any% en moins de 40 minutes ?", "speedrun", 9760, true, 107, 2),
        Chan("theo", "Arcana Théo", "Draft de la saison : deck Tempête", "arcana", 4210, true, 108, 1),
        Chan("tournesol", "Tournesol", "Discussion : vos questions du jour", "discussion", 7880, true, 109, 1),
        Chan("nova", "Garage Nova", "Course d'endurance 6 h, relais en direct", "racers", 11340, true, 110, 3),
        Chan("sacha", "Atelier Sacha", "Je construis une ville dense sans embouteillage", "simulation", 2650, true, 111, 0),
        Chan("brume", "Brume", "Soirée coop tranquille, venez avec un café", "valmoor", 3920, true, 112, 2),
        Chan("marlou", "Marlou", "Tournoi entre amis, finale ce soir", "racers", 5120, true, 113, 1),
        Chan("ondine", "Ondine", "Blitz 3+2 contre les abonnés", "echecs", 2710, true, 114, 3),
        Chan("hibou", "Hibou du Soir", "", "discussion", 0, false, 115, 0),
        Chan("clem", "Clémentine", "", "peinture", 0, false, 116, 2),
        Chan("fanfan", "Fanfan OST", "", "musique", 0, false, 117, 1),
    )
    private val followedIds = setOf("lunaria", "maitrekoi", "ines", "oriel", "theo", "manon", "zero", "nova", "hibou", "clem", "fanfan")

    private fun catName(id: String) = cats.first { it.id == id }.name

    private fun Chan.stream() = LiveStream(id, id, name, title, cat, catName(cat), viewers, art(seed + 500, style), null)
    private fun Chan.ref() = ChannelRef(id, id, name, null, live, viewers)

    val liveStreams: List<LiveStream> = chans.filter { it.live }.map { it.stream() }
    val followedLive: List<LiveStream> = chans.filter { it.live && it.id in followedIds }.map { it.stream() }
    val recommended: List<LiveStream> = liveStreams.filter { s -> s.channelId !in followedIds }.sortedByDescending { it.viewers }
    val followedOffline: List<ChannelRef> = chans.filter { !it.live && it.id in followedIds }.mapIndexed { index, channel ->
        // Last seen live a day or more ago, a different time each: the offline rows show their "last live" line.
        channel.ref().copy(lastSeenLiveMs = System.currentTimeMillis() - (index + 1) * LAST_LIVE_STEP_MS)
    }
    val channels: List<ChannelRef> = chans.map { it.ref() }
    fun channelSeed(id: String): Pair<Int, Int> = chans.first { it.id == id }.let { it.seed to it.style }
    fun isFollowed(id: String) = id in followedIds
    fun stream(login: String): LiveStream? = liveStreams.firstOrNull { it.login == login }
    fun artFor(seed: Int, style: Int) = art(seed, style)
}

private const val LAST_LIVE_STEP_MS = 27L * 3_600_000L
