package com.twitchplayer.phone.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The phone's own strings ship in English, French and Arabic; every other language falls back to English (documented in
 * docs/LOCALIZATION_PLAN.md). English is the source: French and Arabic must have exactly its keys and placeholders.
 */
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

    private val translations = listOf("values-fr", "values-ar")

    @Test fun everyTranslationHasExactlyTheEnglishKeys() {
        val english = strings("values")
        translations.forEach { dir -> assertEquals("keys of $dir", english.keys, strings(dir).keys) }
    }

    @Test fun translationsKeepTheSamePlaceholders() {
        val english = strings("values")
        translations.forEach { dir ->
            val translated = strings(dir)
            english.forEach { (key, text) -> assertEquals("placeholders of $key in $dir", placeholders(text), placeholders(translated.getValue(key))) }
        }
    }

    @Test fun everyPhoneStringTheCodeUsesExistsInEnglish() {
        val english = strings("values").keys
        val used = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }
            .flatMap { Regex("""R\.string\.(phone_\w+)""").findAll(it.readText()).map { m -> m.groupValues[1] } }.toSet()
        val untranslatable = File(res, "values/phone_strings.xml").readText().let { text -> Regex("""name="(phone_\w+)" translatable="false"""").findAll(text).map { it.groupValues[1] }.toSet() }
        assertEquals("strings used in code but missing from values/phone_strings.xml", emptySet<String>(), used - english - untranslatable)
    }

    @Test fun everyKeyIsPrefixedSoItCannotClashWithTheSharedTvStrings() {
        assertTrue(strings("values").keys.all { it.startsWith("phone_") })
    }

    @Test fun noTranslationIsBlank() {
        (listOf("values") + translations).forEach { dir -> assertTrue("blank string in $dir", strings(dir).values.all { it.isNotBlank() }) }
    }
}
