package ph.tala.registry.ui

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavType
import ph.tala.registry.domain.model.HouseholdId

/** Screens and callbacks exchange record IDs, never positions or entire mutable records. */
sealed interface Destination {
    val route: String
    data object Home : Destination { override val route = "home" }
    data object Households : Destination { override val route = "households" }
    data class Practice(val id: HouseholdId) : Destination {
        override val route: String get() = "practice/${Uri.encode(id.value)}"
        companion object { const val PATTERN = "practice/{householdId}" }
    }
    data class Interview(val id: HouseholdId) : Destination {
        override val route: String get() = "interview/${Uri.encode(id.value)}"
        companion object {
            const val ARGUMENT = "householdId"
            const val PATTERN = "interview/{householdId}"
        }
    }
}

/** Bundle encoding keeps Navigation's saved back stack primitive and restorable. */
object HouseholdIdNavType : NavType<HouseholdId>(isNullableAllowed = false) {
    override fun put(bundle: Bundle, key: String, value: HouseholdId) = bundle.putString(key, value.value)
    override fun get(bundle: Bundle, key: String): HouseholdId? = bundle.getString(key)?.let(::HouseholdId)
    override fun parseValue(value: String): HouseholdId = HouseholdId(value)
    override fun serializeAsValue(value: HouseholdId): String = Uri.encode(value.value)
}
