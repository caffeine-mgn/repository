package pw.binom.repo.controllers

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import pw.binom.flux.Route
import pw.binom.flux.get
import pw.binom.repo.ConfigService
import pw.binom.repo.RepositoryConfig
import pw.binom.strong.Strong
import pw.binom.strong.inject

class RepositoryController(strong: Strong) : Strong.LinkingBean {
    private val flex by strong.inject<Route>()
    private val config by strong.inject<ConfigService>()
    override suspend fun link(strong: Strong) {
        flex.get("/api/v1/repositories") {
            val repos = config.config.repositories.map {
                val type = when (it) {
                    is RepositoryConfig.Maven -> RepositoryDto.Type.MAVEN
                    is RepositoryConfig.Docker -> RepositoryDto.Type.DOCKER
                }
                val blobs = when (it) {
                    is RepositoryConfig.Maven -> it.blobs
                    is RepositoryConfig.Docker -> it.blobs
                }
                RepositoryDto(
                    name = it.name,
                    type = type,
                    blobs = blobs,
                )
            }

            it.writeResponse(ListSerializer(RepositoryDto.serializer()), repos)
        }
    }
}

@Serializable
class RepositoryDto(val name: String, val type: Type, val blobs: List<String>?) {
    @Serializable
    enum class Type {
        MAVEN,
        DOCKER,
    }
}