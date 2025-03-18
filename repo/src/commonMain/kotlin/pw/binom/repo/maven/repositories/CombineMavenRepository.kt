package pw.binom.repo.repositories.maven

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import pw.binom.date.maxOf
import pw.binom.io.AsyncInput
import kotlin.coroutines.coroutineContext

data class CombineMavenRepository(
    val list: List<MavenRepository>,
) : MavenRepository {
    override fun toString(): String =
        "CombineMavenRepository(list=$list)"

    override suspend fun get(group: MavenGroup, artifact: String, version: MavenVersion, file: String): AsyncInput? =
        list
            .asFlow()
            .map {
                it.get(
                    group = group,
                    artifact = artifact,
                    version = version,
                    file = file,
                )
            }
            .filterNotNull()
            .firstOrNull()

    override suspend fun isExist(group: MavenGroup, artifact: String, version: MavenVersion, file: String): Long? =
        list
            .asFlow()
            .mapNotNull { it.isExist(group = group, artifact = artifact, version = version, file = file) }
            .firstOrNull()

    override suspend fun getMetaData(group: MavenGroup, artifact: String): MavenMetadata? {
        val metaDateList = list.map {
            GlobalScope.async(coroutineContext) {
                it.getMetaData(group = group, artifact = artifact)
            }
        }.awaitAll().filterNotNull()
        if (metaDateList.isEmpty()) {
            return null
        }
        return MavenMetadata.combine(metaDateList)
    }

    override suspend fun getMetaData(group: MavenGroup, artifact: String, version: MavenVersion): MavenMetadata? {
        val metaDateList = list.map {
            GlobalScope.async(coroutineContext) {
                it.getMetaData(group = group, artifact = artifact, version = version)
            }
        }.awaitAll().filterNotNull()
        if (metaDateList.isEmpty()) {
            return null
        }
        return MavenMetadata.combine(metaDateList)
    }
}