package ph.tala.registry.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ph.tala.registry.domain.model.HouseholdId

data class InterviewResume(val householdId: HouseholdId, val section: String = "setup", val memberId: String? = null)

interface InterviewResumeStore {
    val recent: Flow<InterviewResume?>
    suspend fun remember(resume: InterviewResume)
}

private val Context.interviewResumeDataStore by preferencesDataStore(name = "interview_resume")

/** Stores navigation context only. Answers continue to save through the interview repository. */
class DataStoreInterviewResumeStore(private val store: DataStore<Preferences>) : InterviewResumeStore {
    constructor(context: Context) : this(context.applicationContext.interviewResumeDataStore)
    private val household = stringPreferencesKey("household")
    private val section = stringPreferencesKey("section")
    private val member = stringPreferencesKey("member")
    override val recent = store.data.map { values ->
        values[household]?.takeIf { it.isNotBlank() }?.let {
            InterviewResume(HouseholdId(it), values[section] ?: "setup", values[member])
        }
    }
    override suspend fun remember(resume: InterviewResume) {
        store.edit { values ->
            values[household] = resume.householdId.value
            values[section] = resume.section
            resume.memberId?.let { values[member] = it } ?: values.remove(member)
        }
    }
}
