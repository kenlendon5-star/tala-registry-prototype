package ph.tala.registry.ui

import android.os.Bundle
import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.data.RoomHouseholdRepository
import ph.tala.registry.data.local.*
import ph.tala.registry.domain.edit.*
import ph.tala.registry.domain.form.*
import ph.tala.registry.domain.model.*
import ph.tala.registry.ui.households.SavePhase
import ph.tala.registry.ui.interview.InterviewWorkspaceViewModel
import java.io.File

/** Real Room + Android Bundle/Parcel round trip with a destroyed and fresh ViewModel. */
class WorkspaceRestorationDeviceTest {
    @Test fun pendingDraftRestoresFromParcelBeforeAnyAutosave() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".d08test"))
        val databaseName = "workspace-restoration-test.db"
        context.deleteDatabase(databaseName)
        val database = RegistryDatabase.open(context, databaseName)
        val repo = RoomHouseholdRepository(RoomRecordStore(database))
        val hh = HouseholdId("d1200000-0000-4000-8000-000000000131")
        val member = MemberId("d1200000-0000-4000-8000-000000000132")
        try {
            assertEquals(WriteResult.Saved, repo.createHousehold(hh, HouseholdDraft("Restoration Fixture", "Fictional Street", HouseholdStatus.InProgress)))
            assertEquals(WriteResult.Saved, repo.saveMember(hh, member, MemberDraft("Sample Member")))
            withContext(Dispatchers.Main) {
                val handle = SavedStateHandle()
                val model = InterviewWorkspaceViewModel(hh, repo, initialRoute = "education", initialMember = member.value, savedState = handle)
                val owner = ViewModelStore().also { it.put("workspace", model) }
                model.state.first { !it.loading }
                model.answer("attending", AnswerValue.Scalar("No"))
                model.answer("reason", AnswerValue.Scalar("Working"))
                val bundle = Bundle()
                for (key in handle.keys()) {
                    when (val value = handle.get<Any?>(key)) {
                        null, is String -> bundle.putString(key, value as String?)
                        is ArrayList<*> -> bundle.putStringArrayList(key, ArrayList(value.map { it as String }))
                        else -> error("Unsupported saved-state value: $key")
                    }
                }
                owner.clear() // cancels the 500 ms debounce before it can write
                val parcel = Parcel.obtain()
                val restored = try {
                    parcel.writeBundle(bundle)
                    parcel.setDataPosition(0)
                    requireNotNull(parcel.readBundle(javaClass.classLoader))
                } finally { parcel.recycle() }
                @Suppress("DEPRECATION")
                val restoredHandle = SavedStateHandle(restored.keySet().associateWith { restored.get(it) })
                assertTrue(InterviewRules.answers(repo.interviewRecord(hh)!!, QuestionnaireSections.education, member.value).isEmpty())
                val resumed = InterviewWorkspaceViewModel(hh, repo, savedState = restoredHandle)
                val resumedOwner = ViewModelStore().also { it.put("workspace", resumed) }
                try {
                    resumed.state.first { !it.loading }
                    assertEquals("education", resumed.state.value.route)
                    assertEquals(member.value, resumed.state.value.memberId)
                    assertEquals("Working", resumed.state.value.answers.scalar("reason"))
                    resumed.save()
                    resumed.state.first { it.phase == SavePhase.Saved || it.phase == SavePhase.Failed }
                    assertEquals(SavePhase.Saved, resumed.state.value.phase)
                    assertEquals("Working", InterviewRules.answers(repo.interviewRecord(hh)!!, QuestionnaireSections.education, member.value).scalar("reason"))
                    val folder = File(context.getExternalFilesDir(null), "day12").apply { mkdirs() }
                    File(folder, "workspace-restoration.txt").writeText("Pending debounce cancelled; draft/route/member/base survived Android Bundle/Parcel into a fresh ViewModel and saved to real Room. This is a controlled restoration test, not an OS-kill test.\n")
                } finally { resumedOwner.clear() }
            }
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }
}
