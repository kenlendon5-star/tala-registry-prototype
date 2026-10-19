package ph.tala.registry.location

/**
 * Deterministic [LocationClient]: requests are recorded and outcomes are delivered
 * only when the test calls [deliver], so timeouts, cancellation and late callbacks
 * are reproducible on the JVM. Delivery always invokes the callback — exactly like a
 * platform late callback — and the ViewModel's generation binding must reject it.
 */
class FakeLocationClient(
    private var enabled: Boolean = true,
    private var fine: Boolean = true,
    private var coarse: Boolean = true,
) : LocationClient, LocationPermissions {
    class Request(val onResult: (FixOutcome) -> Unit) {
        var disposed = false
            private set
        internal fun dispose() { disposed = true }
    }

    val requests = mutableListOf<Request>()
    var disposals = 0
        private set

    fun setLocationEnabled(value: Boolean) { enabled = value }
    fun setPermissions(fineValue: Boolean, coarseValue: Boolean) { fine = fineValue; coarse = coarseValue }

    override fun fineGranted() = fine
    override fun coarseGranted() = coarse

    override fun locationEnabled() = enabled

    override fun requestFix(onResult: (FixOutcome) -> Unit): Disposable {
        val request = Request(onResult)
        requests.add(request)
        return Disposable { disposals++; request.dispose() }
    }

    /** Delivers an outcome through the newest (or given) request, disposed or not. */
    fun deliver(outcome: FixOutcome, request: Request = requests.last()) {
        request.onResult(outcome)
    }
}
