package ph.tala.registry.ui.account

import ph.tala.registry.domain.model.RecordTextLimits

object AccountInputRules {
    fun signInProblem(email: String, password: String): String? = when {
        !isValidEmail(email) -> "Enter a valid email address."
        password.isBlank() -> "Enter your password."
        else -> null
    }

    fun signUpProblem(
        email: String,
        password: String,
        confirmPassword: String,
        displayName: String,
        barangay: String,
    ): String? = when {
        !isValidEmail(email) -> "Enter a valid email address."
        password.length < MIN_PASSWORD_LENGTH -> "Use a password with at least 8 characters."
        password != confirmPassword -> "Passwords do not match."
        displayName.trim().isEmpty() -> "Enter the enumerator name."
        displayName.trim().length > RecordTextLimits.NAME -> "Enumerator name must be ${RecordTextLimits.NAME} characters or fewer."
        barangay.trim().isEmpty() -> "Enter the barangay."
        barangay.trim().length > RecordTextLimits.ADDRESS -> "Barangay must be ${RecordTextLimits.ADDRESS} characters or fewer."
        else -> null
    }

    private fun isValidEmail(value: String): Boolean {
        val email = value.trim()
        return email.length in 3..MAX_EMAIL_LENGTH && EMAIL.matches(email)
    }

    private const val MIN_PASSWORD_LENGTH = 8
    private const val MAX_EMAIL_LENGTH = 254
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
}
