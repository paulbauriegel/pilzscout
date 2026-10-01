package de.pilzscout.app.identify

import de.pilzscout.app.location.GeoPoint
import de.pilzscout.core.model.ViewType
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
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

    @Test
    fun `an imported photo supplies date and position, and the device fix does not replace them`() {
        val repo = DraftRepository()
        repo.add(a)
        val exif = PhotoMetadata(location = GeoPoint(48.0, 7.85), takenAt = 1_700_000_000_000)
        repo.adoptPhotoMetadata(exif, "Freiburg", useDate = true)
        val d = repo.draft.value
        assertEquals(1_700_000_000_000, d.capturedAt)
        assertEquals(GeoPoint(48.0, 7.85), d.location)
        assertEquals("Freiburg", d.placeName)
        assertTrue(d.locationRequested)

        repo.setLocation(GeoPoint(52.5, 13.4), "Berlin")
        assertEquals(GeoPoint(48.0, 7.85), repo.draft.value.location)
        assertEquals("Freiburg", repo.draft.value.placeName)
    }

    @Test
    fun `a later photo neither changes the date nor an existing position`() {
        val repo = DraftRepository()
        repo.add(a)
        repo.setLocation(GeoPoint(52.5, 13.4), "Berlin")
        val stamped = repo.draft.value.capturedAt
        repo.add(b)
        repo.adoptPhotoMetadata(PhotoMetadata(GeoPoint(48.0, 7.85), 1_700_000_000_000), "Freiburg", useDate = false)
        assertEquals(stamped, repo.draft.value.capturedAt)
        assertEquals(GeoPoint(52.5, 13.4), repo.draft.value.location)
    }

    @Test
    fun `a photo without tags leaves the draft untouched`() {
        val repo = DraftRepository()
        repo.add(a)
        val before = repo.draft.value
        repo.adoptPhotoMetadata(PhotoMetadata(), null, useDate = true)
        assertEquals(before.capturedAt, repo.draft.value.capturedAt)
        assertNull(repo.draft.value.location)
        assertFalse(repo.draft.value.locationRequested)
    }
}
