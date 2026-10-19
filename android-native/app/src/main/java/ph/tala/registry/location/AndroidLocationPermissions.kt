package ph.tala.registry.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Real permission precheck for the request-on-use flow. The check only reads the
 * current grant state; requesting happens in the UI through the Activity Result API
 * when the enumerator chooses to capture a location.
 */
class AndroidLocationPermissions(private val context: Context) : LocationPermissions {
    override fun fineGranted(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    override fun coarseGranted(): Boolean = granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
