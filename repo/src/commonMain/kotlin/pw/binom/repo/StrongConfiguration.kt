package pw.binom.repo

import pw.binom.UUID
import pw.binom.flux.Route
import pw.binom.flux.get
import pw.binom.io.file.File
import pw.binom.io.file.relative
import pw.binom.repo.repositories.docker.DockerRepositoryService
import pw.binom.repo.blob.FileBlobStorageService
import pw.binom.repo.repositories.maven.MavenRepositoryService
import pw.binom.repo.users.EmbeddedUsersService
import pw.binom.repo.users.LDAPUsersService
import pw.binom.strong.Strong

const val ROOT_ROUTER = "root-router"

object StrongConfiguration {
    fun mainConfiguration(config: Config) = Strong.config { strong ->

        config.blobStorages.forEach {
            val blobService = when (it) {
                is BlobStorage.FileBlobStorage -> FileBlobStorageService(
                    root = File(it.root),
                    id = UUID.fromString(it.id),
                    bufferSize = config.copyBufferSize
                )
            }
            strong.define(blobService)
        }

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
                    path = File(config.dataDir).relative(it.name),
                    allowRewrite = it.allowRewrite,
                    allowAppend = it.allowAppend,
                )
                is RepositoryConfig.Maven -> MavenRepositoryService(
                    strong = strong,
                    urlPrefix = it.urlPrefix,
                    allowRewrite = it.allowRewrite,
                    allowAppend = it.allowAppend,
                    path = File(config.dataDir).relative(it.name),
                )
            }
            strong.define(repo)
        }

        strong.initializing {
            val b by strong.service<Route>(ROOT_ROUTER)
            b.get("*") {
                println("${it.req.method} ${it.req.uri} ${it.req.headers.keys}")
                false
            }
        }
    }
}