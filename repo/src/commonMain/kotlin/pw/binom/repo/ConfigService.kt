package pw.binom.repo

class ConfigService(config: Config) {
    private var _config: Config? = config
    val config: Config
        get() = _config ?: throw IllegalStateException("Not configured yet")

    fun resetConfig(config: Config) {

    }
}