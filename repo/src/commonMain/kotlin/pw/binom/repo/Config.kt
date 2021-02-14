package pw.binom.repo

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Polymorphic
@Serializable
sealed class RepositoryConfig {
    @Polymorphic
    @Serializable
    @SerialName("docker")
    data class Docker(
        val name: String,
        val allowRewrite: Boolean,
        val allowAppend: Boolean,
        val urlPrefix: String
    ) : RepositoryConfig()

    @Polymorphic
    @Serializable
    @SerialName("maven")
    data class Maven(
        val name: String,
        val allowRewrite: Boolean,
        val allowAppend: Boolean,
        val urlPrefix: String
    ) : RepositoryConfig()
}

@Polymorphic
@Serializable
sealed class UserManagementConfig {
    @Polymorphic
    @Serializable
    @SerialName("embedded")
    data class Embedded(val users: List<User> = emptyList()) : UserManagementConfig() {
        @Serializable
        data class User(val login: String, val password: String)
    }

    @Polymorphic
    @Serializable
    @SerialName("ldap")
    data class LDAP(
        val login: String,
        val password: String,
        val hostAddr: String,
        val hostPort: Int,
        val searchDn: String,
    ) : UserManagementConfig()
}

@Serializable
class BindConfig(val ip: String, val port: Int)

@Serializable
sealed class BlobStorage {
    @Serializable
    @SerialName("fs")
    data class FileBlobStorage(val root: String, val id: String) : BlobStorage()
}

@Serializable
class Config(
    val dataDir: String,
    val copyBufferSize: Int,
    val repositories: List<RepositoryConfig> = emptyList(),
    val userManagement: List<UserManagementConfig> = emptyList(),
    val blobStorages: List<BlobStorage> = emptyList(),
    val bind: List<BindConfig> = emptyList()
)