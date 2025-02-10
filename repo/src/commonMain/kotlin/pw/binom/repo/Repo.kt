package pw.binom.repo

import pw.binom.io.file.File
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.severe
import pw.binom.repo.blob.BlobStorageService
import pw.binom.repo.repositories.Repository
import pw.binom.repo.repositories.docker.DockerHandler
import pw.binom.repo.repositories.maven.MavenRepositoryService
import pw.binom.repo.users.EmbeddedUsersService
import pw.binom.repo.users.LDAPUsersService
import pw.binom.repo.users.UsersService
import pw.binom.strong.EventSystem
import pw.binom.strong.Strong
import pw.binom.strong.inject
import pw.binom.strong.listen
import pw.binom.uuid.UUID
import pw.binom.uuid.toUUID

//class Repo : Strong.InitializingBean,Strong.Bean() {
//    private val eventSystem by strong.inject<EventSystem>()
//    private val logger = Logger.getLogger("Repo")
//    private val commonUsersService by strong.inject<CommonUsersService>()
//    private val config by strong.inject<Config>()
//
//    var blobs: Map<UUID, BlobStorageService> = emptyMap()
//        private set
//    var userService = emptyList<UsersService>()
//        private set
//    var handlers = emptyMap<String, Repository>()
//        private set
//
//    private suspend fun updateConfig(config: Config) {
//        logger.info("Applying config")
//
//        var blobs: Map<UUID, BlobStorageService>? = null
//        var userService: List<UsersService>? = null
//        var handlers: Map<String, Repository>? = null
//        try {
//            blobs = config.blobStorages.associate {
//                val storage = when (it) {
//                    is BlobStorage.FileBlobStorage -> FileBlobStorageService.open(
//                        root = File(it.root),
//                        id = UUID.fromString(it.id),
//                        bufferSize = config.copyBufferSize
//                    )
//                }
//                it.id.toUUID() to storage
//            }
//            userService = config.userManagement.map {
//                when (it) {
//                    is UserManagementConfig.Embedded -> EmbeddedUsersService(it)
//                    is UserManagementConfig.LDAP -> LDAPUsersService(it)
//                }
//            }
//
//            handlers = config.repositories.associate { repoConfig ->
//                val repo = when (repoConfig) {
//                    is RepositoryConfig.Docker -> {
//                        val bb = blobs.filter { it.key.toString() in repoConfig.blobs }
////                            .map { f ->
////                            blobs.find { it.id.toString() == f }
////                                ?: throw IllegalArgumentException("Can't find repository \"$f\" using in DockerRepository \"${repoConfig.name}\"")
////                        }
//                        val blobs = blobs.filter { it.key.toString() in repoConfig.blobs }
//                        DockerHandler(
////                    strong = strong,
//                            urlPrefix = repoConfig.urlPrefix,
//                            path = File(config.dataDir).relative(repoConfig.name),
//                            repo = this,
//                            blobs = blobs.map { it.value },//bb,
////                    path = File(config.dataDir).relative(it.name),
//                            allowRewrite = repoConfig.allowRewrite,
//                            allowAppend = repoConfig.allowAppend,
//                            usersService = commonUsersService,
//                        )
//                    }
//                    is RepositoryConfig.Maven -> {
//                        val blobs = blobs.filter { it.key.toString() in repoConfig.blobs }
//                        MavenRepositoryService(
//                            strong = strong,
//                            urlPrefix = repoConfig.urlPrefix,
//                            allowRewrite = repoConfig.allowRewrite,
//                            allowAppend = repoConfig.allowAppend,
//                            path = File(config.dataDir).relative(repoConfig.name),
//                            repositoryName = repoConfig.name,
//                            blobs = blobs
//                        )
//                    }
//                }
//                repoConfig.name to repo
//            }
//            handlers.forEach {
//                it.value.start()
//            }
//            this.blobs.forEach {
//                it.value.asyncClose()
//            }
//            this.userService.forEach {
//                it.asyncClose()
//            }
//            this.blobs = blobs
//            this.userService = userService
//            this.handlers = handlers
//
//        } catch (e: Throwable) {
//            logger.severe("Error in changes new config.", e)
//            blobs?.forEach {
//                it.value.asyncClose()
//            }
//            userService?.forEach {
//                it.asyncClose()
//            }
//        }
//
//    }
//
//    override suspend fun init(strong: Strong) {
//        updateConfig(config)
//        eventSystem.listen<Config> {
//            updateConfig(config)
//        }
//    }
//
//
//}