package pw.binom.repo

class ConfigService {
    private var _config: Config? = null
    val config: Config
        get() = _config ?: throw IllegalStateException("Not configured yet")

    fun resetConfig(config: Config) {

    }
}