package pw.binom.repo

import pw.binom.io.UTF8

fun urlEncode(url: String) =
        url.splitToSequence("/").map { UTF8.urlEncode(it) }.joinToString("/")

fun urlDecode(url: String) =
        url.splitToSequence("/").map { UTF8.urlDecode(it) }.joinToString("/")