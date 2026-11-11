package com.twitchplayer.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SharedStringsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun res(vararg files: Pair<String, String>): File = tmp.newFolder("res").also { root ->
        files.forEach { (path, text) -> File(root, path).apply { parentFile.mkdirs() }.writeText(text) }
    }

    private fun strings(vararg pairs: Pair<String, String>) = "<resources>" + pairs.joinToString("") { (k, v) -> "<string name=\"$k\">$v</string>" } + "</resources>"

    @Test fun copiesEveryLanguageOfStringsXml() {
        val fr = strings("a" to "fr")
        val source = res(
            "values/strings.xml" to strings("a" to "en"),
            "values-fr/strings.xml" to fr,
            "values-b+fil/strings.xml" to strings("a" to "fil"),
            "values-zh-rCN/strings.xml" to strings("a" to "zh"),
        )
        val target = File(tmp.root, "out")

        val written = SharedStrings.copy(source, target)

        assertEquals(listOf("values/strings.xml", "values-b+fil/strings.xml", "values-fr/strings.xml", "values-zh-rCN/strings.xml"), written)
        assertEquals(fr, File(target, "values-fr/strings.xml").readText())
    }

    @Test fun copiesPluralsAndArraysFilesAndKeepsTranslatableFalse() {
        val plurals = "<resources><plurals name=\"n\"><item quantity=\"one\">%d new</item><item quantity=\"other\">%d new</item></plurals></resources>"
        val arrays = "<resources><string-array name=\"a\"><item>x</item></string-array></resources>"
        val strings = "<resources><string name=\"brand\" translatable=\"false\">Twitch</string><string name=\"b\">b</string></resources>"
        val source = res("values/strings.xml" to strings, "values/plurals.xml" to plurals, "values-fr/arrays.xml" to arrays)
        val target = File(tmp.root, "out")

        val written = SharedStrings.copy(source, target)

        assertEquals(listOf("values/plurals.xml", "values/strings.xml", "values-fr/arrays.xml"), written)
        assertEquals(plurals, File(target, "values/plurals.xml").readText())
        assertTrue(File(target, "values/strings.xml").readText().contains("translatable=\"false\""))
    }

    @Test fun leavesEverythingElseWithItsApp() {
        val source = res(
            "values/strings.xml" to strings("s" to "s"),
            "values/colors.xml" to "<resources><color name=\"c\">#fff</color></resources>",
            "values/themes.xml" to "<resources><style name=\"T\"><item name=\"x\">y</item></style></resources>",
            "values/dimens.xml" to "<resources><dimen name=\"d\">1dp</dimen></resources>",
            "values/empty.xml" to "<resources/>",
            "values-night/themes.xml" to "<resources><style name=\"T\"/></resources>",
            "drawable/icon.xml" to "<vector/>",
            "font/inter.ttf" to "f",
            "resources.properties" to "unqualifiedResLocale=en",
            "valuesextra/strings.xml" to strings("x" to "not a values qualifier"),
        )
        val target = File(tmp.root, "out")

        val written = SharedStrings.copy(source, target)

        assertEquals(listOf("values/strings.xml"), written)
        assertEquals(listOf("values"), target.listFiles()!!.map { it.name })
        assertEquals(listOf("strings.xml"), File(target, "values").list()!!.toList())
    }

    @Test fun aFileMixingTextAndOtherResourcesFailsLoudly() {
        val source = res("values/mixed.xml" to "<resources><string name=\"a\">a</string><color name=\"c\">#fff</color></resources>")

        val error = assertThrows(IllegalStateException::class.java) { SharedStrings.copy(source, File(tmp.root, "out")) }

        assertTrue(error.message!!, error.message!!.contains("values/mixed.xml") && error.message!!.contains("mixes"))
    }

    @Test fun invalidXmlAndForeignRootsFailLoudly() {
        val broken = res("values/strings.xml" to "<resources><string name=\"a\">x</resources>")
        assertTrue(assertThrows(IllegalStateException::class.java) { SharedStrings.copy(broken, File(tmp.root, "out1")) }.message!!.contains("not valid XML"))

        val foreign = tmp.newFolder("res2").also {
            File(it, "values").mkdirs()
            File(it, "values/strings.xml").writeText("<other/>")
        }
        assertTrue(assertThrows(IllegalStateException::class.java) { SharedStrings.copy(foreign, File(tmp.root, "out2")) }.message!!.contains("root element"))
    }

    @Test fun doctypesAreRejected() {
        val xxe = "<!DOCTYPE resources [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><resources><string name=\"a\">&e;</string></resources>"
        val source = res("values/strings.xml" to xxe)

        assertThrows(IllegalStateException::class.java) { SharedStrings.copy(source, File(tmp.root, "out")) }
    }

    @Test fun rebuildsTheTargetSoDeletedLanguagesDisappear() {
        val source = res("values/strings.xml" to strings("a" to "en"), "values-de/strings.xml" to strings("a" to "de"))
        val target = File(tmp.root, "out")
        SharedStrings.copy(source, target)
        File(source, "values-de").deleteRecursively()
        File(target, "stale.txt").writeText("left over")

        SharedStrings.copy(source, target)

        assertFalse(File(target, "values-de").exists())
        assertFalse(File(target, "stale.txt").exists())
        assertTrue(File(target, "values/strings.xml").isFile)
    }

    @Test fun anEmptyResDirectoryYieldsAnEmptyTarget() {
        val source = res()
        val target = File(tmp.root, "out")

        assertEquals(emptyList<String>(), SharedStrings.copy(source, target))
        assertTrue(target.isDirectory)
    }

    @Test fun aMissingSourceFailsLoudly() {
        val error = assertThrows(IllegalArgumentException::class.java) { SharedStrings.copy(File(tmp.root, "nope"), File(tmp.root, "out")) }
        assertTrue(error.message!!.contains("does not exist"))
    }
}
