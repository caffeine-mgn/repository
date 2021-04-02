package pw.binom.repo.users

import pw.binom.repo.UserManagementConfig
import pw.binom.repo.repositories.Repository

class LDAPUsersService(val config: UserManagementConfig.LDAP):UsersService {
    override fun getUser(login: String, password: String): UsersService.User? {
        TODO("Not yet implemented")
    }

    override fun allowAccess(
        repository: Repository,
        user: UsersService.User?,
        operation: UsersService.RepositoryOperationType
    ): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun asyncClose() {
    }
}