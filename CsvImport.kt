package com.branchcheck.app

import android.content.Context

/**
 * Imports branches and devices from CSV text.
 * Columns: branch, device, host, port   (port is optional; empty = ping, number = TCP check)
 * A header row is detected and skipped. Comma, semicolon and tab separators are all accepted.
 */
object CsvImport {
    data class Stats(val newBranches: Int, val devicesAdded: Int, val skipped: Int)

    fun import(ctx: Context, text: String): Stats {
        val lines = text.removePrefix("﻿").lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return Stats(0, 0, 0)

        val delim = listOf(',', ';', '\t').maxByOrNull { c -> lines.first().count { it == c } } ?: ','
        var newBranches = 0
        var added = 0
        var skipped = 0

        for ((i, line) in lines.withIndex()) {
            val cols = line.split(delim).map { it.trim().trim('"').trim() }
            if (cols.size < 3) {
                skipped++
                continue
            }
            if (i == 0 && cols[2].equals("host", ignoreCase = true)) continue

            val branchName = cols[0]
            val deviceName = cols[1]
            val host = cols[2]
            val port = cols.getOrNull(3)?.toIntOrNull()?.takeIf { it in 1..65535 }

            if (branchName.isEmpty() || host.isEmpty() || host.contains(' ')) {
                skipped++
                continue
            }

            val branch = Store.find(branchName) ?: Branch(branchName).also {
                Store.branches.add(it)
                newBranches++
            }
            val device = Device(deviceName.ifEmpty { host }, host, port)
            if (branch.devices.any { it.host == device.host && it.port == device.port }) {
                skipped++
                continue
            }
            branch.devices.add(device)
            added++
        }

        Store.save(ctx)
        return Stats(newBranches, added, skipped)
    }
}
