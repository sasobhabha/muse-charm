package dev.puck

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Last-crash readback in framework Views only — no Compose — so it renders even when Compose is what's crashing. */
class CrashReportActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val trace = CrashLogApp.full(this)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 48, 48, 48) }
        column.addView(TextView(this).apply {
            text = "Muse Launcher crash report"
            textSize = 18f; setPadding(0, 0, 0, 16)
        })
        val body = TextView(this).apply {
            text = trace ?: "No Java-level crash was saved.\nA native crash (SIGSEGV inside a .so) writes no trace — if you are seeing this empty screen after a crash, that alone is the diagnostic: the fault is in native code (vosk / Chaquopy python / JNA)."
            textSize = 13f; setPadding(0, 0, 0, 24)
        }
        column.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val copy = Button(this); copy.text = "Copy"
        copy.setOnClickListener {
            val cm = getSystemService(ClipboardManager::class.java)
            cm?.setPrimaryClip(ClipData.newPlainText("muse-crash", body.text.toString()))
            copy.isEnabled = false; copy.text = "Copy ok"
        }
        val launch = Button(this).apply {
            text = "Clear report and open the app"
            setOnClickListener {
                runCatching { CrashLogApp.clear(this@CrashReportActivity) }
                startActivity(Intent(this@CrashReportActivity, MainActivity::class.java))
                finish()
            }
        }
        column.addView(copy); column.addView(launch)
        setContentView(column)
    }
}
