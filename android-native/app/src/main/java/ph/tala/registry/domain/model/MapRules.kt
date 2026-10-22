package ph.tala.registry.domain.model

/**
 * Android-free rules for the D16 household map: the approximate Cogon working extent,
 * Web Mercator projection for the offline coordinate plot, external map URLs and the
 * manual-pin semantics the map shares with the household location card. No Android or
 * Compose types appear here so every rule stays testable on the JVM.
 */
object MapRules {
    /**
     * Approximate working extent, NOT an official barangay boundary polygon. Values
     * mirror the browser prototype's Cogon reference (southwest/northeast corners and
     * the default center), and the UI must keep labeling the extent as approximate.
     */
    const val SOUTH_WEST_LATITUDE = 11.0116
    const val SOUTH_WEST_LONGITUDE = 124.5971
    const val NORTH_EAST_LATITUDE = 11.0236
    const val NORTH_EAST_LONGITUDE = 124.6091
    const val CENTER_LATITUDE = 11.0176
    const val CENTER_LONGITUDE = 124.6031

    /** Tile-zoom window: OpenStreetMap Mapnik serves up to 19 and the prototype uses 16–19. */
    const val MIN_ZOOM = 16.0
    const val MAX_ZOOM = 19.0
    const val DEFAULT_ZOOM = 17.0

    const val EXTENT_NOTE = "Approximate Cogon working area — not an official barangay boundary."
    const val OUTSIDE_MESSAGE = "Household locations must be within the Cogon working area."
    const val OUT_OF_AREA_FLAG = "Saved pin is outside the Cogon working area. Please correct it."

    /** True when the point sits inside the approximate working extent (inclusive edges). */
    fun contains(latitude: Double, longitude: Double): Boolean =
        latitude >= SOUTH_WEST_LATITUDE && latitude <= NORTH_EAST_LATITUDE &&
            longitude >= SOUTH_WEST_LONGITUDE && longitude <= NORTH_EAST_LONGITUDE

    /**
     * New pins placed on the map must land inside the working extent; existing saved
     * coordinates from before this rule (or captured outside it) are retained and
     * flagged for correction instead of being rejected or re-centering the map.
     */
    fun extentProblem(latitude: Double, longitude: Double): String? =
        if (contains(latitude, longitude)) null else OUTSIDE_MESSAGE

    /** Existing stored coordinates are flagged, never dropped, when outside the extent. */
    fun outOfArea(coordinate: Coordinate): Boolean = !contains(coordinate.latitude, coordinate.longitude)

    /**
     * Web Mercator world coordinates in [0, 1]; the offline plot and the tile-based map
     * agree on this projection, so a pin sits on the same spot with either basemap.
     */
    fun worldX(longitude: Double): Double = (longitude + 180.0) / 360.0

    fun worldY(latitude: Double): Double {
        val clamped = latitude.coerceIn(-85.05112878, 85.05112878)
        val radians = Math.toRadians(clamped)
        return 0.5 - Math.log(Math.tan(Math.PI / 4 + radians / 2)) / (2 * Math.PI)
    }

    fun latitudeAt(worldY: Double): Double {
        val n = Math.PI * (1 - 2 * worldY.coerceIn(0.0, 1.0))
        return Math.toDegrees(Math.atan(Math.sinh(n)))
    }

    fun longitudeAt(worldX: Double): Double = worldX.coerceIn(0.0, 1.0) * 360.0 - 180.0

    /**
     * External hand-off URL. Only the saved coordinates are embedded — never the
     * household name, address or any survey answer (TR-17). Decimal points are
     * locale-stable so a comma-locale device cannot produce an unparseable URL.
     */
    fun externalMapUrl(latitude: Double, longitude: Double, directions: Boolean): String {
        val coordinate = String.format(java.util.Locale.US, "%.6f,%.6f", latitude, longitude)
        return "https://www.google.com/maps/" + (if (directions) "dir/" else "search/") +
            "?api=1&" + (if (directions) "destination" else "query") + "=" + coordinate
    }

    /** Clamps a requested tile zoom into the supported window. */
    fun zoom(requested: Double): Double = requested.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /** Six-decimal text used by pin drafts and the location card alike; dot decimals only. */
    fun formatCoordinate(value: Double): String = String.format(java.util.Locale.US, "%.6f", value)
}
