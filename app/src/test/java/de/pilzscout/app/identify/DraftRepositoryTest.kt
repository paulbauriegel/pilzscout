package de.pilzscout.app.identify

import de.pilzscout.core.model.ViewType
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DraftRepositoryTest {
    private val a = File("a.jpg")
    private val b = File("b.jpg")

    @Test
    fun `add returns the id and photos are untyped by default`() {
        val repo = DraftRepository()
        val id = repo.add(a)
        val photo = repo.draft.value.photos.single()
        assertEquals(id, photo.id)
        assertEquals(ViewType.OTHER, photo.viewType)
    }

    @Test
    fun `adding the same file twice keeps one photo and returns its id`() {
        val repo = DraftRepository()
        val first = repo.add(a)
        val second = repo.add(a)
        assertEquals(first, second)
        assertEquals(1, repo.draft.value.photos.size)
    }

    @Test
    fun `capturedAt is stamped by the first photo only`() {
        val repo = DraftRepository()
        repo.add(a)
        val stamped = repo.draft.value.capturedAt
        Thread.sleep(2)
        repo.add(b)
        assertEquals(stamped, repo.draft.value.capturedAt)
    }

    @Test
    fun `replace swaps the file but keeps id and order`() {
        val repo = DraftRepository()
        val first = repo.add(a)
        repo.add(b)
        val c = File("c.jpg")
        repo.replace(first, c)
        val photos = repo.draft.value.photos
        assertEquals(listOf(c, b), photos.map { it.file })
        assertEquals(first, photos[0].id)
    }

    @Test
    fun `remove and setViewType`() {
        val repo = DraftRepository()
        val first = repo.add(a)
        val second = repo.add(b)
        repo.setViewType(second, ViewType.UNDERSIDE)
        repo.remove(first)
        val photo = repo.draft.value.photos.single()
        assertEquals(second, photo.id)
        assertEquals(ViewType.UNDERSIDE, photo.viewType)
        assertTrue(ViewType.UNDERSIDE in repo.draft.value.capturedViews)
    }

    @Test
    fun `clear keeps the language`() {
        val repo = DraftRepository()
        repo.setLanguage("de")
        val id = repo.add(a)
        repo.clear()
        assertTrue(repo.draft.value.photos.isEmpty())
        assertEquals("de", repo.draft.value.language)
        assertNotEquals(id, repo.add(a))
    }
}
