package ph.tala.registry.domain.model

/** Where a stored household coordinate came from. */
enum class CoordinateSource {
    /** A fresh device fix: [Coordinate.accuracyMetres] is the measured accuracy of that fix. */
    Gps,

    /**
     * Typed by the enumerator: [Coordinate.accuracyMetres] is always null because no
     * measurement describes a hand-entered point, and [Coordinate.capturedAt] is the
     * moment the corrected value was entered, not any earlier GPS fix time. Provenance
     * is therefore recoverable without a schema field: a stored coordinate with null
     * accuracy is manual, one with accuracy is a device fix.
     */
    Manual,
}

/** One household coordinate plus its capture metadata. Pure value; no Android types. */
data class Coordinate(
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Double?,
    val capturedAt: Long,
    val source: CoordinateSource,
)

/**
 * Android-free rules for capturing and correcting household coordinates. The location
 * provider, permission dialogs and Room all live outside this file, so the validity,
 * staleness and provenance semantics stay testable on the JVM.
 */
object LocationRules {
    /** A fix attempt stops after this long; the UI says so instead of waiting forever. */
    const val ACQUISITION_TIMEOUT_MS = 20_000L

    /** Returns the human-readable problem with a candidate coordinate, or null when valid. */
    fun coordinateProblem(latitude: Double, longitude: Double, accuracyMetres: Double?): String? = when {
        !latitude.isFinite() || latitude < -90.0 || latitude > 90.0 -> "Latitude must be a number between -90 and 90."
        !longitude.isFinite() || longitude < -180.0 || longitude > 180.0 -> "Longitude must be a number between -180 and 180."
        accuracyMetres != null && (!accuracyMetres.isFinite() || accuracyMetres < 0.0) ->
            "Measured accuracy must be a non-negative number."
        else -> null
    }

    /** Manual correction text check: same bounds, but the text must parse at all. */
    fun manualProblem(latitudeText: String, longitudeText: String): String? = when {
        parse(latitudeText) == null -> "Latitude must be a number between -90 and 90."
        parse(longitudeText) == null -> "Longitude must be a number between -180 and 180."
        else -> coordinateProblem(parse(latitudeText)!!, parse(longitudeText)!!, null)
    }

    /** Parses a manual entry, or null when the text is not a finite coordinate number. */
    fun parse(text: String): Double? = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() }

    /**
     * A stored coordinate is presentable only when it still satisfies the bounds it was
     * saved under; this guards reopening a record whose row predates stricter validation.
     */
    fun coordinateProblem(coordinate: Coordinate): String? =
        coordinateProblem(coordinate.latitude, coordinate.longitude, coordinate.accuracyMetres)
}
