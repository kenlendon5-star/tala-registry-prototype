package ph.tala.registry.ui.form

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ph.tala.registry.domain.form.FormRules
import ph.tala.registry.domain.model.*
import java.time.LocalDate

data class FormKey(val route: String, val scope: AnswerScope)

data class FormSessionState(
    val drafts: Map<AnswerScope, AnswerState> = emptyMap(),
    val attempts: Map<FormKey, Int> = emptyMap(),
) {
    fun form(section: SectionSpec, scope: AnswerScope, today: LocalDate = LocalDate.now()): SectionFormState {
        checkScope(section, scope)
        val answers = drafts[scope].orEmpty()
        val attempt = attempts[FormKey(section.route, scope)] ?: 0
        return SectionFormState(answers, if (attempt > 0) FormRules.validate(section, answers, today) else emptyMap(), attempt)
    }
}

data class SectionFormState(
    val answers: AnswerState = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
    val validationAttempt: Int = 0,
) {
    val checkedAndValid: Boolean get() = validationAttempt > 0 && errors.isEmpty()
}

/** Session-only drafts. Day 5/6 will connect durable saves; this model never reports a save. */
class FormSessionViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(FormSessionState())
    val state = mutableState.asStateFlow()

    fun answer(section: SectionSpec, scope: AnswerScope, key: String, value: AnswerValue) {
        checkScope(section, scope)
        mutableState.update { current ->
            current.copy(drafts = current.drafts + (scope to FormRules.update(section, current.drafts[scope].orEmpty(), key, value)))
        }
    }

    fun check(section: SectionSpec, scope: AnswerScope) {
        checkScope(section, scope)
        val key = FormKey(section.route, scope)
        mutableState.update { it.copy(attempts = it.attempts + (key to ((it.attempts[key] ?: 0) + 1))) }
    }
}

private fun checkScope(section: SectionSpec, scope: AnswerScope) {
    require(section.scopedToMember == (scope is AnswerScope.Member)) { "Section and answer scope do not match" }
}
