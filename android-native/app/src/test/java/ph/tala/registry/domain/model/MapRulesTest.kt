package ph.tala.registry.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM rules for the D16 household map: extent, projection, provenance and URLs. */
class MapRulesTest {
    private val inside = Coordinate(11.0176, 124.6031, accuracyMetres = 12.5, capturedAt = 1_000L, source = CoordinateSource.Gps)
    private val outside = Coordinate(14.5994, 120.9842, accuracyMetres = null, capturedAt = 2_000L, source = CoordinateSource.Manual)

    @Test fun extentCornersAreInclusive() {
        assertTrue(MapRules.contains(MapRules.SOUTH_WEST_LATITUDE, MapRules.SOUTH_WEST_LONGITUDE))
        assertTrue(MapRules.contains(MapRules.NORTH_EAST_LATITUDE, MapRules.NORTH_EAST_LONGITUDE))
        assertTrue(MapRules.contains(MapRules.CENTER_LATITUDE, MapRules.CENTER_LONGITUDE))
    }

    @Test fun justOutsideTheExtentIsRejected() {
        assertFalse(MapRules.contains(MapRules.SOUTH_WEST_LATITUDE - 0.0001, MapRules.CENTER_LONGITUDE))
        assertFalse(MapRules.contains(MapRules.CENTER_LATITUDE, MapRules.NORTH_EAST_LONGITUDE + 0.0001))
        assertEquals(MapRules.OUTSIDE_MESSAGE, MapRules.extentProblem(11.0100, 124.6031))
        assertNull(MapRules.extentProblem(11.0176, 124.6031))
    }

    @Test fun savedCoordinatesOutsideTheExtentAreFlaggedNotRejected() {
        assertFalse(MapRules.outOfArea(inside))
        assertTrue(MapRules.outOfArea(outside))
        // Flagging never mutates or rejects: the record stays addressable for correction.
        assertEquals(14.5994, outside.latitude, 0.0)
    }

    @Test fun mercatorRoundTripPreservesCoordinates() {
        val x = MapRules.worldX(124.6031)
        val y = MapRules.worldY(11.0176)
        assertEquals(124.6031, MapRules.longitudeAt(x), 1e-9)
        assertEquals(11.0176, MapRules.latitudeAt(y), 1e-9)
        // Cogon sits just east of the 120–126° band's midpoint and just north of the equator.
        assertTrue(x in 0.5..1.0)
        assertTrue(y in 0.4..0.5)
    }

    @Test fun latitudeClampsPoleValuesForThePlot() {
        assertEquals(85.05112878, MapRules.latitudeAt(0.0), 1e-6)
        assertEquals(-85.05112878, MapRules.latitudeAt(1.0), 1e-6)
    }

    @Test fun externalUrlsCarryCoordinatesOnly() {
        val search = MapRules.externalMapUrl(11.0176, 124.6031, directions = false)
        val directions = MapRules.externalMapUrl(11.0176, 124.6031, directions = true)
        // Same contract as the browser prototype: google.com/maps/{search,dir}/?api=1&...
        assertEquals("https://www.google.com/maps/search/?api=1&query=11.017600,124.603100", search)
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=11.017600,124.603100", directions)
        // Privacy: no household name, address or answer text can enter the URL.
        for (url in listOf(search, directions)) {
            assertTrue(!url.contains(" ") && !url.contains("Sample"))
            assertTrue(url.contains("11.017600") && url.contains("124.603100"))
        }
    }

    @Test fun zoomStaysInsideTheTileWindow() {
        assertEquals(MapRules.MIN_ZOOM, MapRules.zoom(2.0), 0.0)
        assertEquals(MapRules.MAX_ZOOM, MapRules.zoom(21.0), 0.0)
        assertEquals(17.5, MapRules.zoom(17.5), 0.0)
    }

    @Test fun coordinateTextUsesSixDecimals() {
        assertEquals("11.017600", MapRules.formatCoordinate(11.0176))
        assertEquals("124.603100", MapRules.formatCoordinate(124.6031))
    }
}
