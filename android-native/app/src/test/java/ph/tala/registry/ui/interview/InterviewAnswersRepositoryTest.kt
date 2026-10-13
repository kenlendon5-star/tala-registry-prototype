package ph.tala.registry.ui.interview

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import ph.tala.registry.data.*
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.edit.*
import ph.tala.registry.domain.model.*

class InterviewAnswersRepositoryTest {
    @Test fun memberPicturesNeverReplaceHouseholdOrOtherMemberPictures() = runTest {
        val store = FakeRecordStore()
        val repo = RoomHouseholdRepository(store)
        val hh = HouseholdId("hh"); val a = MemberId("a"); val b = MemberId("b")
        repo.createHousehold(hh, HouseholdDraft("Fixture", "Street", HouseholdStatus.InProgress))
        repo.saveMember(hh, a, MemberDraft("Maria")); repo.saveMember(hh, b, MemberDraft("Juan"))
        repo.saveHouseholdPhotoAttachment(hh, "attachments/house.jpg", "image/jpeg")
        repo.saveMemberPhotoAttachment(hh, a, "attachments/a.jpg", "image/jpeg")
        repo.saveMemberPhotoAttachment(hh, b, "attachments/b.jpg", "image/jpeg")
        repo.saveMemberPhotoAttachment(hh, a, "attachments/a-new.jpg", "image/jpeg")
        assertEquals(setOf("attachments/house.jpg", "attachments/a-new.jpg", "attachments/b.jpg"), repo.interviewRecord(hh)!!.attachments.map { it.relativePath }.toSet())
        store.failWith = "Full"
        assertTrue(repo.removeMemberPhotoAttachment(hh, a) is WriteResult.Failed)
        assertEquals(3, repo.interviewRecord(hh)!!.attachments.size)
        store.failWith = null
        repo.removeHouseholdPhotoAttachment(hh)
        assertEquals(setOf(a.value, b.value), repo.interviewRecord(hh)!!.attachments.map { it.memberId }.toSet())
        repo.removeMemberPhotoAttachment(hh, a)
        assertEquals(b.value, repo.interviewRecord(hh)!!.attachments.single().memberId)
        assertTrue(repo.saveMemberPhotoAttachment(hh, MemberId("missing"), "attachments/x.jpg", "image/jpeg") is WriteResult.Failed)
    }
}
