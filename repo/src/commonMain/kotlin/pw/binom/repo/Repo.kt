package pw.binom.repo

import pw.binom.UUID
import pw.binom.io.file.File
import pw.binom.io.file.relative
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.severe
import pw.binom.repo.blob.BlobStorageService
import pw.binom.repo.blob.FileBlobStorageService
import pw.binom.repo.repositories.Repository
import pw.binom.repo.repositories.docker.DockerDatabase2
import pw.binom.repo.repositories.docker.DockerHandler
import pw.binom.repo.repositories.maven.MavenRepositoryService
import pw.binom.repo.users.EmbeddedUsersService
import pw.binom.repo.users.LDAPUsersService
import pw.binom.repo.users.UsersService
import pw.binom.strong.EventSystem
import pw.binom.strong.Strong
import pw.binom.strong.inject
import pw.binom.toUUID

class Repo(val strong: Strong) : Strong.InitializingBean {
    private val eventSystem by strong.inject<EventSystem>()
    private val logger = Logger.getLogger("Repo")
    private val commonUsersService by strong.inject<CommonUsersService>()
    private val config by strong.inject<Config>()

    var blobs: Map<UUID, BlobStorageService> = emptyMap()
        private set
    var userService = emptyList<UsersService>()
        private set
    var handlers = emptyMap<String, Repository>()
        private set

    private suspend fun updateConfig(config: Config) {
        logger.info("Applying config")

        var blobs: Map<UUID, BlobStorageService>? = null
        var userService: List<UsersService>? = null
        var handlers: Map<String, Repository>? = null
        try {
            blobs = config.blobStorages.associate {
                val storage = when (it) {
                    is BlobStorage.FileBlobStorage -> FileBlobStorageService.open(
                        root = File(it.root),
                        id = UUID.fromString(it.id),
                        bufferSize = config.copyBufferSize
                    )
                }
                it.id.toUUID() to storage
            }
            userService = config.userManagement.map {
                when (it) {
                    is UserManagementConfig.Embedded -> EmbeddedUsersService(it)
                    is UserManagementConfig.LDAP -> LDAPUsersService(it)
                }
            }

            handlers = config.repositories.associate { repoConfig ->
                val repo = when (repoConfig) {
                    is RepositoryConfig.Docker -> {
                        val bb = blobs.filter { it.key.toString() in repoConfig.blobs }
//                            .map { f ->
//                            blobs.find { it.id.toString() == f }
//                                ?: throw IllegalArgumentException("Can't find repository \"$f\" using in DockerRepository \"${repoConfig.name}\"")
//                        }
                        DockerHandler(
//                    strong = strong,
                            urlPrefix = repoConfig.urlPrefix,
                            data = DockerDatabase2.open(File(config.dataDir).relative(repoConfig.name)),
                            repo = this,
                            blobs = emptyList(),//bb,
//                    path = File(config.dataDir).relative(it.name),
                            allowRewrite = repoConfig.allowRewrite,
                            allowAppend = repoConfig.allowAppend,
                            usersService = commonUsersService,
                        )
                    }
                    is RepositoryConfig.Maven -> {
                        val blobs = blobs.filter { it.key.toString() in repoConfig.blobs }
                        MavenRepositoryService(
                            strong = strong,
                            urlPrefix = repoConfig.urlPrefix,
                            allowRewrite = repoConfig.allowRewrite,
                            allowAppend = repoConfig.allowAppend,
                            path = File(config.dataDir).relative(repoConfig.name),
                            repositoryName = repoConfig.name,
                            blobs = blobs
                        )
                    }
                }
                repoConfig.name to repo
            }
            println("headers: ->${handlers}")
            handlers.forEach {
                it.value.start()
            }
            this.blobs.forEach {
                it.value.asyncClose()
            }
            this.userService.forEach {
                it.asyncClose()
            }
            this.blobs = blobs
            this.userService = userService
            this.handlers = handlers

        } catch (e: Throwable) {
            logger.severe("Error in changes new config.", e)
            blobs?.forEach {
                it.value.asyncClose()
            }
            userService?.forEach {
                it.asyncClose()
            }
        }

    }

    override suspend fun init(strong: Strong) {
        updateConfig(config)
        eventSystem.listen<Config> {
            updateConfig(config)
        }
    }


}