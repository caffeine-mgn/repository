package pw.binom.repo

import pw.binom.Date
import pw.binom.atomic.AtomicReference
import kotlin.native.concurrent.SharedImmutable

object Logger {

    fun getLog(pkg: String) = LoggerImpl(pkg)

    interface Level {
        val name: String
        val priority: UInt
    }

    class LoggerImpl(val pkg: String) {
        private val _level = AtomicReference<Level?>(null)
        var level: Level?
            get() = _level.value
            set(value) {
                _level.value = value
            }

        fun log(level: Level, text: String) {
            val currentLevel = this._level.value

            if (currentLevel != null && currentLevel.priority > level.priority)
                return
            val now = Date.now()

            println("${now.year + 1900}/${(now.month + 1).dateNumber()}/${now.dayOfMonth.dateNumber()} ${now.hours.dateNumber()}:${now.min.dateNumber()}:${now.sec.dateNumber()} [${level.name}] [$pkg]: $text")
        }
    }
}

private fun Int.dateNumber() =
        if (this <= 9)
            "0$this"
        else
            this.toString()

@SharedImmutable
private val INFO_LEVEL = object : Logger.Level {
    override val name: String
        get() = "I"
    override val priority: UInt
        get() = 800u
}

val Logger.INFO: Logger.Level
    get() = INFO_LEVEL

fun Logger.LoggerImpl.info(text: String) {
    log(Logger.INFO, text)
}

@SharedImmutable
private val WARN_LEVEL = object : Logger.Level {
    override val name: String
        get() = "W"
    override val priority: UInt
        get() = 900u
}

val Logger.WARNING: Logger.Level
    get() = WARN_LEVEL

fun Logger.LoggerImpl.warn(text: String) {
    log(Logger.WARNING, text)
}

@SharedImmutable
private val SEVERE_LEVEL = object : Logger.Level {
    override val name: String
        get() = "S"
    override val priority: UInt
        get() = 1000u
}

val Logger.SEVERE: Logger.Level
    get() = SEVERE_LEVEL

fun Logger.LoggerImpl.severe(text: String) {
    log(Logger.SEVERE, text)
}

