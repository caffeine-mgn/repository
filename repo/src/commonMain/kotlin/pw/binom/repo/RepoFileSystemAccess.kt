package pw.binom.repo

import pw.binom.io.FileSystemAccess
import pw.binom.io.file.File
import pw.binom.io.file.isExist
import pw.binom.io.file.name
import pw.binom.io.http.BasicAuth

class RepoFileSystemAccess(val config: Config) : FileSystemAccess<BasicAuth?> {
    private fun checkRead(user: BasicAuth?) {
        if (user == null) {
            if (config.allowGuest) {
                return
            } else {
                throw FileSystemAccess.AccessException.UnauthorizedException()
            }
        }
        val u = config.users.find { it.login == user.login && it.password == user.password }
        if (u == null) {
            throw FileSystemAccess.AccessException.UnauthorizedException()
        }
    }

    private fun checkWrite(user: BasicAuth?) {
        if (user == null)
            throw FileSystemAccess.AccessException.UnauthorizedException()

        val u = config.users.find { it.login == user.login && it.password == user.password }
                ?: throw FileSystemAccess.AccessException.UnauthorizedException()

        if (u.readOnly)
            throw FileSystemAccess.AccessException.ForbiddenException()
    }

    override suspend fun getFile(user: BasicAuth?, path: String) {
        checkRead(user)
    }

    override suspend fun deleteFile(user: BasicAuth?, path: String) {
        checkWrite(user)
    }

    override suspend fun filterFileList(user: BasicAuth?, path: String): Boolean {
        checkRead(user)
        return true
    }

    override suspend fun copyFile(user: BasicAuth?, from: String, to: String) {
        checkWrite(user)
    }

    override suspend fun moveFile(user: BasicAuth?, from: String, to: String) {
        checkWrite(user)
    }

    override suspend fun putFile(user: BasicAuth?, path: String) {
        checkWrite(user)
        val file = File(config.root, path)
        if (file.isDirectory)
            throw FileSystemAccess.AccessException.ForbiddenException()

        val fileName = file.name
        if (fileName == "maven-metadata.xml" ||
                fileName != "maven-metadata.xml.md5" ||
                fileName != "maven-metadata.xml.sha1")
            return

        if (!config.allowRewriting && file.isFile) {
            throw FileSystemAccess.AccessException.ForbiddenException()
        }
    }

    override suspend fun mkdir(user: BasicAuth?, path: String) {
        checkWrite(user)
        val file = File(config.root, path)
        if (file.isExist)
            throw FileSystemAccess.AccessException.ForbiddenException()
    }
}