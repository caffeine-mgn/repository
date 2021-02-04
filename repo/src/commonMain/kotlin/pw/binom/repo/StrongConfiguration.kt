package pw.binom.repo

import pw.binom.flux.Route
import pw.binom.flux.get
import pw.binom.io.file.File
import pw.binom.repo.repositories.DockerRepositoryService
import pw.binom.repo.users.EmbeddedUsersService
import pw.binom.repo.users.LDAPUsersService
import pw.binom.strong.Strong

object StrongConfiguration {
    fun mainConfiguration(config: ConfigObj) = Strong.config { strong ->
        config.userManagement.forEach {
            val usersService = when (it) {
                is UserManagementConfig.Embedded -> EmbeddedUsersService(it)
                is UserManagementConfig.LDAP -> LDAPUsersService(it)
            }
            strong.define(usersService)
        }

        config.repositories.forEach {
            val repo: Any = when (it) {
                is RepositoryConfig.Docker -> DockerRepositoryService(
                    strong = strong,
                    urlPrefix = it.urlPrefix,
                    path = File(it.path)
                )
                is RepositoryConfig.Maven -> MavenRepositoryService()
            }
            strong.define(repo)
        }

        strong.initializing {
            val b by strong.service<Route>()
            b.get("*") {
                println("${it.req.method} ${it.req.uri}")
                false
            }
        }
    }
}