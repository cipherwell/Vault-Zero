package com.example.core

import com.example.model.VaultItem
import com.example.model.VaultType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorHistoryTest {

    private fun credential(value: String, passphrase: Boolean = false) =
        GeneratedCredential(value, passphrase, 0L)

    @Test
    fun `newest entry is first`() {
        val history = GeneratorHistory(maxEntries = 5)
        history.add(credential("first"))
        history.add(credential("second"))
        assertEquals(listOf("second", "first"), history.snapshot().map { it.value })
    }

    @Test
    fun `history is capped`() {
        val history = GeneratorHistory(maxEntries = 3)
        listOf("a", "b", "c", "d", "e").forEach { history.add(credential(it)) }
        assertEquals(3, history.size)
        assertEquals(listOf("e", "d", "c"), history.snapshot().map { it.value })
    }

    @Test
    fun `consecutive duplicates are ignored but repeats later are kept`() {
        val history = GeneratorHistory(maxEntries = 5)
        history.add(credential("same"))
        history.add(credential("same"))
        assertEquals(1, history.size)
        history.add(credential("other"))
        history.add(credential("same"))
        assertEquals(3, history.size)
    }

    @Test
    fun `blank values are ignored and clear empties the history`() {
        val history = GeneratorHistory()
        history.add(credential(""))
        assertEquals(0, history.size)
        history.add(credential("value"))
        history.clear()
        assertEquals(0, history.size)
        assertTrue(history.snapshot().isEmpty())
    }
}

class VaultGroupingTest {

    private fun item(id: String, type: VaultType) = VaultItem(
        id = id, title = id, type = type,
        payload = when (type) {
            VaultType.NOTE -> com.example.model.NotePayload(content = "x")
            else -> com.example.model.LoginPayload()
        },
    )

    @Test
    fun `groups in the canonical type order and omits empty sections`() {
        val items = listOf(
            item("note", VaultType.NOTE),
            item("login", VaultType.LOGIN),
            item("card", VaultType.CARD),
            item("login2", VaultType.LOGIN),
        )
        val sections = VaultGrouping.group(items)
        assertEquals(listOf(VaultType.LOGIN, VaultType.CARD, VaultType.NOTE), sections.map { it.type })
        assertEquals(2, sections.first().count)
        assertEquals(listOf("login", "login2"), sections.first().items.map { it.id })
    }

    @Test
    fun `empty list produces no sections`() {
        assertTrue(VaultGrouping.group(emptyList()).isEmpty())
    }
}
