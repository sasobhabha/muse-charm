package dev.puck

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Side-load aid: last uncaught exception goes to disk for a readback without adb. Trace survives relaunch. */
class CrashLogApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val trace = StringWriter(); e.printStackTrace(PrintWriter(trace))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val text = buildString {
                    append(stamp).append(" Android ").append(Build.VERSION.RELEASE)
                        .append(" sdk=").append(Build.VERSION.SDK_INT)
                    append(" abis=").append(Build.SUPPORTED_ABIS.joinToString(","))
                    append(" device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                    append("\n").append(trace.toString())
                }
                save(this, text)
            } catch (_: Throwable) {}
            previous?.uncaughtException(thread, e)
        }
    }
    companion object {
        /** App-private external dir: writable at all API levels without runtime permission. */
        private fun file(context: Context) = File(context.getExternalFilesDir(null) ?: context.filesDir, "muse-crash.txt")
        internal fun save(context: Context, text: String) { runCatching { file(context).writeText(text) } }
        /** First exception-looking line, so a small on-screen banner can show the real cause. */
        internal fun headline(context: Context): String? {
            val text = try { if (file(context).isFile) file(context).readText() else return null } catch (_: Exception) { return null }
            val line = text.lineSequence().firstOrNull { it.startsWith("java.lang.") || it.startsWith("android.app.") || (it.contains("Exception:") || it.contains("Error:")) }
            return line?.take(220)
        }
        internal fun full(context: Context) = try { if (file(context).isFile) file(context).readText().take(8000) else null } catch (_: Exception) { null }
        internal fun clear(context: Context) { runCatching { file(context).delete() } }
    }
}
