package com.twitchplayer.phone.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The phone's own strings ship in English and French; every other language falls back to English (documented in docs/phone-log.md). */
class PhoneStringsTest {
    private val res = File("src/main/res")

    private fun strings(dir: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$dir/phone_strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) }
            .filter { it.attributes.getNamedItem("translatable")?.nodeValue != "false" }
            .associate { it.attributes.getNamedItem("name").nodeValue to it.textContent }
    }

    private fun placeholders(text: String) = Regex("""%\d\$[sd]""").findAll(text).map { it.value }.sorted().toList()

    @Test fun frenchTranslatesEveryEnglishKeyAndNothingElse() {
        val english = strings("values")
        val french = strings("values-fr")

        assertEquals(english.keys, french.keys)
    }

    @Test fun translationsKeepTheSamePlaceholders() {
        val english = strings("values")
        val french = strings("values-fr")

        english.forEach { (key, text) -> assertEquals("placeholders of $key", placeholders(text), placeholders(french.getValue(key))) }
    }

    @Test fun everyKeyIsPrefixedSoItCannotClashWithTheSharedTvStrings() {
        assertTrue(strings("values").keys.all { it.startsWith("phone_") })
    }

    @Test fun noTranslationIsBlank() {
        assertTrue(strings("values").values.all { it.isNotBlank() })
        assertTrue(strings("values-fr").values.all { it.isNotBlank() })
    }
}
