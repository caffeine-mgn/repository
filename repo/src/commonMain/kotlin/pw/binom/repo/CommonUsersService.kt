package pw.binom.repo

import pw.binom.repo.repositories.Repository
import pw.binom.repo.users.UsersService
import pw.binom.strong.Strong
import pw.binom.strong.inject

//class CommonUsersService: UsersService,Strong.Bean() {
//    private val repo by strong.inject<Repo>()
//    override fun getUser(login: String, password: String): UsersService.User? =
//        repo.userService
//            .asSequence()
//            .mapNotNull { it.getUser(login = login, password = password) }
//            .firstOrNull()
//
//    override fun allowAccess(
//        repository: Repository,
//        user: UsersService.User?,
//        operation: UsersService.RepositoryOperationType
//    ): Boolean {
//        val disallow = repo.userService.any {
//            !it.allowAccess(
//                repository = repository,
//                user = user,
//                operation = operation,
//            )
//        }
//        return !disallow
//    }
//
//    override suspend fun asyncClose() {
//
//    }
//}