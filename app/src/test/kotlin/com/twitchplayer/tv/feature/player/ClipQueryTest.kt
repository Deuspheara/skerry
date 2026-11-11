package com.twitchplayer.tv.feature.player

import com.twitchplayer.tv.feature.player.data.stream.GqlClipResolver
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipQueryTest {
    /** Twitch's GraphQL types the clip slug as ID!; declaring String! makes it answer "used in position expecting type ID!" and no clip plays. */
    @Test fun slugVariableIsDeclaredAsId() {
        assertTrue(GqlClipResolver.QUERY.contains("\$slug: ID!"))
    }
}
