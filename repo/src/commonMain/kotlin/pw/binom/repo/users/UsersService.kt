package pw.binom.repo.users

interface UsersService {
    fun getUser(login: String, password: String): User?

    class User(val login: String)
}