package pw.binom.repo

//class WebDavHandler(
//    val prefix: String,
//    val fs: FileSystem,
//    override val bufferPool: ObjectPool<ByteBuffer>
//) : AbstractWebDavHandler<BasicAuth?>() {
//    override fun getFS(req: HttpRequest): FileSystem = fs
//
//    override fun getGlobalURI(req: HttpRequest): URN = prefix.toURN
//
//    override fun getLocalURI(req: HttpRequest, globalURI: URN): String =
//        globalURI.raw.removePrefix(prefix)
//
//    override fun getUser(req: HttpRequest): BasicAuth? =
//        req.headers.basicAuth
//
//    override fun getFS(req: HttpRequest, resp: HttpResponse): FileSystem = fs
//
//    override fun getGlobalURI(req: HttpRequest): String {
//        return prefix
//    }
//
//    override fun getLocalURI(req: HttpRequest, globalURI: String): String = globalURI.removePrefix(prefix)
//
//    override fun getUser(req: HttpRequest, resp: HttpResponse) = req.basicAuth
//}