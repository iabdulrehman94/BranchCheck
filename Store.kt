package com.branchcheck.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Holds the branch list (saved to a JSON file) and the latest check results (kept in memory). */
object Store {
    val branches = mutableListOf<Branch>()
    private val results = ConcurrentHashMap<String, CheckResult>()
    private var loaded = false

    private fun file(ctx: Context) = File(ctx.filesDir, "branches.json")
    private fun key(b: Branch, d: Device) = b.name + "|" + d.key

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val f = file(ctx)
        if (!f.exists()) return
        try {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val b = arr.getJSONObject(i)
                val devices = mutableListOf<Device>()
                val da = b.getJSONArray("devices")
                for (j in 0 until da.length()) {
                    val d = da.getJSONObject(j)
                    val port = if (d.has("port") && !d.isNull("port")) d.getInt("port") else null
                    devices.add(Device(d.getString("name"), d.getString("host"), port))
                }
                branches.add(Branch(b.getString("name"), devices))
            }
        } catch (_: Exception) {
            // A corrupt file just means we start with an empty list.
        }
    }

    @Synchronized
    fun save(ctx: Context) {
        val arr = JSONArray()
        for (b in branches) {
            val da = JSONArray()
            for (d in b.devices) {
                val o = JSONObject()
                o.put("name", d.name)
                o.put("host", d.host)
                if (d.port != null) o.put("port", d.port)
                da.put(o)
            }
            val bo = JSONObject()
            bo.put("name", b.name)
            bo.put("devices", da)
            arr.put(bo)
        }
        file(ctx).writeText(arr.toString())
    }

    fun find(name: String): Branch? = branches.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun resultFor(b: Branch, d: Device): CheckResult? = results[key(b, d)]

    fun setResult(b: Branch, d: Device, r: CheckResult) {
        results[key(b, d)] = r
    }

    fun clearResults(b: Branch) {
        for (d in b.devices) results.remove(key(b, d))
    }

    fun deleteBranch(ctx: Context, b: Branch) {
        clearResults(b)
        branches.remove(b)
        save(ctx)
    }

    fun status(b: Branch): BranchStatus {
        var checked = 0
        var online = 0
        for (d in b.devices) {
            val r = results[key(b, d)] ?: continue
            checked++
            if (r.online) online++
        }
        return BranchStatus(b.devices.size, checked, online)
    }
}
