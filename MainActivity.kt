package com.branchcheck.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: BranchAdapter
    private lateinit var summary: TextView
    private lateinit var empty: TextView
    private lateinit var search: EditText
    private lateinit var btnAll: Button
    private var job: Job? = null

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFrom(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.load(this)
        setContentView(R.layout.activity_main)

        summary = findViewById(R.id.summary)
        empty = findViewById(R.id.empty)
        search = findViewById(R.id.search)
        btnAll = findViewById(R.id.btnCheckAll)

        adapter = BranchAdapter(
            onClick = { b ->
                startActivity(Intent(this, BranchActivity::class.java).putExtra("branch", b.name))
            },
            onLongClick = { b -> confirmDelete(b) }
        )
        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = refresh()
        })
        btnAll.setOnClickListener { toggleCheckAll() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val q = search.text.toString().trim().lowercase()
        val shown = Store.branches
            .filter { q.isEmpty() || it.name.lowercase().contains(q) }
            .sortedWith { a, b -> naturalCompare(a.name, b.name) }
        adapter.submit(shown)
        empty.visibility = if (Store.branches.isEmpty()) View.VISIBLE else View.GONE

        var checkedBranches = 0
        var problemBranches = 0
        for (b in Store.branches) {
            val s = Store.status(b)
            if (s.checked > 0) checkedBranches++
            if (s.offline > 0) problemBranches++
        }
        summary.text = getString(R.string.main_summary, Store.branches.size, checkedBranches, problemBranches)
    }

    // ---- Check all branches -------------------------------------------------

    private fun toggleCheckAll() {
        if (job?.isActive == true) {
            job?.cancel()
            return
        }
        val all = Store.branches.flatMap { b -> b.devices.map { d -> b to d } }
        if (all.isEmpty()) {
            Toast.makeText(this, R.string.nothing_to_check, Toast.LENGTH_SHORT).show()
            return
        }
        for (b in Store.branches) Store.clearResults(b)
        refresh()
        btnAll.setText(R.string.stop)

        val gate = Semaphore(24)
        var done = 0
        job = lifecycleScope.launch {
            try {
                coroutineScope {
                    all.map { (b, d) ->
                        async {
                            gate.withPermit {
                                Store.setResult(b, d, Checker.check(d))
                            }
                            done++
                            summary.text = getString(R.string.checking_progress, done, all.size)
                            if (done % 5 == 0 || done == all.size) adapter.notifyDataSetChanged()
                        }
                    }.awaitAll()
                }
            } finally {
                btnAll.setText(R.string.check_all)
                refresh()
            }
        }
    }

    // ---- Menu ---------------------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_import -> {
            picker.launch(arrayOf("*/*"))
            true
        }
        R.id.action_add_branch -> {
            addBranch()
            true
        }
        R.id.action_share -> {
            val text = Report.build(Store.branches)
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                    null
                )
            )
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun importFrom(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            val s = CsvImport.import(this, text)
            Toast.makeText(
                this,
                getString(R.string.import_done, s.newBranches, s.devicesAdded, s.skipped),
                Toast.LENGTH_LONG
            ).show()
            refresh()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.import_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun addBranch() {
        val input = EditText(this)
        input.hint = getString(R.string.branch_name)
        AlertDialog.Builder(this)
            .setTitle(R.string.add_branch)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                if (Store.find(name) != null) {
                    Toast.makeText(this, R.string.branch_exists, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Store.branches.add(Branch(name))
                Store.save(this)
                refresh()
                startActivity(Intent(this, BranchActivity::class.java).putExtra("branch", name))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(b: Branch) {
        AlertDialog.Builder(this)
            .setTitle(b.name)
            .setMessage(R.string.delete_branch_q)
            .setPositiveButton(R.string.delete) { _, _ ->
                Store.deleteBranch(this, b)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
