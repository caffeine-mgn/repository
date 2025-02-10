package pw.binom.repo.repositories.maven

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import pw.binom.date.maxOf
import pw.binom.io.AsyncInput
import kotlin.coroutines.coroutineContext

class CombineMavenRepository(
    val list: List<MavenRepository>,
) : MavenRepository {

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

    override suspend fun getMetaData(group: MavenGroup, artifact: String): MetaData? {
        val metaDateList = list.map {
            GlobalScope.async(coroutineContext) {
                it.getMetaData(group = group, artifact = artifact)
            }
        }.awaitAll().filterNotNull()
        if (metaDateList.isEmpty()) {
            return null
        }
        if (metaDateList.size == 1) {
            return metaDateList.first()
        }
        return metaDateList.reduce { acc, metaData ->
            val latest = versionMaxOf(acc.latest, metaData.latest)
            val release = versionMaxOf(acc.release, metaData.release)
            val version = versionMaxOf(acc.version, metaData.version)
            val lastUpdate = maxOf(acc.lastUpdate, metaData.lastUpdate)
            val versions = (acc.versions + metaData.versions).sorted()
            MetaData(
                latest = latest,
                release = release,
                version = version,
                lastUpdate = lastUpdate,
                versions = versions,
                groupId = group,
                artifactId = artifact,
            )
        }
    }

    private fun versionMaxOf(a: MavenVersion?, b: MavenVersion?): MavenVersion? =
        when {
            a == null && b == null -> null
            a != null && b == null -> a
            a == null && b != null -> b
            a != null && b != null -> if (a > b) a else b
            else -> b
        }
}