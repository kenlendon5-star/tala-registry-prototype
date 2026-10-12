package ph.tala.registry.ui.interview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ph.tala.registry.data.HouseholdRepository
import ph.tala.registry.data.local.StoredRecord
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.form.*
import ph.tala.registry.domain.model.*
import ph.tala.registry.ui.households.SavePhase

data class WorkspaceState(
    val loading: Boolean = true,
    val record: StoredRecord? = null,
    val route: String = "setup",
    val memberId: String? = null,
    val answers: AnswerState = emptyMap(),
    val phase: SavePhase = SavePhase.Idle,
    val message: String = "",
    val errors: Map<String, String> = emptyMap(),
    val attempt: Int = 0,
    val issues: List<SectionIssue> = emptyList(),
    val showingReview: Boolean = false,
) {
    val section: SectionSpec get() = QuestionnaireSections.get(route)
}

/** One scope at a time; save-before-switch and compare-with-base prevent cross-member/stale writes. */
class InterviewWorkspaceViewModel(private val id: HouseholdId, private val repository: HouseholdRepository) : ViewModel() {
    private val mutable = MutableStateFlow(WorkspaceState())
    val state = mutable.asStateFlow()
    private var base: AnswerState = emptyMap()
    private var dirty = false
    private var pending: Job? = null
    private val operations = Mutex()

    init { reload() }

    fun reload() { viewModelScope.launch { operations.withLock { load(mutable.value.route, mutable.value.memberId) } } }

    private suspend fun load(route: String, memberId: String?) {
        val record = repository.interviewRecord(id)
        val section = QuestionnaireSections.get(route)
        val selected = if (section.scopedToMember) memberId?.takeIf { key -> record?.members?.any { it.id == key } == true }
            ?: record?.members?.firstOrNull()?.id else null
        base = record?.let { InterviewRules.answers(it, section, selected) }.orEmpty()
        dirty = false
        mutable.value = WorkspaceState(loading = false, record = record, route = route, memberId = selected, answers = base,
            issues = record?.let(InterviewRules::issues).orEmpty(), message = if (record == null) "Household unavailable." else "")
    }

    fun answer(key: String, value: AnswerValue) {
        val current = mutable.value
        if (current.loading || current.phase == SavePhase.Saving || current.record == null) return
        val answers = FormRules.update(current.section, current.answers, key, value)
        dirty = answers != base
        mutable.value = current.copy(answers = answers, phase = SavePhase.Idle,
            message = if (dirty) "Unsaved changes" else "", errors = if (current.attempt > 0) FormRules.validate(current.section, answers) else emptyMap())
        pending?.cancel()
        pending = viewModelScope.launch { delay(500); operations.withLock { persist() } }
    }

    private suspend fun persist(): Boolean {
        if (!dirty) return mutable.value.phase != SavePhase.Failed
        val current = mutable.value
        if (current.section.scopedToMember && current.memberId == null) return false
        mutable.value = current.copy(phase = SavePhase.Saving, message = "Saving…")
        val result = repository.saveDraftSection(id, current.route, current.memberId?.let(::MemberId), current.answers, base)
        if (result is WriteResult.Saved) {
            base = InterviewRules.clean(current.section, current.answers)
            dirty = false
            val saved = repository.interviewRecord(id)
            mutable.value = mutable.value.copy(answers = base, record = saved, phase = SavePhase.Saved, message = "Saved on this device.",
                issues = saved?.let(InterviewRules::issues).orEmpty())
            return true
        }
        mutable.value = mutable.value.copy(phase = SavePhase.Failed, message = (result as WriteResult.Failed).message)
        return false
    }

    fun save() { pending?.cancel(); viewModelScope.launch { operations.withLock { persist() } } }
    fun check() {
        val current = mutable.value
        mutable.value = current.copy(errors = FormRules.validate(current.section, current.answers), attempt = current.attempt + 1)
    }
    fun select(route: String, member: String? = null) {
        pending?.cancel()
        viewModelScope.launch { operations.withLock { if (persist()) load(route, member) } }
    }
    fun leave(onSaved: () -> Unit) {
        pending?.cancel()
        viewModelScope.launch { operations.withLock { if (persist()) onSaved() } }
    }
    fun review() {
        pending?.cancel()
        viewModelScope.launch { operations.withLock {
            if (persist()) mutable.value = mutable.value.copy(showingReview = true,
                issues = repository.interviewRecord(id)?.let(InterviewRules::issues).orEmpty())
        } }
    }
    fun complete() { viewModelScope.launch { operations.withLock {
        if (!persist()) return@withLock
        val result = repository.reviewInterview(id)
        mutable.value = mutable.value.copy(phase = if (result is WriteResult.Saved) SavePhase.Saved else SavePhase.Failed,
            message = if (result is WriteResult.Saved) "Interview reviewed on this device." else (result as WriteResult.Failed).message)
    } } }
}
