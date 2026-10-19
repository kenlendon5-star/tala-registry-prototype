package ph.tala.registry.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

/** Foreground framework provider without Play services or network permission.
 * getCurrentLocation may reuse a recent fix (Android LocationManager reference).
 * Subscribe until a measurement newer than this request arrives, then stop.
 * The ViewModel bounds acquisition time and validates wall-clock metadata too.
 */
class AndroidLocationClient(context: Context) : LocationClient {
    private val context = context.applicationContext
    private val manager = context.getSystemService(LocationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    override fun locationEnabled(): Boolean = try {
        val m = manager
        m != null && if (Build.VERSION.SDK_INT >= 28) m.isLocationEnabled else
            m.isProviderEnabled(LocationManager.GPS_PROVIDER) || m.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    } catch (_: SecurityException) { false }

    @SuppressLint("MissingPermission") // Grant checked before selection; revocation caught below.
    override fun requestFix(onResult: (FixOutcome) -> Unit): Disposable {
        val closed = AtomicBoolean(false)
        val m = manager
        var listener: LocationListener? = null
        fun stop() { listener?.let { try { m?.removeUpdates(it) } catch (_: SecurityException) { } } }
        fun finish(result: FixOutcome) {
            handler.post {
                if (closed.compareAndSet(false, true)) { stop(); onResult(result) }
            }
        }
        val permissions = AndroidLocationPermissions(context)
        val providers = try { m?.getProviders(true).orEmpty() } catch (_: SecurityException) { emptyList() }
        val candidates = if (permissions.fineGranted()) listOf("gps", "fused", "network") else
            if (permissions.coarseGranted()) listOf("fused", "network") else emptyList()
        val provider = candidates.firstOrNull { it in providers }
        if (m == null || provider == null) finish(FixOutcome.Unavailable)
        else {
            val started = SystemClock.elapsedRealtimeNanos()
            listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (closed.get() || location.elapsedRealtimeNanos < started) return
                    if (!location.hasAccuracy() || !location.accuracy.isFinite() || location.accuracy < 0) finish(FixOutcome.Unavailable)
                    else finish(FixOutcome.Fix(location.latitude, location.longitude, location.accuracy.toDouble(), location.time))
                }
                override fun onProviderDisabled(provider: String) { finish(FixOutcome.Unavailable) }
                override fun onProviderEnabled(provider: String) = Unit
                @Deprecated("Required for older Android listener implementations")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            try { m.requestLocationUpdates(provider, 0L, 0f, requireNotNull(listener), Looper.getMainLooper()) }
            catch (_: SecurityException) { finish(FixOutcome.Unavailable) }
            catch (_: IllegalArgumentException) { finish(FixOutcome.Unavailable) }
        }
        return Disposable { if (closed.compareAndSet(false, true)) stop() }
    }
}
