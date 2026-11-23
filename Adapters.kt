package com.branchcheck.app

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

object Ui {
    val GREEN = 0xFF2E7D32.toInt()
    val RED = 0xFFC62828.toInt()
    val GRAY = 0xFF9E9E9E.toInt()

    fun setDot(dot: View, color: Int) {
        val bg = dot.background.mutate()
        if (bg is GradientDrawable) bg.setColor(color)
    }
}

class BranchAdapter(
    private val onClick: (Branch) -> Unit,
    private val onLongClick: (Branch) -> Unit
) : RecyclerView.Adapter<BranchAdapter.VH>() {

    private var items: List<Branch> = emptyList()

    fun submit(list: List<Branch>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val dot: View = v.findViewById(R.id.dot)
        val name: TextView = v.findViewById(R.id.name)
        val sub: TextView = v.findViewById(R.id.sub)
        val status: TextView = v.findViewById(R.id.status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_branch, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val b = items[position]
        val s = Store.status(b)
        val ctx = h.itemView.context

        h.name.text = b.name
        h.sub.text = ctx.getString(R.string.n_devices, s.total)

        val color: Int
        when {
            s.total == 0 -> {
                h.status.text = ctx.getString(R.string.no_devices)
                color = Ui.GRAY
            }
            s.checked == 0 -> {
                h.status.text = ctx.getString(R.string.not_checked)
                color = Ui.GRAY
            }
            s.offline > 0 -> {
                h.status.text = ctx.getString(R.string.n_offline, s.offline)
                color = Ui.RED
            }
            s.checked < s.total -> {
                h.status.text = ctx.getString(R.string.n_of_m_online, s.online, s.total)
                color = Ui.GRAY
            }
            else -> {
                h.status.text = ctx.getString(R.string.all_online)
                color = Ui.GREEN
            }
        }
        h.status.setTextColor(color)
        Ui.setDot(h.dot, color)

        h.itemView.setOnClickListener { onClick(b) }
        h.itemView.setOnLongClickListener {
            onLongClick(b)
            true
        }
    }
}

class DeviceAdapter(
    private val branch: Branch,
    private val onLongClick: (Device) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.VH>() {

    /** True while a check is running, so unchecked rows show "Checking...". */
    var checking = false

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val dot: View = v.findViewById(R.id.dot)
        val name: TextView = v.findViewById(R.id.name)
        val target: TextView = v.findViewById(R.id.target)
        val result: TextView = v.findViewById(R.id.result)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false))

    override fun getItemCount(): Int = branch.devices.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val d = branch.devices[position]
        val r = Store.resultFor(branch, d)
        val ctx = h.itemView.context

        h.name.text = d.name
        h.target.text = if (d.port == null) ctx.getString(R.string.ping_target, d.host)
        else ctx.getString(R.string.tcp_target, d.host, d.port)

        val color: Int
        when {
            r == null -> {
                h.result.text = ctx.getString(if (checking) R.string.checking_short else R.string.not_checked)
                color = Ui.GRAY
            }
            r.online -> {
                h.result.text = if (r.ms != null) ctx.getString(R.string.online_ms, r.ms) else ctx.getString(R.string.online)
                color = Ui.GREEN
            }
            else -> {
                h.result.text = r.detail ?: ctx.getString(R.string.offline)
                color = Ui.RED
            }
        }
        h.result.setTextColor(color)
        Ui.setDot(h.dot, color)

        h.itemView.setOnLongClickListener {
            onLongClick(d)
            true
        }
    }
}
