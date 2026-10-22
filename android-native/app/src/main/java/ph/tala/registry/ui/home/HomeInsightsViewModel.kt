package ph.tala.registry.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ph.tala.registry.data.HouseholdRepository
import ph.tala.registry.data.InterviewResume
import ph.tala.registry.data.InterviewResumeStore
import ph.tala.registry.domain.form.QuestionnaireSections
import ph.tala.registry.domain.model.Household
import ph.tala.registry.domain.model.HouseholdId
import java.util.Locale

data class PurokCount(val label: String, val count: Int, val unrecorded: Boolean = false)
data class HomeInsights(
    val loading: Boolean = true,
    val puroks: List<PurokCount> = emptyList(),
    val resume: InterviewResume? = null,
    val message: String? = null,
)

/** Uses the explicit setup answer, never a substring of an address. */
fun householdPurokLabel(raw: String?): String? {
    val cleaned = raw?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotBlank() } ?: return null
    val numeric = Regex("(?i)(?:purok\\s*)?0*([0-9]+)").matchEntire(cleaned)
    return numeric?.groupValues?.get(1)?.let { "Purok ${it.toIntOrNull() ?: it}" }
        ?: cleaned.split(" ").joinToString(" ") { it.lowercase(Locale.ROOT).replaceFirstChar(Char::titlecase) }
}

fun purokCounts(households: List<Household>, answers: Map<String, String>): List<PurokCount> {
    val groups = households.filterNot { it.archived }.groupingBy { householdPurokLabel(answers[it.id.value]) }.eachCount()
    return groups.filterKeys { it != null }.map { (label, count) -> PurokCount(requireNotNull(label), count) }
        .sortedWith(compareBy<PurokCount> {
            Regex("Purok ([0-9]+)").matchEntire(it.label)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
        }.thenBy { it.label })
        .let { rows -> rows + listOfNotNull(groups[null]?.let { PurokCount("Not Recorded", it, true) }) }
}

fun validResume(reference: InterviewResume?, households: List<Household>): InterviewResume? {
    reference ?: return null
    val household = households.find { it.id == reference.householdId && !it.archived } ?: return null
    val section = QuestionnaireSections.all.find { it.route == reference.section && it.route != "members" }
        ?: QuestionnaireSections.setup
    // A removed member returns to household setup rather than opening another person's answers.
    if (section.scopedToMember && household.members.none { it.id.value == reference.memberId })
        return reference.copy(section = "setup", memberId = null)
    return reference.copy(section = section.route, memberId = reference.memberId.takeIf { section.scopedToMember })
}

class HomeInsightsViewModel(repository: HouseholdRepository, private val resumes: InterviewResumeStore) : ViewModel() {
    private val warning = MutableStateFlow<String?>(null)
    val state = combine(
        repository.households, repository.householdPuroks,
        resumes.recent.catch { error ->
            if (error is CancellationException) throw error
            warning.value = "Recent Interview Unavailable. Browse Households To Continue."
            emit(null)
        }, warning,
    ) { households, puroks, resume, message ->
        HomeInsights(false, purokCounts(households, puroks), validResume(resume, households), message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeInsights())

    fun remember(resume: InterviewResume) {
        viewModelScope.launch {
            try { resumes.remember(resume); warning.value = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { warning.value = "Could Not Remember The Recent Interview. Browse Households To Continue." }
        }
    }

    fun opened(householdId: HouseholdId) {
        viewModelScope.launch {
            try {
                val previous = resumes.recent.first()
                resumes.remember(previous?.takeIf { it.householdId == householdId } ?: InterviewResume(householdId))
                warning.value = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { warning.value = "Could Not Remember The Recent Interview. Browse Households To Continue." }
        }
    }
}
