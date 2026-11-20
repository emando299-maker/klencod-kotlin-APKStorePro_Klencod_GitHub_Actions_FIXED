package com.apkstore.pro

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class RepoClient(private val context: Context) {
    private val indexUrl = "https://f-droid.org/repo/index-v1.json"
    private val baseUrl = "https://f-droid.org/repo/"
    private val cacheFile get() = File(context.cacheDir, "fdroid-index-v1.json")

    fun loadCatalog(force: Boolean = false): List<StoreApp> {
        val jsonText = if (!force && cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < 6 * 60 * 60 * 1000L) {
            cacheFile.readText()
        } else {
            val text = requestText(indexUrl)
            cacheFile.writeText(text)
            text
        }
        return parseIndex(JSONObject(jsonText))
    }

    private fun requestText(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000; readTimeout = 45000
            setRequestProperty("User-Agent", "APKStorePro/1.0 (Android)")
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally { connection.disconnect() }
    }

    private fun parseIndex(root: JSONObject): List<StoreApp> {
        val apps = root.optJSONArray("apps") ?: return emptyList()
        val packages = root.optJSONObject("packages") ?: JSONObject()
        val result = ArrayList<StoreApp>(apps.length())
        for (i in 0 until apps.length()) {
            val app = apps.optJSONObject(i) ?: continue
            val id = app.optString("packageName")
            if (id.isBlank()) continue
            val versions = packages.optJSONArray(id) ?: continue
            var latest: JSONObject? = null
            for (j in 0 until versions.length()) {
                val candidate = versions.optJSONObject(j) ?: continue
                if (latest == null || candidate.optLong("versionCode") > latest!!.optLong("versionCode")) latest = candidate
            }
            val pkg = latest ?: continue
            val localized = app.optJSONObject("localized")?.optJSONObject("en-US")
                ?: app.optJSONObject("localized")?.optJSONObject("en")
            val name = localized?.optString("name")?.takeIf { it.isNotBlank() }
                ?: app.optString("name", id)
            val summary = localized?.optString("summary")?.takeIf { it.isNotBlank() }
                ?: app.optString("summary", "Open-source Android application")
            val description = localized?.optString("description")?.takeIf { it.isNotBlank() }
                ?: app.optString("description", summary)
            val cats = mutableListOf<String>()
            val catArray = app.optJSONArray("categories")
            if (catArray != null) for (k in 0 until catArray.length()) cats.add(catArray.optString(k))
            result.add(StoreApp(
                id=id, name=name, summary=summary, description=description,
                icon=localized?.optString("icon")?.takeIf { it.isNotBlank() } ?: app.optString("icon"),
                categories=cats, versionName=pkg.optString("versionName", ""),
                versionCode=pkg.optLong("versionCode"), apkName=pkg.optString("apkName"),
                hash=pkg.optString("hash"), hashType=pkg.optString("hashType", "sha256"),
                size=pkg.optLong("size"), minSdk=pkg.optInt("minSdkVersion", 1)
            ))
        }
        return result.sortedBy { it.name.lowercase() }
    }

    fun downloadAndVerify(app: StoreApp, destination: File, progress: (Long, Long) -> Unit) {
        require(app.apkName.matches(Regex("[A-Za-z0-9._+-]+\\.apk"))) { "اسم ملف APK غير صالح" }
        val connection = (URL(baseUrl + app.apkName).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000; readTimeout = 60000
            setRequestProperty("User-Agent", "APKStorePro/1.0 (Android)")
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("فشل التنزيل: HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: app.size
            val digest = MessageDigest.getInstance("SHA-256")
            destination.parentFile?.mkdirs()
            connection.inputStream.use { input -> destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(32 * 1024); var count = 0L
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    digest.update(buffer, 0, n); output.write(buffer, 0, n); count += n
                    progress(count, total)
                }
            } }
            if (!app.hashType.equals("sha256", true) || app.hash.isBlank()) {
                destination.delete()
                throw SecurityException("نوع بصمة الملف غير مدعوم؛ تم حذف الملف")
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(app.hash, true)) { destination.delete(); throw SecurityException("فشل التحقق من SHA-256؛ تم حذف الملف") }
        } finally { connection.disconnect() }
    }
}
