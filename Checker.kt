package com.branchcheck.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

object Checker {

    /** Ping when the device has no port, TCP connect when it has one. */
    suspend fun check(d: Device, timeoutMs: Int = 2000): CheckResult = withContext(Dispatchers.IO) {
        val port = d.port
        if (port != null) tcp(d.host, port, timeoutMs) else ping(d.host, timeoutMs)
    }

    private fun tcp(host: String, port: Int, timeoutMs: Int): CheckResult {
        val start = System.nanoTime()
        return try {
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
            CheckResult(true, (System.nanoTime() - start) / 1_000_000)
        } catch (e: Exception) {
            CheckResult(false, null, describe(e))
        }
    }

    private fun ping(host: String, timeoutMs: Int): CheckResult {
        val sec = maxOf(1, timeoutMs / 1000)
        val start = System.nanoTime()
        try {
            // Android has no raw ICMP access for apps, so use the system ping binary.
            val p = ProcessBuilder(
                "/system/bin/ping", "-c", "1", "-W", sec.toString(), "-w", (sec + 1).toString(), host
            ).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            if (!p.waitFor((sec + 3).toLong(), TimeUnit.SECONDS)) {
                p.destroyForcibly()
                return CheckResult(false, null, "Timed out")
            }
            when (p.exitValue()) {
                0 -> {
                    val ms = Regex("time=([0-9.]+)").find(out)?.groupValues?.get(1)?.toDoubleOrNull()?.toLong()
                        ?: ((System.nanoTime() - start) / 1_000_000)
                    return CheckResult(true, ms)
                }
                1 -> return CheckResult(false, null, "No reply")
                // Any other exit code means ping itself could not run (bad host name, etc.): try the fallback.
            }
        } catch (_: Exception) {
            // ping binary missing or blocked: try the fallback.
        }
        return try {
            val ok = InetAddress.getByName(host).isReachable(timeoutMs)
            if (ok) CheckResult(true, (System.nanoTime() - start) / 1_000_000)
            else CheckResult(false, null, "No reply")
        } catch (e: Exception) {
            CheckResult(false, null, describe(e))
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "Unknown host"
        is java.net.SocketTimeoutException -> "Timed out"
        is java.net.ConnectException -> "Refused / unreachable"
        else -> e.javaClass.simpleName
    }
}
