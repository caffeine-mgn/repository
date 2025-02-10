package pw.binom.repo.services

import pw.binom.io.file.File
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.HttpClientRunnable
import pw.binom.logger.Logger
import pw.binom.logger.infoSync
import pw.binom.repo.properties.MavenRepositoryProperty
import pw.binom.repo.repositories.maven.*
import pw.binom.strong.inject

class MavenRepositoriesService {
    private val httpClient by inject<HttpClientRunnable>()
    private val map = HashMap<String, MavenRepository>()
    private val logger by Logger.ofThisOrGlobal
    fun getById(id: String) = map[id]
    fun removeBy(id: String) = map.remove(id)

    fun add(id: String, param: MavenRepositoryProperty) {
        val repo = when {
            param.file != null -> {
                val root = File(param.file.root)
                logger.infoSync("Creating file repository with root ${param.file.root}")
                if (root.isFile) {
                    throw IllegalStateException("Can't create repository $id: \"$root\" is file")
                }
                root.mkdirs()
                FileSystemMavenRepository(
                    root = root,
                    readOnly = param.file.readOnly,
                    id = id,
                )
            }

            param.http != null -> {
                logger.infoSync("Creating http repository with http \"${param.http.url}\"")
                ExternalMavenRepository(
                    client = httpClient,
                    url = param.http.url,
                    auth = param.http.auth,
                    getTimeout = param.http.getTimeout,
                    readOnly = param.http.readOnly,
                    id = id,
                )
            }

            param.cache != null -> {
                val source = map[param.cache.source]
                    ?: throw IllegalStateException("Creating cache repository \"$id\". Source \"${param.cache.source}\" not found")
                val cacheStorage = map[param.cache.storage]
                    ?: throw IllegalStateException("Creating cache repository \"$id\". Cache Storage \"${param.cache.source}\" not found")
                if (cacheStorage.readOnly) {
                    throw IllegalStateException("Creating cache repository \"$id\". Cache Storage \"${param.cache.source}\" is not mutable")
                }
                logger.infoSync("Creating cache repository. Source: \"${param.cache.source}\". Cache Storage: \"${param.cache.storage}\"")
                CacheMavenRepository(
                    source = source,
                    cache = cacheStorage,
                    readOnly = param.cache.readOnly,
                    id = id,
                    copingTimeout = param.cache.copingTimeout,
                )
            }

            param.combine != null -> {
                val repos = param.combine.readFrom.map {
                    map[it] ?: throw IllegalStateException("Repository \"$it\" not found")
                }
                CombineMavenRepository(list = repos)
            }

            else -> TODO()
        }
        map[id] = repo
    }
}