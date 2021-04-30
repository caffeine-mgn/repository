package pw.binom.repo.repositories

import pw.binom.concurrency.ThreadRef
import pw.binom.concurrency.Worker
import pw.binom.db.sqlite.SQLiteConnector
import pw.binom.io.Closeable
import pw.binom.io.file.File
import pw.binom.io.file.mkdirs
import pw.binom.io.file.parent

//abstract class AbstractSQLiteService(file: File) : Closeable {
//    protected val worker = Worker()
//
//    init {
//        file.parent?.mkdirs()
//    }
//
//    protected var connection = SQLiteConnector.openFile(file)
//    private val threadRef = ThreadRef()
//
//    init {
////        doFreeze()
//    }
//
//    override fun close() {
//        connection.close()
//    }
//
//    protected suspend fun <T> executeOnWorker(func: suspend () -> T): T =
//        execute(worker, func)
//}