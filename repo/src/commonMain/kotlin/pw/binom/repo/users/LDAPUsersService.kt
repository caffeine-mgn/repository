package pw.binom.repo.users

import pw.binom.repo.UserManagementConfig

class LDAPUsersService(val config: UserManagementConfig.LDAP):UsersService {
    override fun getUser(login: String, password: String): UsersService.User? {
        TODO("Not yet implemented")
    }
}