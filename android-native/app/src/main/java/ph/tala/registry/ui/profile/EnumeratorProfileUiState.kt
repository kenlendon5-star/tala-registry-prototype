package ph.tala.registry.ui.profile

import ph.tala.registry.ui.households.SavePhase

/** Read model exposed by [EnumeratorProfileViewModel]. */
data class EnumeratorProfileUiState(
    val name: String = "",
    val barangay: String = "",
    val photoPath: String? = null,
    val photoSaved: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val phase: SavePhase = SavePhase.Idle,
    val message: String = "",
)