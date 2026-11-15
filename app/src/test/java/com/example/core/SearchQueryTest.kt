package com.example.core

import com.example.model.LoginPayload
import com.example.model.NotePayload
import com.example.model.VaultItem
import com.example.model.VaultType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {

    private fun login(id: String, title: String, user: String = "", url: String = "", favorite: Boolean = false) =
        VaultItem(
            id = id, title = title, type = VaultType.LOGIN, isFavorite = favorite,
            payload = LoginPayload(username = user, password = "x", url = url),
        )

    private val github = login("github", "GitHub", "cipherwell@example.com", "https://github.com", favorite = true)
    private val netflix = login("netflix", "Netflix", "family@example.com", "https://netflix.com")
    private val note = VaultItem(
        id = "note", title = "Router", type = VaultType.NOTE,
        payload = NotePayload(content = "admin 192.168.1.1"),
    )
    private val items = listOf(github, netflix, note)

    @Test
    fun `plain words match title, username and url`() {
        assertEquals(listOf(github), items.filter { SearchQuery.matches(it, SearchQuery.parse("github")) })
        assertEquals(listOf(github), items.filter { SearchQuery.matches(it, SearchQuery.parse("cipherwell")) })
        assertEquals(listOf(netflix), items.filter { SearchQuery.matches(it, SearchQuery.parse("family")) })
        assertEquals(listOf(note), items.filter { SearchQuery.matches(it, SearchQuery.parse("192.168")) })
    }

    @Test
    fun `type operator narrows results with aliases`() {
        val spec = SearchQuery.parse("type:note")
        assertEquals(VaultType.NOTE, spec.type)
        assertEquals(listOf(note), items.filter { SearchQuery.matches(it, spec) })
        assertEquals(VaultType.LOGIN, SearchQuery.parse("type:logins").type)
        assertEquals(VaultType.API_SECRET, SearchQuery.parse("t:api").type)
    }

    @Test
    fun `favorite operator works and can be cleared`() {
        val favorites = SearchQuery.parse("is:fav")
        assertEquals(listOf(github), items.filter { SearchQuery.matches(it, favorites) })
        val all = SearchQuery.parse("is:all")
        assertEquals(items, items.filter { SearchQuery.matches(it, all) })
    }

    @Test
    fun `tag operator requires every tag`() {
        val tagged = note.copy(tags = listOf("home", "wifi"))
        assertTrue(SearchQuery.matches(tagged, SearchQuery.parse("tag:home")))
        assertFalse(SearchQuery.matches(tagged, SearchQuery.parse("tag:work")))
    }

    @Test
    fun `unknown prefixes are treated as plain text`() {
        val spec = SearchQuery.parse("https://github.com")
        assertTrue(spec.terms.isNotEmpty())
        assertEquals(1, items.count { SearchQuery.matches(it, spec) })
    }

    @Test
    fun `empty query matches everything and parses empty`() {
        assertTrue(SearchQuery.parse("").isEmpty)
        assertTrue(SearchQuery.parse("   ").isEmpty)
        assertEquals(3, items.count { SearchQuery.matches(it, SearchQuery.parse("")) })
    }

    @Test
    fun `combined operators and terms are ANDed`() {
        val spec = SearchQuery.parse("type:login netflix")
        assertEquals(listOf(netflix), items.filter { SearchQuery.matches(it, spec) })
        assertNull(SearchQuery.parse("type:unknown").type)
        assertTrue(SearchQuery.parse("type:unknown").terms.isNotEmpty())
    }
}
