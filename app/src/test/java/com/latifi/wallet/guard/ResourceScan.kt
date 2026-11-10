package com.latifi.wallet.guard

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Shared reader for the copy guards. Unit tests run with the app module as the working
 * directory, so the resource folders and the source root are plain relative paths.
 */
object ResourceScan {
    val DEFAULT_VALUES = File("src/main/res/values")
    val SPANISH_VALUES = File("src/main/res/values-b+es+419")
    val SOURCE_ROOT = File("src/main/java")

    data class StringEntry(val value: String, val translatable: Boolean)

    data class Resources(
        val strings: Map<String, StringEntry>,
        /** Plural name to quantity to text. */
        val plurals: Map<String, Map<String, String>>,
    ) {
        /** Every user-visible text with the resource name it belongs to, plurals included. */
        fun allTexts(): List<Pair<String, String>> =
            strings.map { it.key to it.value.value } +
                plurals.flatMap { (name, quantities) -> quantities.map { "$name[${it.key}]" to it.value } }

        fun allNames(): Set<String> = strings.keys + plurals.keys
    }

    fun load(valuesDir: File): Resources {
        require(valuesDir.isDirectory) { "resource folder not found: ${valuesDir.absolutePath}" }
        val strings = linkedMapOf<String, StringEntry>()
        val plurals = linkedMapOf<String, Map<String, String>>()
        valuesDir.listFiles { f -> f.isFile && f.name.startsWith("strings") && f.extension == "xml" }
            .orEmpty()
            .sortedBy { it.name }
            .forEach { parse(it, strings, plurals) }
        return Resources(strings, plurals)
    }

    /** Parses one strings file. Exposed so a guard self-check can feed it a tiny fixture. */
    fun parse(
        file: File,
        strings: MutableMap<String, StringEntry> = linkedMapOf(),
        plurals: MutableMap<String, Map<String, String>> = linkedMapOf(),
    ): Resources {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val root = factory.newDocumentBuilder().parse(file).documentElement
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val element = node as Element
            val name = element.getAttribute("name")
            when (element.tagName) {
                "string" -> strings[name] = StringEntry(
                    value = normalize(element.textContent),
                    translatable = element.getAttribute("translatable") != "false",
                )
                "plurals" -> {
                    val items = linkedMapOf<String, String>()
                    val itemNodes = element.getElementsByTagName("item")
                    for (j in 0 until itemNodes.length) {
                        val item = itemNodes.item(j) as Element
                        items[item.getAttribute("quantity")] = normalize(item.textContent)
                    }
                    plurals[name] = items
                }
            }
        }
        return Resources(strings, plurals)
    }

    /** Drops Android escapes (a backslash before a character) and surrounding quotes. */
    fun normalize(raw: String): String =
        raw.trim().removeSurrounding("\"").replace(Regex("""\\(.)"""), "$1").trim()

    fun kotlinFiles(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.toList()

    /** Positional or plain format specifiers of a value, with the literal `%%` removed. */
    fun placeholders(value: String): Set<String> =
        PLACEHOLDER.findAll(value.replace("%%", "")).map { it.value }.toSet()

    private val PLACEHOLDER = Regex("""%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]""")
}
