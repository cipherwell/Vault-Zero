package com.example.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the bilingual catalog without needing an emulator:
 *  - every English string has a Spanish (US) translation and there are no orphan keys,
 *  - no key is defined twice across the per-screen XML files,
 *  - format placeholders match between languages (a translation may never drop or add %1$s),
 *  - no translation is empty.
 *
 * Runs against the module's `src/main/res` tree; Gradle's test working directory is the module.
 */
class LocalizationCatalogTest {

    private val resDir: File = sequenceOf(
        File("src/main/res"),
        File("app/src/main/res"),
    ).firstOrNull { it.isDirectory }
        ?: error("Cannot locate src/main/res from ${File(".").absolutePath}")

    private data class Entry(val key: String, val value: String, val file: String)

    private fun readCatalog(dirName: String): List<Entry> {
        val dir = File(resDir, dirName)
        assertTrue("missing resource directory $dir", dir.isDirectory)
        val entries = ArrayList<Entry>()
        dir.listFiles { f -> f.isFile && f.name.endsWith(".xml") }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
                val nodes = doc.getElementsByTagName("string")
                for (i in 0 until nodes.length) {
                    val element = nodes.item(i) as Element
                    entries.add(
                        Entry(
                            key = element.getAttribute("name"),
                            value = element.textContent,
                            file = file.name,
                        )
                    )
                }
            }
        return entries
    }

    private val base by lazy { readCatalog("values") }
    private val spanish by lazy { readCatalog("values-es-rUS") }

    private val placeholderRegex = Regex("""%(?:\d+\$)?[a-zA-Z]""")

    @Test
    fun `no string key is defined twice across the catalog files`() {
        val duplicates = base.groupBy { it.key }.filterValues { it.size > 1 }
        assertTrue(
            "duplicate English keys: " + duplicates.entries.joinToString {
                "${it.key} in ${it.value.map { e -> e.file }}"
            },
            duplicates.isEmpty(),
        )
        val spanishDuplicates = spanish.groupBy { it.key }.filterValues { it.size > 1 }
        assertTrue(
            "duplicate Spanish keys: " + spanishDuplicates.entries.joinToString { it.key },
            spanishDuplicates.isEmpty(),
        )
    }

    @Test
    fun `every English string has a Spanish US translation and vice versa`() {
        val baseKeys = base.map { it.key }.toSet()
        val spanishKeys = spanish.map { it.key }.toSet()

        val missingSpanish = (baseKeys - spanishKeys).sorted()
        val orphanSpanish = (spanishKeys - baseKeys).sorted()

        assertTrue("missing Spanish translations: $missingSpanish", missingSpanish.isEmpty())
        assertTrue("Spanish keys with no English source: $orphanSpanish", orphanSpanish.isEmpty())
    }

    @Test
    fun `translations are not empty and keep the same format placeholders`() {
        val spanishByKey = spanish.associateBy { it.key }
        val problems = ArrayList<String>()

        for (entry in base) {
            val translated = spanishByKey[entry.key] ?: continue
            if (translated.value.isBlank()) {
                problems.add("${entry.key} is empty in Spanish (${translated.file})")
                continue
            }
            val basePlaceholders = placeholderRegex.findAll(entry.value).map { it.value }.sorted().toList()
            val spanishPlaceholders = placeholderRegex.findAll(translated.value).map { it.value }.sorted().toList()
            if (basePlaceholders != spanishPlaceholders) {
                problems.add(
                    "${entry.key}: placeholders differ EN=$basePlaceholders ES=$spanishPlaceholders",
                )
            }
        }
        assertEquals("translation problems:\n" + problems.joinToString("\n"), 0, problems.size)
    }

    @Test
    fun `the Spanish catalog actually translates the app, not just mirrors English`() {
        val spanishByKey = spanish.associateBy { it.key }
        val comparable = base.filter { it.key !in IDENTICAL_BY_DESIGN }
        val changed = comparable.count { entry ->
            val translated = spanishByKey[entry.key]?.value ?: return@count false
            translated != entry.value
        }
        val ratio = changed.toDouble() / comparable.size
        assertTrue(
            "only ${(ratio * 100).toInt()}% of strings are translated; expected most of them",
            ratio > 0.90,
        )
    }

    private companion object {
        /** Brand and language names legitimately identical in both catalogs. */
        val IDENTICAL_BY_DESIGN = setOf(
            "app_name",
            "language_english_us",
            "language_spanish_us",
        )
    }
}
