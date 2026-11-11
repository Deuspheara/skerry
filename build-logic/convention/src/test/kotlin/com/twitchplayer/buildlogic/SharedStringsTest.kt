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

    @Test fun copiesEveryLanguageOfStringsXml() {
        val source = res(
            "values/strings.xml" to "<resources>en</resources>",
            "values-fr/strings.xml" to "<resources>fr</resources>",
            "values-b+fil/strings.xml" to "<resources>fil</resources>",
            "values-zh-rCN/strings.xml" to "<resources>zh</resources>",
        )
        val target = File(tmp.root, "out")

        val written = SharedStrings.copy(source, target)

        assertEquals(listOf("values/strings.xml", "values-b+fil/strings.xml", "values-fr/strings.xml", "values-zh-rCN/strings.xml"), written)
        assertEquals("<resources>fr</resources>", File(target, "values-fr/strings.xml").readText())
        assertEquals("<resources>fil</resources>", File(target, "values-b+fil/strings.xml").readText())
    }

    @Test fun leavesEverythingElseWithItsApp() {
        val source = res(
            "values/strings.xml" to "s",
            "values/colors.xml" to "c",
            "values/styles.xml" to "st",
            "values-night/themes.xml" to "t",
            "drawable/icon.xml" to "d",
            "font/inter.ttf" to "f",
            "resources.properties" to "unqualifiedResLocale=en",
            "valuesextra/strings.xml" to "not a values qualifier",
        )
        val target = File(tmp.root, "out")

        val written = SharedStrings.copy(source, target)

        assertEquals(listOf("values/strings.xml"), written)
        assertEquals(listOf("values"), target.listFiles()!!.map { it.name })
        assertEquals(listOf("strings.xml"), File(target, "values").list()!!.toList())
    }

    @Test fun rebuildsTheTargetSoDeletedLanguagesDisappear() {
        val source = res("values/strings.xml" to "en", "values-de/strings.xml" to "de")
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
