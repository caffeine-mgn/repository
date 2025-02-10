package pw.binom.repo.repositories.maven

import pw.binom.crypto.MD5MessageDigest
import pw.binom.crypto.Sha1MessageDigest
import pw.binom.date.format.toDatePattern
import pw.binom.io.AsyncInput
import pw.binom.io.DataTransferSize

interface MavenRepository {
    companion object {
        private val datePattern = "yyyyMMddHHmmss".toDatePattern()
    }

    suspend fun get(
        group: MavenGroup,
        artifact: String,
        version: MavenVersion,
        file: String,
    ): AsyncInput?

    suspend fun isExist(
        group: MavenGroup,
        artifact: String,
        version: MavenVersion,
        file: String,
    ): Long?

    val readOnly: Boolean
        get() = true

    suspend fun push(group: MavenGroup, artifact: String, version: MavenVersion, file: String, from: AsyncInput) {
        throw IllegalStateException("Repository not support mutation")
    }

    suspend fun getMetaData(group: MavenGroup, artifact: String): MetaData?
    suspend fun getMetaDataMd5(group: MavenGroup, artifact: String): ByteArray? {
        val data = getMetaDataText(group = group, artifact = artifact)?.encodeToByteArray() ?: return null
        val d = MD5MessageDigest()
        d.update(data)
        return d.finish()
    }

    suspend fun getMetaDataSha1(group: MavenGroup, artifact: String): ByteArray? {
        val data = getMetaDataText(group = group, artifact = artifact)?.encodeToByteArray() ?: return null
        val d = Sha1MessageDigest()
        d.update(data)
        return d.finish()
    }

    suspend fun getMetaDataText(group: MavenGroup, artifact: String): String? {
        val meta = getMetaData(group = group, artifact) ?: return null
        val sb = StringBuilder()
        sb.appendLine("<?xml version='1.0' encoding='US-ASCII'?>")
            .appendLine("<metadata>")
            .append("<groupId>").append(meta.groupId.asString).appendLine("</groupId>")
            .append("<artifactId>").append(meta.artifactId).appendLine("</artifactId>")
        if (meta.version != null) {
            sb.append("<version>").append(meta.version.asString).appendLine("</version>")
        }
        sb.append("<versioning>")
        if (meta.latest != null) {
            sb.append("<latest>").append(meta.latest.asString).appendLine("</latest>")
        }
        if (meta.release != null) {
            sb.append("<release>").append(meta.release.asString).appendLine("</release>")
        }
        if (meta.versions.isEmpty()) {
            sb.append("<versions/>")
        } else {
            sb.appendLine("<versions>")
            meta.versions.forEach { sb.append("<version>").append(it.asString).appendLine("</version>") }
            sb.appendLine("</versions>")
        }
        sb.append("<lastUpdated>")
            .append(datePattern.toString(meta.lastUpdate, timeZoneOffset = 0))
            .append("</lastUpdated>")
        sb.append("</versioning>")
        sb.append("</metadata>")
        return sb.toString()
    }
}