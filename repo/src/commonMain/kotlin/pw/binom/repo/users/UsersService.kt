package pw.binom.repo.users

import pw.binom.io.AsyncCloseable
import pw.binom.repo.repositories.Repository

interface UsersService : AsyncCloseable {

    enum class RepositoryOperationType {
        READ, WRITE, REWRITE
    }

    fun getUser(login: String, password: String): User?
    fun allowAccess(repository: Repository, user: User?, operation: RepositoryOperationType): Boolean

    class User(val login: String)
}