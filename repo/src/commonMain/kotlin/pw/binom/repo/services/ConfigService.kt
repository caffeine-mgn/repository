package pw.binom.repo.services

import pw.binom.repo.properties.RepoProperties
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.inject
import pw.binom.strong.properties.injectProperty

class ConfigService {
    private val repoProperties by injectProperty<RepoProperties>()
    private val httpServerService by inject<HttpServerService>()
    private val mavenRepositoriesService by inject<MavenRepositoriesService>()

    init {
        BeanLifeCycle.postConstruct {
            repoProperties.repositories.maven
//                .sortedBy { (_, property) ->
//                    when {
//                        property.file != null -> 0
//                        property.http != null -> 1
//                        property.cache != null -> 2
//                        else -> TODO()
//                    }
//                }
                .forEach { (key, repo) ->
                    mavenRepositoriesService.add(
                        id = key,
                        param = repo,
                    )
                }
            repoProperties.endpoints.forEach { (id, endpoint) ->
                httpServerService.add(
                    id = id,
                    endpoint = endpoint,
                )
            }
        }
    }
}