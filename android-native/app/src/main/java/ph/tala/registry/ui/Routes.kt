package ph.tala.registry.ui

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavType
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.MemberId

/** Screens and callbacks exchange record IDs, never positions or entire mutable records. */
sealed interface Destination {
    val route: String
    data object Home : Destination { override val route = "home" }
    data object Households : Destination { override val route = "households" }
    data object Profile : Destination { override val route = "profile" }
    data object Account : Destination { override val route = "account" }
    data object Map : Destination { override val route = "map" }
    data object Reports : Destination { override val route = "reports" }
    data object HouseholdAdd : Destination { override val route = "household/add" }
    data class Questionnaire(val id: HouseholdId, val section: String = "setup", val member: MemberId? = null) : Destination {
        override val route get() = "questionnaire/${Uri.encode(id.value)}?section=${Uri.encode(section)}" + (member?.let { "&member=${Uri.encode(it.value)}" } ?: "")
        companion object { const val PATTERN = "questionnaire/{householdId}?section={section}&member={member}" }
    }
    data class HouseholdEdit(val id: HouseholdId) : Destination {
        override val route: String get() = "household/${Uri.encode(id.value)}/edit"
        companion object {
            const val ARGUMENT = "householdId"
            const val PATTERN = "household/{householdId}/edit"
        }
    }
    data class MemberAdd(val householdId: HouseholdId) : Destination {
        override val route: String get() = "household/${Uri.encode(householdId.value)}/member/add"
        companion object {
            const val ARGUMENT = "householdId"
            const val PATTERN = "household/{householdId}/member/add"
        }
    }
    data class MemberEdit(val householdId: HouseholdId, val memberId: MemberId) : Destination {
        override val route: String get() =
            "household/${Uri.encode(householdId.value)}/member/${Uri.encode(memberId.value)}"
        companion object {
            const val HOUSEHOLD_ARGUMENT = "householdId"
            const val ARGUMENT = "memberId"
            const val PATTERN = "household/{householdId}/member/{memberId}"
        }
    }
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
