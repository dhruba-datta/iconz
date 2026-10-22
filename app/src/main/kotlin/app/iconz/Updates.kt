package app.iconz

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ProgressBar
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import kotlin.concurrent.thread

/**
 * In-app updates, the same way as Wallet: a public GitHub repo holds only releases
 * (iconz.apk + latest.json each). The app reads latest.json through GitHub's ordinary
 * download link (not its API, which allows only 60 requests an hour per connection),
 * downloads the APK into its cache and hands it to Android's installer. Android only
 * installs it over this app if it is signed with the same key.
 */
object Updates {
    class Release(val version: String, val notes: String, val apkUrl: String, val size: Long)

    private const val PREFS = "updates"
    private const val LAST_CHECK = "last_check"
    private const val QUIET_EVERY_MS = 12 * 60 * 60 * 1000L // a quiet check twice a day

    private val repo get() = BuildConfig.RELEASES_REPO
    private val main = Handler(Looper.getMainLooper())
    private var pendingInstall: File? = null

    /** "v1.2.3" / "1.2.3+4" → [1, 2, 3]. Unparsable parts count as 0. */
    fun parseVersion(v: String) = v.removePrefix("v").substringBefore('+').split('.').map { it.toIntOrNull() ?: 0 }

    fun isNewer(candidate: String, current: String): Boolean {
        val a = parseVersion(candidate)
        val b = parseVersion(current)
        for (i in 0 until 3) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Menu button: always answers, even when there is nothing new. */
    fun check(activity: Activity) {
        if (repo.isEmpty()) {
            activity.dialog().setTitle("Updates aren't set up")
                .setMessage("This build has no release channel. Build with -Piconz.releasesRepo=owner/name (see Claude/RELEASE.md).")
                .setPositiveButton("OK", null).show()
            return
        }
        activity.toast("Checking for updates…")
        thread {
            val result = runCatching { newest() }
            main.post {
                if (activity.isGone) return@post
                result.onSuccess { r ->
                    if (r == null) activity.toast("Iconz ${BuildConfig.VERSION_NAME} is the latest version")
                    else offer(activity, r)
                }.onFailure {
                    activity.dialog().setTitle("Couldn't check for updates")
                        .setMessage(it.message ?: it.toString()).setPositiveButton("OK", null).show()
                }
            }
        }
    }

    /** On opening the app, at most twice a day; silent unless there is an update. */
    fun checkQuietly(activity: Activity) {
        if (repo.isEmpty()) return
        val prefs = activity.getSharedPreferences(PREFS, 0)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(LAST_CHECK, 0) < QUIET_EVERY_MS) return
        prefs.edit().putLong(LAST_CHECK, now).apply()
        thread {
            val r = runCatching { newest() }.getOrNull() ?: return@thread
            main.post { if (!activity.isGone) offer(activity, r) }
        }
    }

    /** After the user allowed "install unknown apps" in Settings and came back. */
    fun resumePendingInstall(activity: Activity) {
        val file = pendingInstall ?: return
        if (canInstall(activity)) {
            pendingInstall = null
            install(activity, file)
        }
    }

    private fun newest(): Release? {
        val r = fromManifest() ?: fromRedirect()
        return if (isNewer(r.version, BuildConfig.VERSION_NAME)) r else null
    }

    private fun apkUrl(version: String) = "https://github.com/$repo/releases/download/v$version/iconz.apk"

    private fun fromManifest(): Release? {
        val c = connect("https://github.com/$repo/releases/latest/download/latest.json")
        try {
            if (c.responseCode == 404) return null // a release without one
            if (c.responseCode != 200) throw IOException("GitHub answered ${c.responseCode}")
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val version = json.getString("version").removePrefix("v")
            return Release(version, json.optString("notes").trim(), apkUrl(version), json.optLong("size"))
        } finally {
            c.disconnect()
        }
    }

    private fun fromRedirect(): Release {
        val c = connect("https://github.com/$repo/releases/latest", method = "HEAD", follow = false)
        try {
            val to = c.getHeaderField("Location").orEmpty()
            val tag = Regex("/releases/tag/([^/?#]+)").find(to)?.groupValues?.get(1)
                ?: throw IOException("GitHub answered ${c.responseCode}")
            val version = URLDecoder.decode(tag, "UTF-8").removePrefix("v")
            return Release(version, "", apkUrl(version), 0)
        } finally {
            c.disconnect()
        }
    }

    private fun connect(url: String, method: String = "GET", follow: Boolean = true) =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = follow
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Iconz/${BuildConfig.VERSION_NAME}")
        }

    private fun offer(activity: Activity, r: Release) {
        val size = if (r.size > 0) " (%.1f MB)".format(r.size / 1_048_576.0) else ""
        activity.dialog().setTitle("Iconz ${r.version} is available")
            .setMessage(r.notes.ifEmpty { "A new version is ready to install." } + "\n\nYou have ${BuildConfig.VERSION_NAME}.$size")
            .setPositiveButton("Update") { _, _ -> download(activity, r) }
            .setNegativeButton("Later", null).show()
    }

    private fun download(activity: Activity, r: Release) {
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000 }
        val box = LinearLayout(activity).apply {
            setPadding(activity.dp(24), activity.dp(16), activity.dp(24), 0)
            addView(bar, LinearLayout.LayoutParams(-1, -2))
        }
        var cancelled = false
        val progress = activity.dialog().setTitle("Downloading Iconz ${r.version}").setView(box)
            .setCancelable(false).setNegativeButton("Cancel") { _, _ -> cancelled = true }.show()

        thread {
            val result = runCatching {
                val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "iconz-${r.version}.apk")
                val c = connect(r.apkUrl)
                try {
                    if (c.responseCode != 200) throw IOException("download failed (${c.responseCode})")
                    val total = if (r.size > 0) r.size else c.contentLengthLong
                    var received = 0L
                    c.inputStream.use { input ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (!cancelled) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                received += n
                                if (total > 0) main.post { bar.progress = (received * 1000 / total).toInt() }
                            }
                        }
                    }
                    if (cancelled) throw InterruptedException()
                    if (total > 0 && received != total) throw IOException("the download was cut off")
                } finally {
                    c.disconnect()
                }
                file
            }
            main.post {
                progress.dismiss()
                if (activity.isGone) return@post
                result.onSuccess { install(activity, it) }.onFailure {
                    if (it !is InterruptedException) {
                        activity.dialog().setTitle("Update failed")
                            .setMessage(it.message ?: it.toString()).setPositiveButton("OK", null).show()
                    }
                }
            }
        }
    }

    private fun canInstall(activity: Activity) =
        Build.VERSION.SDK_INT < 26 || activity.packageManager.canRequestPackageInstalls()

    private fun install(activity: Activity, apk: File) {
        if (!canInstall(activity)) {
            pendingInstall = apk
            activity.dialog().setTitle("Allow Iconz to install its update")
                .setMessage("Android asks once: turn on \"Allow from this source\" for Iconz, then come back.")
                .setPositiveButton("Open settings") { _, _ ->
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")),
                    )
                }.setNegativeButton("Cancel") { _, _ -> pendingInstall = null }.show()
            return
        }
        activity.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(UpdateProvider.uriFor(activity, apk), UpdateProvider.APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
