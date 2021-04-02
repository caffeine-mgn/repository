package pw.binom.repo

import pw.binom.io.httpServer.Handler
import pw.binom.io.httpServer.HttpRequest
import pw.binom.logger.Logger
import pw.binom.logger.warn
import pw.binom.strong.Strong
import pw.binom.strong.inject

class RepositoryHandler(val strong: Strong) : Handler {
    private val repo by strong.inject<Repo>()
    val logger = Logger.getLogger("Repositories")
    override suspend fun request(req: HttpRequest) {
        val repoName = req.path.getVariable("repo-name", "/repositories/{repo-name}/*")
        println("repoName=$repoName")
        val repo = repo.handlers[repoName]
        if (repo == null) {
            logger.warn("Repository \"${repoName}\" not found")
            return
        }
        repo.request(req)
    }
}