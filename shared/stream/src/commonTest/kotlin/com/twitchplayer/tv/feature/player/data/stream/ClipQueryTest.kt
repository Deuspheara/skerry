package com.twitchplayer.tv.feature.player.data.stream

import com.twitchplayer.tv.feature.player.data.stream.GqlClipResolver
import kotlin.test.Test
import kotlin.test.assertTrue

class ClipQueryTest {
    /** Twitch's GraphQL types the clip slug as ID!; declaring String! makes it answer "used in position expecting type ID!" and no clip plays. */
    @Test fun slugVariableIsDeclaredAsId() {
        assertTrue(GqlClipResolver.QUERY.contains("\$slug: ID!"))
    }
}
