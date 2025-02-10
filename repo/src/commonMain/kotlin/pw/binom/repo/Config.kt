package pw.binom.repo

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Polymorphic
@Serializable
sealed class RepositoryConfig {

    abstract val name: String

    @Polymorphic
    @Serializable
    @SerialName("docker")
    data class Docker(
        override val name: String,
        @SerialName("allow_rewrite")
        val allowRewrite: Boolean,

        @SerialName("allow_append")
        val allowAppend: Boolean,

        @SerialName("url_prefix")
        val urlPrefix: String,

        @SerialName("blobs")
        val blobs: List<String> = emptyList(),
    ) : RepositoryConfig()

    @Polymorphic
    @Serializable
    @SerialName("maven")
    data class Maven(
        override val name: String,

        @SerialName("allow_rewrite")
        val allowRewrite: Boolean,

        @SerialName("allow_append")
        val allowAppend: Boolean,

        @SerialName("url_prefix")
        val urlPrefix: String,

        @SerialName("blobs")
        val blobs: List<String> = emptyList(),
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
    @SerialName("copy_buffer_size")
    val copyBufferSize: Int,
    val repositories: List<RepositoryConfig> = emptyList(),
    @SerialName("user_management")
    val userManagement: List<UserManagementConfig> = emptyList(),
    @SerialName("blob_storages")
    val blobStorages: List<BlobStorage> = emptyList(),
    val bind: List<BindConfig> = emptyList()
)