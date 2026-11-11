package com.twitchplayer.tv.core.di

import com.twitchplayer.tv.core.platform.NumberFormatter
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Decimal text in the symbols of the current default locale (the in-app language picker sets it), as the VOD speed label always did. */
class AndroidNumberFormatter(private val locale: () -> Locale = Locale::getDefault) : NumberFormatter {
    override fun decimal(value: Float, maxFractionDigits: Int): String = DecimalFormat("0." + "#".repeat(maxFractionDigits), DecimalFormatSymbols(locale())).format(value)
}
