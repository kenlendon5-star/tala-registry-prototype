package ph.tala.registry.ui.interview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.net.Uri
import ph.tala.registry.data.EnumeratorProfileStore
import ph.tala.registry.data.HouseholdRepository
import ph.tala.registry.data.PhotoRepository
import ph.tala.registry.data.local.StoredRecord
import ph.tala.registry.data.local.AnswerCodec
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
    val progress: InterviewProgress = InterviewProgress(0, QuestionnaireSections.all.size),
) {
    val section: SectionSpec get() = QuestionnaireSections.get(route)
}

/**
 * One scope at a time; save-before-switch and compare-with-base prevent cross-member/stale
 * writes. The optional stores wire the D08 registration extras: app-private household
 * pictures and the enumerator profile that stamps a fresh setup section.
 */
class InterviewWorkspaceViewModel(
    private val id: HouseholdId,
    private val repository: HouseholdRepository,
    private val photos: PhotoRepository? = null,
    private val profiles: EnumeratorProfileStore? = null,
    initialRoute: String = "setup",
    initialMember: String? = null,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val restoring = savedState.get<String>(HOUSEHOLD) == id.value
    private val restoredRoute = savedState.get<String>(ROUTE)?.takeIf { route -> QuestionnaireSections.all.any { it.route == route } }
    private val mutable = MutableStateFlow(WorkspaceState(
        route = if (restoring) restoredRoute ?: initialRoute else initialRoute,
        memberId = if (restoring) savedState[MEMBER] else initialMember,
    ))
    val state = mutable.asStateFlow()
    private var base: AnswerState = emptyMap()
    private var dirty = false
    private var pending: Job? = null
    private val operations = Mutex()

    init { viewModelScope.launch { operations.withLock { load(mutable.value.route, mutable.value.memberId, restoreDraft = restoring) } } }

    fun reload() {
        pending?.cancel()
        viewModelScope.launch { operations.withLock { load(mutable.value.route, mutable.value.memberId) } }
    }

    private suspend fun load(route: String, memberId: String?, restoreDraft: Boolean = false) {
        mutable.value = mutable.value.copy(loading = true)
        val record = repository.interviewRecord(id)
        val section = QuestionnaireSections.get(route)
        val selected = if (section.scopedToMember) memberId?.takeIf { key -> record?.members?.any { it.id == key } == true }
            ?: record?.members?.firstOrNull()?.id else null
        base = record?.let { InterviewRules.answers(it, section, selected) }.orEmpty()
        // Attribution is saved at creation, never retroactively taken from the current profile.
        val restored = if (restoreDraft && selected == memberId && savedState.contains(DRAFT)) decode(DRAFT) else null
        val answers = restored?.let { InterviewRules.clean(section, it) } ?: base
        // Keep the original comparison base when restoring a dirty draft. A changed
        // durable section must still be refused, not silently overwritten after death.
        if (restored != null && answers != base && base != decode(IN_FLIGHT)) base = decode(BASE) ?: base
        savedState.remove<ArrayList<String>>(IN_FLIGHT)
        dirty = answers != base
        mutable.value = WorkspaceState(loading = false, record = record, route = route, memberId = selected, answers = answers,
            issues = record?.let(InterviewRules::issues).orEmpty(),
            progress = record?.let { InterviewRules.progress(it) } ?: InterviewProgress(0, QuestionnaireSections.all.size),
            message = if (record == null) "Household unavailable." else if (dirty) "Restored unsaved changes. Save to retry." else "")
        rememberDraft()
    }

    /** Bundle-compatible primitives only; never put Room records or bitmaps in saved state. */
    private fun rememberDraft() {
        savedState[HOUSEHOLD] = id.value
        savedState[ROUTE] = mutable.value.route
        savedState[MEMBER] = mutable.value.memberId
        savedState[DRAFT] = encode(mutable.value.answers)
        savedState[BASE] = encode(base)
    }

    private fun encode(answers: AnswerState) = ArrayList(answers.flatMap { (key, value) ->
        val (kind, json) = AnswerCodec.encode(value)
        listOf(key, kind, json)
    })

    private fun decode(key: String): AnswerState? = savedState.get<ArrayList<String>>(key)?.let { values ->
        if (values.size % 3 != 0) return@let null
        runCatching { values.chunked(3).associate { it[0] to AnswerCodec.decode(it[1], it[2]) } }.getOrNull()
    }

    fun answer(key: String, value: AnswerValue) {
        val current = mutable.value
        if (current.loading || current.record == null) return
        val answers = FormRules.update(current.section, current.answers, key, value)
        dirty = answers != base
        mutable.value = current.copy(answers = answers, phase = if (current.phase == SavePhase.Saving) SavePhase.Saving else SavePhase.Idle,
            message = if (current.phase == SavePhase.Saving) "Saving…" else if (dirty) "Unsaved changes" else "",
            errors = if (current.attempt > 0) validate(answers) else emptyMap())
        rememberDraft()
        // Refusing consent drops the drawn mark, as the prototype reference does.
        if (current.route == "consent" && key == "consent" && answers.scalar("consent") == "No" &&
            current.record?.attachments?.any { it.kind == HouseholdRepository.SIGNATURE } == true) {
            pending?.cancel()
            viewModelScope.launch { operations.withLock { persist() } }
            return
        }
        pending?.cancel()
        pending = viewModelScope.launch {
            delay(500)
            // Only the debounce is cancellable by a new keystroke. A started write
            // finishes, then the next operation saves any newer answers.
            pending = null
            operations.withLock { persist() }
        }
    }

    /** Section errors, with the typed-name requirement met by an existing drawn signature. */
    private fun validate(answers: AnswerState): Map<String, String> {
        val errors = FormRules.validate(currentSection(), answers).toMutableMap()
        if (answers.scalar("consent") == "Yes" && answers.scalar("signedName").isBlank() &&
            mutable.value.record?.attachments?.any { it.kind == HouseholdRepository.SIGNATURE } == true)
            errors.remove("signedName")
        return errors
    }

    private fun currentSection() = mutable.value.section

    private suspend fun clearSignatureAfterRefusal(): Boolean {
        val store = photos ?: run {
            mutable.value = mutable.value.copy(phase = SavePhase.Failed, message = "Signature cleanup is unavailable. Retry before leaving.")
            return false
        }
        mutable.value = mutable.value.copy(phase = SavePhase.Saving)
        val result = store.removeSignature(id, repository)
        signatureResult(result, "Refusal recorded. The drawn signature was removed.")
        return result is WriteResult.Saved
    }

    private suspend fun persist(): Boolean {
        // A media operation can fail after the text draft was already committed. It
        // must remain retryable without forcing an unrelated edit or discarding state.
        while (dirty) {
            val current = mutable.value
            if (current.section.scopedToMember && current.memberId == null) return false
            mutable.value = current.copy(phase = SavePhase.Saving, message = "Saving…")
            // If the process dies after commit but before acknowledgement, a new
            // process can recognize our own committed snapshot and retry later edits.
            savedState[IN_FLIGHT] = encode(InterviewRules.clean(current.section, current.answers))
            val result = repository.saveDraftSection(id, current.route, current.memberId?.let(::MemberId), current.answers, base)
            if (result is WriteResult.Saved) {
                base = InterviewRules.clean(current.section, current.answers)
                val saved = repository.interviewRecord(id)
                val latest = InterviewRules.clean(current.section, mutable.value.answers)
                dirty = latest != base
                mutable.value = mutable.value.copy(answers = latest, record = saved,
                    phase = if (dirty) SavePhase.Idle else SavePhase.Saved,
                    message = if (dirty) "Unsaved changes" else "Saved on this device.",
                    issues = saved?.let(InterviewRules::issues).orEmpty(),
                    progress = saved?.let { InterviewRules.progress(it) } ?: InterviewProgress(0, QuestionnaireSections.all.size))
                rememberDraft()
                savedState.remove<ArrayList<String>>(IN_FLIGHT)
                // A keystroke can arrive while Room suspends. Drain that newer draft
                // before switching scope or leaving; never replace it with load().
                continue
            }
            mutable.value = mutable.value.copy(phase = SavePhase.Failed, message = (result as WriteResult.Failed).message)
            rememberDraft()
            savedState.remove<ArrayList<String>>(IN_FLIGHT)
            return false
        }
        val latest = mutable.value
        if (latest.route == "consent" && latest.answers.scalar("consent") == "No" && hasSignature)
            return clearSignatureAfterRefusal()
        return true
    }

    fun save() { pending?.cancel(); viewModelScope.launch { operations.withLock { persist() } } }
    fun recordVisit() {
        pending?.cancel()
        viewModelScope.launch { operations.withLock {
            if (mutable.value.route != "visit" || !persist()) return@withLock
            val errors = validate(mutable.value.answers)
            mutable.value = mutable.value.copy(errors = errors, attempt = mutable.value.attempt + 1)
            if (errors.isNotEmpty()) return@withLock
            mutable.value = mutable.value.copy(phase = SavePhase.Saving)
            val result = repository.recordVisit(id, base)
            mutable.value = mutable.value.copy(record = repository.interviewRecord(id),
                phase = if (result is WriteResult.Saved) SavePhase.Saved else SavePhase.Failed,
                message = if (result is WriteResult.Saved) "Visit result recorded on this device." else (result as WriteResult.Failed).message)
        } }
    }
    fun check() {
        val current = mutable.value
        mutable.value = current.copy(errors = validate(current.answers), attempt = current.attempt + 1)
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
            if (persist()) {
                val record = repository.interviewRecord(id)
                mutable.value = mutable.value.copy(showingReview = true,
                    issues = record?.let(InterviewRules::issues).orEmpty(),
                    progress = record?.let { InterviewRules.progress(it) } ?: InterviewProgress(0, QuestionnaireSections.all.size))
            }
        } }
    }
    fun complete() { viewModelScope.launch { operations.withLock {
        if (!persist()) return@withLock
        val result = repository.reviewInterview(id)
        mutable.value = mutable.value.copy(record = repository.interviewRecord(id), phase = if (result is WriteResult.Saved) SavePhase.Saved else SavePhase.Failed,
            message = if (result is WriteResult.Saved) "Interview reviewed on this device." else (result as WriteResult.Failed).message)
    } } }

    val hasHouseholdPhoto: Boolean
        get() = mutable.value.record?.attachments?.any { it.kind == HouseholdRepository.HOUSEHOLD_PHOTO } == true

    val hasMemberPhoto: Boolean
        get() = mutable.value.record?.attachments?.any { it.kind == HouseholdRepository.MEMBER_PHOTO && it.memberId == mutable.value.memberId } == true

    fun savePhoto(uri: Uri) {
        val store = photos ?: return
        pending?.cancel()
        viewModelScope.launch { operations.withLock {
            if (!persist()) return@withLock
            mutable.value = mutable.value.copy(phase = SavePhase.Saving)
            val result = store.saveHouseholdPhoto(uri, id, repository)
            photoResult(result)
        } }
    }

    fun saveMemberPhoto(uri: Uri, member: MemberId) {
        val store = photos ?: return
        pending?.cancel()
        viewModelScope.launch { operations.withLock {
            if (!persist()) return@withLock
            mutable.value = mutable.value.copy(phase = SavePhase.Saving)
            val result = store.saveMemberPhoto(uri, id, member, repository)
            photoResult(result)
        } }
    }

    fun removePhoto() {
        val store = photos ?: return
        pending?.cancel()
        viewModelScope.launch { operations.withLock {
            if (!persist()) return@withLock
            mutable.value = mutable.value.copy(phase = SavePhase.Saving)
            val result = store.removeHouseholdPhoto(id, repository)
            photoResult(result)
        } }
    }

    fun removeMemberPhoto() {
        val store = photos ?: return
        val member = mutable.value.memberId?.let(::MemberId) ?: return
        pending?.cancel()
        viewModelScope.launch { operations.withLock {
            if (!persist()) return@withLock
            mutable.value = mutable.value.copy(phase = SavePhase.Saving)
            val result = store.removeMemberPhoto(id, member, repository)
            photoResult(result)
        } }
    }

    private suspend fun photoResult(result: WriteResult) {
        mutable.value = mutable.value.copy(record = repository.interviewRecord(id),
            phase = if (result is WriteResult.Failed) SavePhase.Failed else if (dirty) SavePhase.Idle else SavePhase.Saved,
            message = if (result is WriteResult.Failed) result.message else if (dirty) "Unsaved changes" else "Picture saved on this device.")
    }

    val hasSignature: Boolean
        get() = mutable.value.record?.attachments?.any { it.kind == HouseholdRepository.SIGNATURE } == true

    /** Saves the pad's rendered mark (already encoded by the UI) as the signature attachment. */
    fun saveSignature(bytes: ByteArray) {
        val store = photos ?: return
        if (bytes.isEmpty()) return
        pending?.cancel()
        viewModelScope.launch { operations.withLock {
            if (!persist()) return@withLock
            mutable.value = mutable.value.copy(phase = SavePhase.Saving)
            val result = store.saveSignature(bytes, id, repository)
            signatureResult(result, "Signature saved on this device.")
        } }
    }

    fun clearSignature() {
        val store = photos ?: return
        pending?.cancel()
        viewModelScope.launch { operations.withLock {
            if (!persist()) return@withLock
            mutable.value = mutable.value.copy(phase = SavePhase.Saving)
            val result = store.removeSignature(id, repository)
            signatureResult(result, "Signature cleared.")
        } }
    }

    private suspend fun signatureResult(result: WriteResult, savedMessage: String) {
        val record = repository.interviewRecord(id)
        mutable.value = mutable.value.copy(record = record,
            phase = if (result is WriteResult.Failed) SavePhase.Failed else if (dirty) SavePhase.Idle else SavePhase.Saved,
            message = if (result is WriteResult.Failed) result.message else if (dirty) "Unsaved changes" else savedMessage,
            issues = record?.let(InterviewRules::issues).orEmpty(),
            progress = record?.let { InterviewRules.progress(it) } ?: InterviewProgress(0, QuestionnaireSections.all.size))
    }

    private companion object {
        const val HOUSEHOLD = "workspace.household"
        const val ROUTE = "workspace.route"
        const val MEMBER = "workspace.member"
        const val DRAFT = "workspace.draft"
        const val BASE = "workspace.base"
        const val IN_FLIGHT = "workspace.inFlight"
    }
}
