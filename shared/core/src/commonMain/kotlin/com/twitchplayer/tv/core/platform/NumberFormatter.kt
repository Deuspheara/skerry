package com.twitchplayer.tv.core.platform

/**
 * Locale-aware number text, provided by the platform (Android: `DecimalFormat` with the default locale's symbols), read at
 * each call so a language switch applies at once.
 */
fun interface NumberFormatter {
    /** [value] with at most [maxFractionDigits] decimals, no trailing zeros, no grouping, the locale's decimal separator: 1.0 -> "1", 1.25 -> "1.25" ("1,25" in French). */
    fun decimal(value: Float, maxFractionDigits: Int): String
}
