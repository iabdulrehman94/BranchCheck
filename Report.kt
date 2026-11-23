package com.branchcheck.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Report {

    /** Plain-text report, ready to paste into a ticket or chat. */
    fun build(branches: List<Branch>, includeOnline: Boolean = false): String {
        val sb = StringBuilder()
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        sb.append("Branch check report - ").append(ts).append("\n\n")

        var any = false
        for (b in branches.sortedWith { x, y -> naturalCompare(x.name, y.name) }) {
            val s = Store.status(b)
            if (s.checked == 0) continue
            any = true
            sb.append(b.name).append(": ").append(s.online).append("/").append(s.total).append(" online\n")
            for (d in b.devices) {
                val r = Store.resultFor(b, d) ?: continue
                if (r.online) {
                    if (includeOnline) {
                        sb.append("  OK       ").append(d.name).append(" (").append(d.target).append(")")
                        if (r.ms != null) sb.append(" ").append(r.ms).append(" ms")
                        sb.append("\n")
                    }
                } else {
                    sb.append("  OFFLINE  ").append(d.name).append(" (").append(d.target).append(")")
                    if (r.detail != null) sb.append(" - ").append(r.detail)
                    sb.append("\n")
                }
            }
        }
        if (!any) sb.append("Nothing has been checked yet.\n")
        return sb.toString()
    }
}
