package me.grey.picquery.common

import android.util.Log
import me.grey.picquery.PicQueryApplication
import org.json.JSONObject
import xcrash.ICrashCallback
import xcrash.TombstoneManager
import xcrash.TombstoneParser
import xcrash.XCrash
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG: String = "CrashHandler"

fun PicQueryApplication.initCrashCallback() {
    // callback for java crash, native crash and ANR
    val callback = ICrashCallback { logPath, emergency ->
        Log.d(
            TAG, "log path: ${logPath ?: "(null)"}, " +
                    "emergency: ${(emergency ?: "(null)")}"
        )
        if (emergency != null) {
            debug(logPath, emergency)
        } else {
            // Add some expanded sections. Send crash report at the next time APP startup.
            TombstoneManager.appendSection(logPath, "expanded_key_1", "expanded_content")
            TombstoneManager.appendSection(
                logPath,
                "expanded_key_2",
                "expanded_content_row_1\nexpanded_content_row_2"
            )
            debug(logPath, null)
        }
    }

    Log.d(TAG, "xCrash SDK init: start")
    // Initialize xCrash.
    val versionCode = packageManager.getPackageInfo(packageName, 0).longVersionCode
    XCrash.init(
        this, XCrash.InitParameters()
            .setAppVersion(versionCode.toString())
            .setJavaRethrow(true)
            .setJavaLogCountMax(10)
            .setJavaDumpAllThreadsWhiteList(
                arrayOf(
                    "^main$",
                    "^Binder:.*",
                    ".*Finalizer.*"
                )
            )
            .setJavaDumpAllThreadsCountMax(10)
            .setJavaCallback(callback)
            .setNativeRethrow(true)
            .setNativeLogCountMax(10)
            .setNativeDumpAllThreadsWhiteList(
                arrayOf(
                    "^xcrash\\.sample$",
                    "^Signal Catcher$",
                    "^Jit thread pool$",
                    ".*(R|r)ender.*",
                    ".*Chrome.*"
                )
            )
            .setNativeDumpAllThreadsCountMax(10)
            .setNativeCallback(callback) //          .setAnrCheckProcessState(false)
            .setAnrRethrow(true)
            .setAnrLogCountMax(10)
            .setAnrCallback(callback)
            .setPlaceholderCountMax(3)
            .setPlaceholderSizeKb(512)
            .setLogDir(getExternalFilesDir("xcrash").toString())
            .setLogFileMaintainDelayMs(1000)
    )

    Log.d(TAG, "xCrash SDK init: end")
}


private fun debug(logPath: String, emergency: String?) {
    // Parse and save the crash info to a JSON file for debugging.
    var writer: FileWriter? = null
    try {
        val today = Date()
        val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(today)
        val debug = File(XCrash.getLogDir(), "debug-${formatted}.json")
        debug.createNewFile()

        writer = FileWriter(debug, false)
        writer.write((TombstoneParser.parse(logPath, emergency) as Map<*, *>?)?.let {
            JSONObject(it).toString()
        })
    } catch (e: Exception) {
        Log.d(TAG, "Saving debug log failed!", e)
    } finally {
        if (writer != null) {
            try {
                writer.close()
            } catch (ignored: Exception) {
            }
        }
    }
}