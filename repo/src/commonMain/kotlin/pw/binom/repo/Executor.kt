package pw.binom.repo

import pw.binom.io.Closeable
import pw.binom.job.FuturePromise
import pw.binom.job.ResumableFuturePromise

interface Executor<T, R> : Closeable {
    fun execute(value: T, promise: ResumableFuturePromise<R>?)
}