package pw.binom.repo.users

import pw.binom.io.Sha256MessageDigest
import pw.binom.io.use
import pw.binom.repo.UserManagementConfig
import pw.binom.repo.repositories.Repository
import pw.binom.repo.toHex
import pw.binom.wrap

class EmbeddedUsersService(val config: UserManagementConfig.Embedded) : UsersService {
    override fun getUser(login: String, password: String): UsersService.User? {
        val u = config.users.find { it.login == login } ?: return null


        val sha = Sha256MessageDigest()
        password.encodeToByteArray().wrap().use { pass ->
            sha.update(pass)
        }
        if (sha.finish().toHex() == u.password) {
            return UsersService.User(
                login = u.login
            )
        }
        return null
    }

    override fun allowAccess(
        repository: Repository,
        user: UsersService.User?,
        operation: UsersService.RepositoryOperationType
    ): Boolean = true

    override suspend fun asyncClose() {
    }

}