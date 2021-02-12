package pw.binom.repo.repositories.docker

import pw.binom.async
import pw.binom.atomic.AtomicReference
import pw.binom.concurrency.Worker
import pw.binom.concurrency.asReference
import pw.binom.db.sqlite.SQLiteConnector
import pw.binom.doFreeze
import pw.binom.io.file.File
import pw.binom.io.file.mkdirs
import pw.binom.io.file.parent
import pw.binom.io.use
import pw.binom.network.NetworkHolderElementKey
import pw.binom.network.resume
import pw.binom.repo.repositories.AbstractSQLiteService
import kotlin.coroutines.suspendCoroutine



//class DockerIndexer(file: File): AbstractSQLiteService(file) {
//    suspend fun findImageBySha256(sha256: ByteArray): Pair<String, String>? =
//        executeOnWorker {
//            db!!.prepareStatement("select name, label from images where sha256=? limit 1").use {
//                it.set(0, sha256)
//                val q = it.executeQuery()
//                if (!q.next()) {
//                    null
//                } else {
//                    q.getString(0)!! to q.getString(1)!!
//                }
//            }
//        }
//
//    suspend fun insertImage(name: String, label: String, sha256: ByteArray) {
//        executeOnWorker {
//            db!!.prepareStatement("insert into images (name,label,sha256) values(?,?,?) on conflict do nothing").use {
//                it.set(0, name)
//                it.set(1, label)
//                it.set(2, sha256)
//                it.executeUpdate()
//            }
//        }
//    }
//}