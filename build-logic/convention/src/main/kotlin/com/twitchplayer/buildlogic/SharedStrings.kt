package com.twitchplayer.buildlogic

import java.io.File

/**
 * The copy rule behind the `twitchplayer.shared-strings` plugin, kept free of Gradle types so it is unit-tested directly.
 *
 * Takes the translated string files of an app's `res` directory (`values/strings.xml`, `values-fr/strings.xml`, ...) and
 * writes them under the same relative paths in [target]. Nothing else is taken: drawables, fonts, layouts, styles and
 * `resources.properties` stay with their app. The target is rebuilt from scratch so a deleted language disappears.
 */
internal object SharedStrings {
    /** `values`, `values-fr`, `values-b+fil`, `values-zh-rCN`: the qualifier is anything after the first dash. */
    private val valuesDirectory = Regex("""values(-[A-Za-z0-9+\-]+)?""")

    const val FILE_NAME = "strings.xml"

    /** Copies the `strings.xml` of every `values` directory of [source] into [target]; returns the relative paths written, sorted. */
    fun copy(source: File, target: File): List<String> {
        require(source.isDirectory) { "Shared strings source does not exist: $source" }
        target.deleteRecursively()
        target.mkdirs()
        val written = source.listFiles { file -> file.isDirectory && valuesDirectory.matches(file.name) }
            .orEmpty()
            .mapNotNull { dir -> File(dir, FILE_NAME).takeIf { it.isFile }?.let { dir.name to it } }
            .sortedBy { it.first }
        return written.map { (dirName, file) ->
            val out = File(File(target, dirName).also { it.mkdirs() }, FILE_NAME)
            file.copyTo(out, overwrite = true)
            "$dirName/$FILE_NAME"
        }
    }
}
