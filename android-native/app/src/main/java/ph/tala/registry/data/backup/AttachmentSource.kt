package ph.tala.registry.data.backup

/**
 * Reads one stored attachment's bytes by its app-private relative path.
 * Returning null means the file is missing or unreadable — the exporter skips
 * and counts it instead of failing the record or the whole export.
 */
interface AttachmentSource {
    suspend fun read(relativePath: String): ByteArray?
}
