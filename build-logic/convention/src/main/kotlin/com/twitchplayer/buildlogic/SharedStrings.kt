package com.twitchplayer.buildlogic

import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The copy rule behind the `twitchplayer.shared-strings` plugin, kept free of Gradle types so it is unit-tested directly.
 *
 * Takes the TEXT resources of an app's `res` directory and writes them under the same relative paths in [target]: every
 * `*.xml` file of a `values*` directory whose top-level resources are all `<string>`, `<plurals>` or `<string-array>`
 * (so `strings.xml`, `plurals.xml`, `arrays.xml`, per language). Files of any other kind (`colors.xml`, `themes.xml`,
 * `dimens.xml`...), drawables, fonts, layouts and `resources.properties` stay with their app. A file that MIXES text and
 * other resources is an error, not a silent drop: split it. The target is rebuilt from scratch so a deleted language
 * disappears.
 */
internal object SharedStrings {
    /** `values`, `values-fr`, `values-b+fil`, `values-zh-rCN`: the qualifier is anything after the first dash. */
    private val valuesDirectory = Regex("""values(-[A-Za-z0-9+\-]+)?""")

    private val textResources = setOf("string", "plurals", "string-array")

    private enum class Kind { Text, Other, Mixed }

    /** Copies the text resource files of every `values` directory of [source] into [target]; returns the relative paths written, sorted. */
    fun copy(source: File, target: File): List<String> {
        require(source.isDirectory) { "Shared strings source does not exist: $source" }
        target.deleteRecursively()
        target.mkdirs()
        val written = mutableListOf<String>()
        val dirs = source.listFiles { file -> file.isDirectory && valuesDirectory.matches(file.name) }.orEmpty().sortedBy { it.name }
        for (dir in dirs) {
            val files = dir.listFiles { file -> file.isFile && file.extension == "xml" }.orEmpty().sortedBy { it.name }
            for (file in files) {
                when (kindOf(file)) {
                    Kind.Text -> {
                        val out = File(File(target, dir.name).also { it.mkdirs() }, file.name)
                        file.copyTo(out, overwrite = true)
                        written += "${dir.name}/${file.name}"
                    }

                    Kind.Other -> Unit

                    Kind.Mixed -> error(
                        "${dir.name}/${file.name} mixes text resources (string, plurals, string-array) with other resources: " +
                            "split it so the shared-strings plugin can take the text part.",
                    )
                }
            }
        }
        return written
    }

    private fun kindOf(file: File): Kind {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val root = try {
            factory.newDocumentBuilder().parse(file).documentElement
        } catch (e: org.xml.sax.SAXException) {
            throw IllegalStateException("${file.parentFile.name}/${file.name} is not valid XML: ${e.message}", e)
        }
        check(root.tagName == "resources") { "${file.parentFile.name}/${file.name}: the root element must be <resources>" }
        val names = (0 until root.childNodes.length).map { root.childNodes.item(it) }.filter { it.nodeType == Node.ELEMENT_NODE }.map { it.nodeName }
        return when {
            names.isEmpty() -> Kind.Other
            names.all { it in textResources } -> Kind.Text
            names.any { it in textResources } -> Kind.Mixed
            else -> Kind.Other
        }
    }
}
