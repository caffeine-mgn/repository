package pw.binom.repo.maven.repositories

import pw.binom.asyncInput
import pw.binom.crypto.MD5MessageDigest
import pw.binom.crypto.Sha1MessageDigest
import pw.binom.date.format.toDatePattern
import pw.binom.io.AsyncInput
import pw.binom.io.ByteArrayInput
import pw.binom.io.StringReader
import pw.binom.repo.maven.MavenVersion
import pw.binom.repo.repositories.maven.MavenGroup
import pw.binom.repo.repositories.maven.MavenMetadata
import pw.binom.xml.serialization.Xml

interface MavenRepository {
    companion object {
        private val datePattern = "yyyyMMddHHmmss".toDatePattern()
        const val MAVEN_METADATA_XML = "maven-metadata.xml"
        const val MAVEN_METADATA_XML_MD5 = "maven-metadata.xml.md5"
        const val MAVEN_METADATA_XML_SHA1 = "maven-metadata.xml.sha1"
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

    suspend fun getBlob(
        group: MavenGroup,
        artifact: String,
        version: MavenVersion,
        file: String,
    ): AsyncInput? =
        when (file) {
            MAVEN_METADATA_XML -> getMetaDataText(
                group = group,
                artifact = artifact,
                version = version,
            )?.encodeToByteArray()?.let { ByteArrayInput(it) }?.asyncInput(callClose = true)

            MAVEN_METADATA_XML_MD5 -> getMetaDataMd5(
                group = group,
                artifact = artifact,
                version = version,
            )?.let { ByteArrayInput(it) }?.asyncInput(callClose = true)

            MAVEN_METADATA_XML_SHA1 -> getMetaDataMd5(
                group = group,
                artifact = artifact,
                version = version,
            )?.let { ByteArrayInput(it) }?.asyncInput(callClose = true)

            else -> get(group = group, artifact = artifact, version = version, file = file)
        }

    suspend fun getBlob(
        group: MavenGroup,
        artifact: String,
        file: String,
    ): AsyncInput? =
        when (file) {
            MAVEN_METADATA_XML -> getMetaDataText(
                group = group,
                artifact = artifact,
            )?.encodeToByteArray()?.let { ByteArrayInput(it) }?.asyncInput(callClose = true)

            MAVEN_METADATA_XML_MD5 -> getMetaDataMd5(
                group = group,
                artifact = artifact,
            )?.let { ByteArrayInput(it) }?.asyncInput(callClose = true)

            MAVEN_METADATA_XML_SHA1 -> getMetaDataMd5(
                group = group,
                artifact = artifact,
            )?.let { ByteArrayInput(it) }?.asyncInput(callClose = true)

            else -> null
        }

    val readOnly: Boolean
        get() = true

    suspend fun push(group: MavenGroup, artifact: String, version: MavenVersion, file: String, from: AsyncInput) {
        throw IllegalStateException("Repository not support mutation")
    }

    suspend fun getMetaData(group: MavenGroup, artifact: String, version: MavenVersion): MavenMetadata?
    suspend fun getMetaDataMd5(group: MavenGroup, artifact: String, version: MavenVersion): ByteArray? {
        val data =
            getMetaDataText(group = group, artifact = artifact, version = version)?.encodeToByteArray() ?: return null
        val d = MD5MessageDigest()
        d.update(data)
        return d.finish()
    }

    suspend fun getMetaDataSha1(group: MavenGroup, artifact: String, version: MavenVersion): ByteArray? {
        val data =
            getMetaDataText(group = group, artifact = artifact, version = version)?.encodeToByteArray() ?: return null
        val d = Sha1MessageDigest()
        d.update(data)
        return d.finish()
    }

    suspend fun getMetaDataText(group: MavenGroup, artifact: String, version: MavenVersion): String? =
        getMetaData(group = group, artifact = artifact, version = version)
            ?.let {
                Xml().encodeToString(MavenMetadata.serializer(), it)
            }


    suspend fun getMetaData(group: MavenGroup, artifact: String): MavenMetadata?
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

    suspend fun getMetaDataText(group: MavenGroup, artifact: String): String? =
        getMetaData(group = group, artifact = artifact)
            ?.let {
                Xml().encodeToString(MavenMetadata.serializer(), it)
            }
}