package com.branchcheck.app

/** A thing to check at a branch. If [port] is null it is checked with ping, otherwise with a TCP connect. */
data class Device(val name: String, val host: String, val port: Int? = null) {
    val key: String get() = "$host:${port ?: 0}"
    val target: String get() = if (port == null) host else "$host:$port"
}

data class Branch(val name: String, val devices: MutableList<Device> = mutableListOf())

data class CheckResult(
    val online: Boolean,
    val ms: Long? = null,
    val detail: String? = null,
    val at: Long = System.currentTimeMillis()
)

data class BranchStatus(val total: Int, val checked: Int, val online: Int) {
    val offline: Int get() = checked - online
}

/** Sorts "Branch 2" before "Branch 10". */
fun naturalCompare(a: String, b: String): Int {
    val re = Regex("(\\d+)|(\\D+)")
    val pa = re.findAll(a.lowercase()).map { it.value }.toList()
    val pb = re.findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(pa.size, pb.size)) {
        val x = pa[i]
        val y = pb[i]
        val c = if (x[0].isDigit() && y[0].isDigit()) {
            x.toBigInteger().compareTo(y.toBigInteger())
        } else {
            x.compareTo(y)
        }
        if (c != 0) return c
    }
    return pa.size - pb.size
}
