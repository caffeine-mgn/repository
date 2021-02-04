package pw.binom.repo

import pw.binom.repo.users.UsersService
import pw.binom.strong.Strong

class ConfigService(val strong: Strong) {
    private val usersServices by strong.serviceList<UsersService>()

    fun getUser(login: String, password: String): UsersService.User? =
        usersServices.asSequence().mapNotNull {
            it.value.getUser(login = login, password = password)
        }.firstOrNull()
}