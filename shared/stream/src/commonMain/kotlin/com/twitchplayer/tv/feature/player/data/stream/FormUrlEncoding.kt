package com.twitchplayer.tv.feature.player.data.stream

private const val HEX = "0123456789ABCDEF"
private const val BYTE_MASK = 0xFF
private const val NIBBLE_BITS = 4
private const val NIBBLE_MASK = 0xF

/**
 * `application/x-www-form-urlencoded` encoding of one value in UTF-8, byte for byte what the JDK's `URLEncoder.encode(value, "UTF-8")`
 * produced before this code moved to common: letters, digits and `. - * _` stay, a space becomes `+`, every other byte
 * becomes `%XX` in upper case. Ktor's `encodeURLParameter` differs (it keeps `~` and encodes `*`), which would change
 * the usher URLs, so it is not used. Strings with unpaired surrogates are not supported (Twitch tokens are ASCII).
 */
internal fun formUrlEncode(value: String): String {
    val bytes = value.encodeToByteArray()
    val out = StringBuilder(bytes.size + bytes.size / 2)
    for (byte in bytes) {
        val c = byte.toInt() and BYTE_MASK
        val plain = c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code ||
            c == '.'.code || c == '-'.code || c == '*'.code || c == '_'.code
        when {
            plain -> out.append(c.toChar())
            c == ' '.code -> out.append('+')
            else -> out.append('%').append(HEX[c shr NIBBLE_BITS]).append(HEX[c and NIBBLE_MASK])
        }
    }
    return out.toString()
}
