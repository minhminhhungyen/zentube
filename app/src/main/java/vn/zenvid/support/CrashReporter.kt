package vn.zenvid.support

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ghi lại lỗi khi app bị dừng đột ngột – CHỈ lưu trên máy, không tự gửi đi đâu.
 * Lần mở app sau, bạn sẽ thấy nội dung lỗi và tự chọn có chia sẻ cho người phát triển hay không.
 */
object CrashReporter {
    private lateinit var file: File

    fun install(ctx: Context, version: String) {
        file = File(ctx.filesDir, "last_crash.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { file.writeText(report(thread, e, version)) }
            previous?.uncaughtException(thread, e)
        }
    }

    fun report(thread: Thread, e: Throwable, version: String): String {
        val trace = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
        return buildString {
            appendLine("Zenvid $version")
            appendLine("Thời gian: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            appendLine("Thiết bị: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Luồng: ${thread.name}")
            appendLine()
            append(trace.take(12_000))
        }
    }

    /** Nội dung lỗi lần trước (nếu có). */
    fun pending(): String? = if (::file.isInitialized && file.exists()) runCatching { file.readText() }.getOrNull() else null

    fun clear() { if (::file.isInitialized) file.delete() }
}
