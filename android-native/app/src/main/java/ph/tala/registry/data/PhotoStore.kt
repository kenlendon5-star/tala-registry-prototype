package ph.tala.registry.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.model.EnumeratorProfile
import ph.tala.registry.domain.model.MemberId
import ph.tala.registry.domain.model.HouseholdId
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

interface PhotoRepository {
    suspend fun saveMemberPhoto(source: Uri, householdId: HouseholdId, memberId: MemberId, repository: HouseholdRepository): WriteResult = WriteResult.Failed("Member pictures are unavailable.")
    suspend fun removeMemberPhoto(householdId: HouseholdId, memberId: MemberId, repository: HouseholdRepository): WriteResult = WriteResult.Failed("Member pictures are unavailable.")
    suspend fun saveHouseholdPhoto(source: Uri, householdId: HouseholdId, repository: HouseholdRepository): WriteResult
    suspend fun removeHouseholdPhoto(householdId: HouseholdId, repository: HouseholdRepository): WriteResult
    suspend fun saveProfilePhoto(source: Uri, profile: EnumeratorProfile, profiles: EnumeratorProfileStore): WriteResult
    suspend fun removeProfilePhoto(profile: EnumeratorProfile, profiles: EnumeratorProfileStore): WriteResult
    /** Saves the consent signature pad's rendered mark; the UI owns the bounded encoding. */
    suspend fun saveSignature(bytes: ByteArray, householdId: HouseholdId, repository: HouseholdRepository): WriteResult = WriteResult.Failed("Signatures are unavailable.")
    suspend fun removeSignature(householdId: HouseholdId, repository: HouseholdRepository): WriteResult = WriteResult.Failed("Signatures are unavailable.")
}

/** One app-scoped instance serializes media changes. A durable reference is committed before
 * removing the old file. Cancellation cannot split the reference/cleanup sequence. After a
 * process interruption, cleanupUnreferenced removes only our UUID-named, unreferenced copies.
 * Tests inject a private root; no test owns the application's whole media directory. */
class PhotoStore(context: Context, private val root: File = context.filesDir) : PhotoRepository, ph.tala.registry.data.backup.AttachmentSource, ph.tala.registry.data.backup.RestoredAttachmentStore {
    private val resolver = context.applicationContext.contentResolver
    private val writes = Mutex()
    val maxBytes = 5 * 1024 * 1024

    override suspend fun saveHouseholdPhoto(source: Uri, householdId: HouseholdId, repository: HouseholdRepository) = operation {
        val old = repository.interviewRecord(householdId)?.attachments?.find { it.kind == HouseholdRepository.HOUSEHOLD_PHOTO }?.relativePath
        replace(source, "attachments", old) { path -> repository.saveHouseholdPhotoAttachment(householdId, path, "image/jpeg") }
    }

    override suspend fun removeHouseholdPhoto(householdId: HouseholdId, repository: HouseholdRepository) = operation {
        val old = repository.interviewRecord(householdId)?.attachments?.find { it.kind == HouseholdRepository.HOUSEHOLD_PHOTO }?.relativePath
        val result = repository.removeHouseholdPhotoAttachment(householdId)
        if (result is WriteResult.Saved) removeOld(old) else result
    }

    override suspend fun saveProfilePhoto(source: Uri, profile: EnumeratorProfile, profiles: EnumeratorProfileStore) = operation {
        val old = profiles.profile.first().photoRelativePath
        replace(source, "profile", old) { path -> profiles.save(profile.copy(photoRelativePath = path)) }
    }

    override suspend fun removeProfilePhoto(profile: EnumeratorProfile, profiles: EnumeratorProfileStore) = operation {
        val old = profiles.profile.first().photoRelativePath
        val result = profiles.save(profile.copy(photoRelativePath = null))
        if (result is WriteResult.Saved) removeOld(old) else result
    }

    override suspend fun saveMemberPhoto(source: Uri, householdId: HouseholdId, memberId: MemberId, repository: HouseholdRepository) = operation {
        val old = repository.interviewRecord(householdId)?.attachments?.find { it.kind == HouseholdRepository.MEMBER_PHOTO && it.memberId == memberId.value }?.relativePath
        replace(source, "attachments", old) { path -> repository.saveMemberPhotoAttachment(householdId, memberId, path, "image/jpeg") }
    }

    override suspend fun saveSignature(bytes: ByteArray, householdId: HouseholdId, repository: HouseholdRepository) = operation {
        if (bytes.isEmpty() || bytes.size > maxBytes) throw InvalidPhoto("The drawn signature is too large to save.")
        val old = repository.interviewRecord(householdId)?.attachments?.find { it.kind == HouseholdRepository.SIGNATURE }?.relativePath
        stage("attachments", old, bytes, extension = "png") { path -> repository.saveSignatureAttachment(householdId, path, "image/png") }
    }

    override suspend fun removeSignature(householdId: HouseholdId, repository: HouseholdRepository) = operation {
        val old = repository.interviewRecord(householdId)?.attachments?.find { it.kind == HouseholdRepository.SIGNATURE }?.relativePath
        val result = repository.removeSignatureAttachment(householdId)
        if (result is WriteResult.Saved) removeOld(old) else result
    }

    override suspend fun removeMemberPhoto(householdId: HouseholdId, memberId: MemberId, repository: HouseholdRepository) = operation {
        val old = repository.interviewRecord(householdId)?.attachments?.find { it.kind == HouseholdRepository.MEMBER_PHOTO && it.memberId == memberId.value }?.relativePath
        val result = repository.removeMemberPhotoAttachment(householdId, memberId)
        if (result is WriteResult.Saved) removeOld(old) else result
    }

    private suspend fun operation(block: suspend () -> WriteResult): WriteResult = withContext(Dispatchers.IO + NonCancellable) {
        writes.withLock {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (bad: InvalidPhoto) { WriteResult.Failed(bad.message ?: "Choose a valid JPEG, PNG or WebP picture.") }
            catch (_: Exception) { WriteResult.Failed("Could not update the picture. Check available storage and retry.") }
        }
    }

    private suspend fun replace(source: Uri, directory: String, old: String?, commit: suspend (String) -> WriteResult): WriteResult {
        val bytes = normalize(source)
        return stage(directory, old, bytes, commit = commit)
    }

    /** Stages the encoded copy, commits the durable reference, then removes the old file. */
    private suspend fun stage(directory: String, old: String?, bytes: ByteArray, extension: String = "jpg", commit: suspend (String) -> WriteResult): WriteResult {
        val folder = File(root, directory)
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Media directory unavailable")
        val relative = "$directory/${UUID.randomUUID()}.$extension"
        val target = resolve(relative)
        try {
            target.outputStream().use { stream -> stream.write(bytes); stream.fd.sync() }
        } catch (failure: Exception) {
            target.delete()
            throw failure
        }
        // Do not delete the staged copy on an unknown commit outcome. Startup recovery checks
        // durable references before deleting orphans; this also handles process termination.
        val result = commit(relative)
        if (result is WriteResult.Failed) {
            return if (target.delete() || !target.exists()) result else WriteResult.Failed("Picture was not saved. A temporary copy needs cleanup; retry after checking storage.")
        }
        return removeOld(old)
    }

    private fun removeOld(path: String?): WriteResult {
        if (path == null) return WriteResult.Saved
        val file = resolve(path)
        return if (!file.exists() || file.delete()) WriteResult.Saved
        else WriteResult.Failed("The picture change is saved, but the old copy could not be removed. Cleanup will retry when the app opens.")
    }

    private fun resolve(path: String): File {
        require(Regex("(attachments|profile)/[a-zA-Z0-9_-]+\\.[a-zA-Z0-9]+").matches(path))
        val result = File(root, path).canonicalFile
        require(result.toPath().startsWith(root.canonicalFile.toPath()))
        return result
    }

    /** Export-side read behind the same traversal guard; null when the file is gone. */
    override suspend fun read(relativePath: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val file = resolve(relativePath)
            if (file.isFile) file.readBytes() else null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun stage(bytes: ByteArray, mimeType: String): String = withContext(Dispatchers.IO + NonCancellable) {
        writes.withLock {
            require(bytes.isNotEmpty() && bytes.size <= maxBytes)
            require(mimeType in setOf("image/jpeg", "image/png", "image/webp"))
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outMimeType == mimeType)
            val extension = when (mimeType) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                else -> "webp"
            }
            val relative = "attachments/${UUID.randomUUID()}.$extension"
            val target = resolve(relative)
            target.parentFile?.mkdirs()
            try {
                target.outputStream().use { stream -> stream.write(bytes); stream.fd.sync() }
            } catch (error: Exception) {
                target.delete()
                throw error
            }
            relative
        }
    }

    override suspend fun discard(relativePaths: Collection<String>) = withContext(Dispatchers.IO + NonCancellable) {
        writes.withLock {
            relativePaths.distinct().forEach { path -> runCatching { resolve(path).delete() } }
        }
    }

    /** References are read inside the same mutex as saves, never from a stale startup snapshot. */
    suspend fun cleanupUnreferenced(references: suspend () -> Set<String>): WriteResult = operation {
        val live = references()
        var failed = false
        for (directory in listOf("profile", "attachments")) {
            File(root, directory).listFiles()?.filter { Regex("[0-9a-f-]{36}\\.(jpg|png)").matches(it.name) }?.forEach { file ->
                if ("$directory/${file.name}" !in live && !file.delete()) failed = true
            }
        }
        if (failed) WriteResult.Failed("Unused picture copies could not be removed. Check storage and reopen the app.") else WriteResult.Saved
    }

    private fun normalize(source: Uri): ByteArray {
        val input = resolver.openInputStream(source) ?: throw InvalidPhoto("The selected picture could not be opened.")
        val bytes = input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = stream.read(buffer, 0, minOf(buffer.size, maxBytes + 1 - out.size()))
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > maxBytes) throw InvalidPhoto("Choose a picture of 5 MiB or less.")
            }
            out.toByteArray()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outMimeType !in setOf("image/jpeg", "image/png", "image/webp") || bounds.outWidth <= 0 || bounds.outHeight <= 0)
            throw InvalidPhoto("Choose a valid JPEG, PNG or WebP picture.")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3200) sample *= 2
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw InvalidPhoto("This picture could not be decoded.")
        try {
            val orientation = bytes.inputStream().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(-90f); postScale(-1f, 1f) }
                    8 -> setRotate(-90f)
                }
            }
            val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (oriented !== bitmap) { bitmap.recycle(); bitmap = oriented }
            val ratio = minOf(1f, 1600f / maxOf(bitmap.width, bitmap.height))
            if (ratio < 1f) {
                val scaled = Bitmap.createScaledBitmap(bitmap, maxOf(1, (bitmap.width * ratio).toInt()), maxOf(1, (bitmap.height * ratio).toInt()), true)
                bitmap.recycle(); bitmap = scaled
            }
            for (quality in listOf(90, 80, 65, 50, 35)) {
                val out = ByteArrayOutputStream()
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) throw InvalidPhoto("This picture could not be saved.")
                if (out.size() <= 1024 * 1024) return out.toByteArray()
            }
            throw InvalidPhoto("This picture could not be reduced to 1 MiB. Choose a smaller image.")
        } finally { bitmap.recycle() }
    }

    private class InvalidPhoto(message: String) : IOException(message)
}
