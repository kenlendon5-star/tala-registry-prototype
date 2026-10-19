package ph.tala.registry.location

/**
 * Injectable foreground location source. The ViewModel owns states, timeouts and
 * stale-callback rejection; a client only answers "is location usable?" and "give me
 * one fresh fix". Fakes implement this for deterministic JVM tests; the production
 * implementation uses the framework LocationManager (see [AndroidLocationClient]).
 */
interface LocationClient {
    /** True when the system location master switch leaves a provider usable. */
    fun locationEnabled(): Boolean

    /**
     * Requests exactly one fresh fix. Never returns a cached last-known position as a
     * result: every delivered [FixOutcome.Fix] is a new computation. Exactly one
     * outcome is delivered unless [Disposable.dispose] cancels the request first, and
     * no outcome is delivered after disposal.
     */
    fun requestFix(onResult: (FixOutcome) -> Unit): Disposable
}

fun interface Disposable {
    /** Cancels the pending fix request; calling twice is harmless. */
    fun dispose()
}

sealed interface FixOutcome {
    /** One fresh fix with its measured accuracy and the platform's fix time. */
    data class Fix(
        val latitude: Double,
        val longitude: Double,
        val accuracyMetres: Double,
        val capturedAt: Long,
    ) : FixOutcome

    /** The provider could not produce a fix (unavailable, or cancelled by the system). */
    data object Unavailable : FixOutcome
}

/**
 * Injectable permission gate so the ViewModel can be tested without an Android
 * context. The UI layer performs the actual system permission request and reports
 * the result back; this interface only answers the request-on-use precheck.
 */
interface LocationPermissions {
    fun fineGranted(): Boolean
    fun coarseGranted(): Boolean
}
