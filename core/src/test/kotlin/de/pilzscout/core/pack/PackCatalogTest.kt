package de.pilzscout.core.pack

import de.pilzscout.core.model.PackCatalog
import de.pilzscout.core.model.PackCatalogEntry
import de.pilzscout.core.model.PackManifest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PackCatalogTest {
    private fun entry(version: String, schema: Int = 4, format: Int = 2, minApp: Int = 1) =
        PackCatalogEntry(version, "DE", schema, format, minApp, "rev-$version", "$version/manifest.json", "sha")

    @Test
    fun picksNewestEntryTheAppCanRead() {
        val catalog = PackCatalog(
            packs = listOf(
                entry("2026.12.1", schema = 5),
                entry("2026.11.1", minApp = 7),
                entry("2026.10.2", format = 3),
                entry("2026.10.1"),
                entry("2026.09.10"),
            ),
        )
        assertEquals("2026.10.1", catalog.newestCompatible(schemaVersion = 4, appVersionCode = 3)?.packVersion)
        assertEquals("2026.11.1", catalog.newestCompatible(schemaVersion = 4, appVersionCode = 7)?.packVersion)
        assertEquals("2026.12.1", catalog.newestCompatible(schemaVersion = 5, appVersionCode = 1)?.packVersion)
        assertNull(catalog.newestCompatible(schemaVersion = 3, appVersionCode = 99))
    }

    @Test
    fun parsesManifestsWithAndWithoutArchives() {
        val json = Json { ignoreUnknownKeys = true }
        val v1 = """{"packVersion":"1","region":"DE","schemaVersion":4,"components":[
            {"id":"wiki","version":"1","bytes":10,"required":false,"files":[{"path":"wiki/a","sha256":"x","bytes":10}]}]}"""
        val v2 = """{"packVersion":"2","region":"DE","schemaVersion":4,"formatVersion":2,"build":{"toolsCommit":"abc"},
            "components":[{"id":"wiki","version":"2","bytes":10,"required":false,"license":"CC-BY-SA-4.0",
            "files":[{"path":"wiki/a","sha256":"x","bytes":10}],"archive":{"path":"wiki.tar","sha256":"y","bytes":2048}}]}"""
        val m1 = json.decodeFromString(PackManifest.serializer(), v1)
        val m2 = json.decodeFromString(PackManifest.serializer(), v2)
        assertEquals(1, m1.formatVersion)
        assertNull(m1.components[0].archive)
        assertEquals(10, m1.components[0].downloadBytes)
        assertEquals(2048, m2.components[0].downloadBytes)
        assertEquals("CC-BY-SA-4.0", m2.components[0].license)
    }
}
