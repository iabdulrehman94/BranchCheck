package com.branchcheck.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class BranchActivity : AppCompatActivity() {

    private lateinit var branch: Branch
    private lateinit var adapter: DeviceAdapter
    private lateinit var summary: TextView
    private lateinit var btnCheck: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.load(this)
        val found = intent.getStringExtra("branch")?.let { Store.find(it) }
        if (found == null) {
            finish()
            return
        }
        branch = found

        setContentView(R.layout.activity_branch)
        title = branch.name
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        summary = findViewById(R.id.summary)
        btnCheck = findViewById(R.id.btnCheck)

        adapter = DeviceAdapter(branch) { d -> confirmDelete(d) }
        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        btnCheck.setOnClickListener { runCheck() }
        findViewById<Button>(R.id.btnAdd).setOnClickListener { addDevice() }

        // Checking starts automatically: you open the branch because you are standing there.
        runCheck()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun runCheck() {
        if (branch.devices.isEmpty()) {
            updateSummary()
            return
        }
        Store.clearResults(branch)
        adapter.checking = true
        adapter.notifyDataSetChanged()
        btnCheck.isEnabled = false
        summary.setText(R.string.checking)

        lifecycleScope.launch {
            try {
                coroutineScope {
                    branch.devices.toList().map { d ->
                        async {
                            Store.setResult(branch, d, Checker.check(d))
                            adapter.notifyDataSetChanged()
                            updateSummary()
                        }
                    }.awaitAll()
                }
            } finally {
                adapter.checking = false
                adapter.notifyDataSetChanged()
                btnCheck.isEnabled = true
                updateSummary()
            }
        }
    }

    private fun updateSummary() {
        val s = Store.status(branch)
        summary.text = when {
            s.total == 0 -> getString(R.string.no_devices_hint)
            s.checked == 0 -> getString(R.string.not_checked)
            else -> getString(R.string.branch_summary, s.online, s.total, s.offline)
        }
    }

    private fun addDevice() {
        val v = layoutInflater.inflate(R.layout.dialog_device, null)
        val nameIn = v.findViewById<EditText>(R.id.devName)
        val hostIn = v.findViewById<EditText>(R.id.devHost)
        val portIn = v.findViewById<EditText>(R.id.devPort)
        AlertDialog.Builder(this)
            .setTitle(R.string.add_device)
            .setView(v)
            .setPositiveButton(R.string.save) { _, _ ->
                val host = hostIn.text.toString().trim()
                if (host.isEmpty() || host.contains(' ')) return@setPositiveButton
                val port = portIn.text.toString().trim().toIntOrNull()?.takeIf { it in 1..65535 }
                val name = nameIn.text.toString().trim().ifEmpty { host }
                branch.devices.add(Device(name, host, port))
                Store.save(this)
                adapter.notifyDataSetChanged()
                updateSummary()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(d: Device) {
        AlertDialog.Builder(this)
            .setTitle(d.name)
            .setMessage(R.string.delete_device_q)
            .setPositiveButton(R.string.delete) { _, _ ->
                branch.devices.remove(d)
                Store.save(this)
                adapter.notifyDataSetChanged()
                updateSummary()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.branch_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_share) {
            val text = Report.build(listOf(branch), includeOnline = true)
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                    null
                )
            )
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
