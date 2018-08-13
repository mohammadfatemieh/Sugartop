package io.exoji2e.erbil

import android.content.Intent
import android.os.Bundle
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import java.io.*
import kotlinx.android.synthetic.main.element_bottom_navigation.*


abstract class ErbilActivity : AppCompatActivity() {
    abstract val TAG : String

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(getContentViewId());
        navigation.setOnNavigationItemSelectedListener {item ->
                when(item.itemId) {
                    R.id.action_dashboard -> {
                        startActivity(Intent(this, MainActivity::class.java))
                        true
                    }
                    R.id.action_recent -> {
                        startActivity(Intent(this, ResultActivity::class.java))
                        true
                    }
                    else -> {
                        true
                    }
                }
        }
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.constant_menu, menu)
        return true
    }

    override fun onStart() {
        super.onStart()
        updateNavigationBarState()
    }

    private fun updateNavigationBarState() {
        val actionId = getNavigationMenuItemId()
        selectBottomNavigationBarItem(actionId)
    }

    private fun selectBottomNavigationBarItem(itemId: Int) {
        val item = navigation.menu.findItem(itemId)
        item.isChecked = true
    }

    internal abstract fun getNavigationMenuItemId(): Int
    internal abstract fun getContentViewId(): Int

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        when (item.getItemId()) {
            R.id.share_db -> {
                shareDB()
                return true
            }
            R.id.manual -> {
                launchManual()
                return true
            }
            R.id.share_csv -> {
                shareAsCSV()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    fun shareDB() {
        val path = getDatabasePath(ErbilDataBase.NAME).getAbsolutePath()
        share_file(path)
    }
    fun shareAsCSV() {
        val task = Runnable {
            val v : List<GlucoseEntry> = ErbilDataBase.getInstance(this)!!.glucoseEntryDao().getAll()
            val sb = StringBuilder(v.size*100)
            sb.append(GlucoseEntry.headerString()).append('\n')
            for(entry in v) {
                sb.append(entry.toString()).append('\n')
            }
            val filename = Time.datetime() + "-Erbil.csv"
            Log.d(TAG, filename)
            val file = File(filesDir, filename)
            file.writeText(sb.toString())
            share_file(file.absolutePath)
            file.deleteOnExit()
        }
        DbWorkerThread.getInstance().postTask(task)
    }

    private fun share_file(path: String) {
        val file = File(path)
        try {
            val uri = FileProvider.getUriForFile(
                    this@ErbilActivity,
                    "io.exoji2e.erbil.fileprovider",
                    file)
            val sharingIntent = Intent(Intent.ACTION_SEND)
            sharingIntent.setType("application/octet-stream");
            sharingIntent.putExtra(Intent.EXTRA_STREAM, uri)
            sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            startActivity(Intent.createChooser(sharingIntent, "Share using"))

        } catch (e: IllegalArgumentException) {
            Log.e(TAG,
                    "The selected file can't be shared: $path")
        }

    }
    private fun launchManual() {
        val intent = Intent(this, ManualActivity::class.java)
        startActivity(intent)
    }
}