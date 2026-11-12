package com.twitchplayer.phone.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardSpecTest {
    @Test fun aCategoryNameAlwaysTakesTwoLinesSoCardsStayLevel() {
        assertEquals(2, CardSpec.CATEGORY_NAME_LINES)
    }

    @Test fun aCompactRowIsAsTallAsItsSixteenByNineThumbnail() {
        assertEquals(94.5f, CardSpec.compactRowHeight.value, 0.01f)
        assertTrue(CardSpec.compactRowHeight.value * 16f / 9f - CardSpec.COMPACT_THUMBNAIL_WIDTH.value < 0.01f)
    }
}
