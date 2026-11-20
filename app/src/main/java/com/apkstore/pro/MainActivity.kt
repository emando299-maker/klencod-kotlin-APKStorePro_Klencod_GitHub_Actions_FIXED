package com.apkstore.pro

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max

class MainActivity : Activity() {
    private val bg = Color.rgb(9, 15, 29)
    private val surface = Color.rgb(19, 29, 49)
    private val surface2 = Color.rgb(26, 39, 64)
    private val accent = Color.rgb(48, 132, 255)
    private val text = Color.WHITE
    private val muted = Color.rgb(165, 180, 203)
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var repo: RepoClient
    private lateinit var content: LinearLayout
    private lateinit var headerStatus: TextView
    private lateinit var search: EditText
    private var catalog: List<StoreApp> = emptyList()
    private var currentTab = "home"
    private var currentCategory: String? = null
    private var listHost: LinearLayout? = null
    private val requestLocalApk = 301

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = RepoClient(this)
        window.statusBarColor = bg; window.navigationBarColor = bg
        buildShell(); loadCatalog(false)
    }

    private fun buildShell() {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(24)) }
        scroll.addView(content)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(surface); gravity = Gravity.CENTER }
        listOf("الرئيسية", "التصنيفات", "التنزيلات", "تطبيقاتي").forEachIndexed { i, label ->
            val b = TextView(this).apply { text = label; textSize = 12f; gravity = Gravity.CENTER; setTextColor(if (i == 0) this@MainActivity.text else muted); setPadding(dp(7), dp(15), dp(7), dp(15)) }
            b.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            b.setOnClickListener { when(i) { 0 -> showHome(); 1 -> showCategories(); 2 -> showDownloads(); else -> showInstalled() } }
            nav.addView(b)
        }
        outer.addView(nav)
        setContentView(outer)
        showHome()
    }

    private fun reset(title: String, subtitle: String = "") {
        content.removeAllViews()
        listHost = null
        val top = row()
        val brand = column()
        brand.addView(label("APK STORE PRO", 22f, text, true))
        if (subtitle.isNotBlank()) brand.addView(label(subtitle, 12f, muted))
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val refresh = button("تحديث") { loadCatalog(true) }
        top.addView(refresh)
        content.addView(top)
        content.addView(label(title, 20f, text, true).apply { setPadding(0, dp(18), 0, dp(8)) })
        headerStatus = label("المصدر: مستودع F-Droid الرسمي", 11f, muted)
        content.addView(headerStatus)
    }

    private fun showHome() {
        currentTab = "home"; currentCategory = null
        reset("اكتشف تطبيقات جديدة", "متجر مستقل • تنزيلات وتحديثات وإدارة APK")
        search = EditText(this).apply {
            hint = "ابحث عن تطبيق أو لعبة"; setSingleLine(true); textSize = 14f
            setTextColor(this@MainActivity.text); setHintTextColor(muted); setPadding(dp(14), dp(11), dp(14), dp(11)); setBackgroundColor(surface2)
        }
        content.addView(search, match().apply { topMargin = dp(12); bottomMargin = dp(12) })
        search.addTextChangedListener(SimpleTextWatcher { renderAppList(filterApps(it), "نتائج البحث") })
        content.addView(label("اختصارات", 16f, text, true))
        val quick = row()
        listOf("الأكثر حداثة", "الأدوات", "الألعاب").forEach { q -> quick.addView(button(q) {
            val found = when(q) { "الأدوات" -> catalog.filter { it.categories.any { c -> c.contains("util", true) || c.contains("tool", true) } }; "الألعاب" -> catalog.filter { it.categories.any { c -> c.contains("game", true) } }; else -> catalog.sortedByDescending { it.versionCode } }
            renderAppList(found, q)
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(5) }) }
        content.addView(quick)
        content.addView(label("تطبيقات مقترحة", 17f, text, true).apply { setPadding(0, dp(16), 0, dp(6)) })
        listHost = column()
        content.addView(listHost)
        renderAppList(catalog.take(30), "تطبيقات مقترحة", false)
    }

    private fun loadCatalog(force: Boolean) {
        headerStatusOrNull("جارٍ تحميل فهرس التطبيقات…")
        worker.execute {
            try {
                val list = repo.loadCatalog(force)
                runOnUiThread {
                    catalog = list
                    if (currentTab == "home") showHome()
                    else if (currentTab == "categories") showCategories()
                    headerStatusOrNull("تم تحميل ${list.size} تطبيقاً من مستودع F-Droid")
                }
            } catch (e: Exception) {
                runOnUiThread { headerStatusOrNull("تعذر تحديث المتجر: ${e.message}. تحقق من الإنترنت.") }
            }
        }
    }

    private fun headerStatusOrNull(s: String) { if (::headerStatus.isInitialized) headerStatus.text = s }
    private fun filterApps(q: String): List<StoreApp> = if (q.isBlank()) catalog.take(30) else catalog.filter {
        it.name.contains(q, true) || it.id.contains(q, true) || it.summary.contains(q, true) || it.categories.any { c -> c.contains(q, true) }
    }.take(100)

    private fun renderAppList(items: List<StoreApp>, title: String, addHeading: Boolean = true) {
        if (addHeading) {
            content.addView(label(title, 17f, text, true).apply { setPadding(0, dp(14), 0, dp(7)) })
            listHost = column()
            content.addView(listHost)
        }
        val host = listHost ?: content
        if (!addHeading) host.removeAllViews()
        items.forEach { app -> host.addView(appCard(app)) }
        if (items.isEmpty()) host.addView(label("لا توجد نتائج. حدّث الفهرس أو جرّب كلمة بحث أخرى.", 13f, muted))
    }

    private fun appCard(app: StoreApp): View {
        val card = row().apply { setBackgroundColor(surface); setPadding(dp(12), dp(11), dp(12), dp(11)); gravity = Gravity.CENTER_VERTICAL }
        val icon = TextView(this).apply { text = app.name.take(1).uppercase(); textSize = 22f; gravity = Gravity.CENTER; setTextColor(this@MainActivity.text); setBackgroundColor(surface2) }
        card.addView(icon, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(12) })
        val info = column()
        info.addView(label(app.name, 15f, text, true))
        info.addView(label(app.summary.take(72), 11f, muted))
        info.addView(label("${app.versionName}  •  ${formatSize(app.size)}", 10f, muted))
        card.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(button("عرض") { showDetails(app) })
        card.setOnClickListener { showDetails(app) }
        return card.apply { val p = match(); p.bottomMargin = dp(8); layoutParams = p }
    }

    private fun showDetails(app: StoreApp) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(20), dp(22), dp(20)); setBackgroundColor(surface) }
        box.addView(label(app.name, 21f, text, true))
        box.addView(label(app.id, 11f, muted))
        box.addView(label("الإصدار: ${app.versionName}\nالحجم: ${formatSize(app.size)}\nأقل إصدار أندرويد: ${app.minSdk}\nالتصنيفات: ${app.categories.joinToString().ifBlank { "غير محدد" }}", 12f, muted).apply { setPadding(0, dp(12), 0, dp(12)) })
        box.addView(label(app.summary, 14f, text))
        box.addView(label(app.description.replace(Regex("<[^>]*>"), " ").take(1600), 12f, muted).apply { setPadding(0, dp(10), 0, dp(12)) })
        box.addView(button("تنزيل APK والتحقق من SHA-256") { downloadApp(app) })
        box.addView(button("فتح صفحة المصدر") { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/${app.id}/"))) })
        val scroll = ScrollView(this); scroll.addView(box)
        android.app.AlertDialog.Builder(this).setView(scroll).setNegativeButton("إغلاق", null).show()
    }

    private fun downloadApp(app: StoreApp) {
        val dialog = android.app.ProgressDialog(this).apply { setTitle("تنزيل ${app.name}"); setMessage("جارٍ الاتصال بالمستودع…"); setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL); max = 100; setCancelable(false); show() }
        worker.execute {
            try {
                val file = File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), app.apkName)
                repo.downloadAndVerify(app, file) { done, total -> runOnUiThread { dialog.progress = if (total > 0) (done * 100 / total).toInt() else 0; dialog.setMessage("${formatSize(done)} / ${formatSize(total)}") } }
                runOnUiThread { dialog.dismiss(); installApk(file) }
            } catch (e: Exception) { runOnUiThread { dialog.dismiss(); toast("فشل التنزيل أو التحقق: ${e.message}") } }
        }
    }

    private fun installApk(file: File) {
        if (android.os.Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
            startActivity(intent); toast("اسمح للتطبيق بتثبيت ملفات APK ثم اضغط تثبيت مرة أخرى")
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/vnd.android.package-archive"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            startActivity(intent)
        } catch (e: Exception) { toast("تعذر فتح المثبّت: ${e.message}") }
    }

    private fun showCategories() {
        currentTab = "categories"; reset("التصنيفات", "تصفّح التطبيقات حسب نوعها")
        val cats = catalog.flatMap { it.categories }.distinct().filter { it.isNotBlank() }.sorted()
        if (cats.isEmpty()) { content.addView(label("حمّل فهرس المتجر لعرض التصنيفات.", 13f, muted)); return }
        cats.forEach { c -> content.addView(button(c) { currentCategory = c; reset("تصنيف: $c"); renderAppList(catalog.filter { app -> app.categories.any { it.equals(c, true) } }, c) }) }
    }

    private fun showDownloads() {
        currentTab = "downloads"; reset("التنزيلات", "ملفات APK المحفوظة داخل مساحة التطبيق")
        content.addView(button("اختيار APK من الهاتف") { pickLocalApk() }, match().apply { topMargin = dp(8); bottomMargin = dp(12) })
        val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
        val files = dir?.listFiles()?.filter { it.isFile && it.extension.equals("apk", true) }?.sortedByDescending { it.lastModified() }.orEmpty()
        if (files.isEmpty()) content.addView(label("لا توجد تنزيلات حتى الآن.", 13f, muted))
        files.forEach { f ->
            val r = row(); r.addView(label("${f.name}\n${formatSize(f.length())}", 12f, text), LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(button("تثبيت") { installApk(f) }); content.addView(r)
        }
    }

    private fun showInstalled() {
        currentTab = "installed"; reset("تطبيقاتي", "التطبيقات المثبتة على هذا الجهاز")
        val pm = packageManager
        @Suppress("DEPRECATION")
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA).sortedBy { it.loadLabel(pm).toString().lowercase() }
        installed.forEach { app ->
            val row = this.row()
            val name = app.loadLabel(pm).toString()
            row.addView(label("$name\n${app.packageName}", 12f, text), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(button("فتح") {
                val launch = pm.getLaunchIntentForPackage(app.packageName)
                if (launch != null) startActivity(launch) else toast("لا توجد واجهة تشغيل")
            })
            val latest = catalog.firstOrNull { it.id == app.packageName }
            val installedCode = try {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(app.packageName, 0).versionCode.toLong()
            } catch (_: Exception) { 0L }
            if (latest != null && latest.versionCode > installedCode) {
                row.addView(button("تحديث") { downloadApp(latest) })
            }
            row.addView(button("إزالة") { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))) })
            content.addView(row)
        }
    }


    private fun pickLocalApk() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.android.package-archive"
        }
        startActivityForResult(intent, requestLocalApk)
    }

    @Deprecated("Platform document picker callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != requestLocalApk || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        worker.execute {
            try {
                val target = File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "imported_${System.currentTimeMillis()}.apk")
                contentResolver.openInputStream(uri).use { input ->
                    if (input == null) throw IllegalStateException("لا يمكن قراءة الملف")
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                @Suppress("DEPRECATION")
                val info = packageManager.getPackageArchiveInfo(target.absolutePath, 0)
                runOnUiThread {
                    if (info == null) toast("الملف ليس APK صالحاً أو أنه حزمة مقسمة غير مدعومة")
                    else android.app.AlertDialog.Builder(this)
                        .setTitle(info.applicationInfo?.loadLabel(packageManager) ?: info.packageName)
                        .setMessage("اسم الحزمة: ${info.packageName}\nالإصدار: ${info.versionName ?: "غير معروف"}\nالحجم: ${formatSize(target.length())}")
                        .setPositiveButton("تثبيت") { _, _ -> installApk(target) }
                        .setNegativeButton("إلغاء", null).show()
                }
            } catch (e: Exception) { runOnUiThread { toast("تعذر فتح الملف: ${e.message}") } }
        }
    }

    private fun button(value: String, action: () -> Unit): TextView = TextView(this).apply {
        text = value; textSize = 12f; gravity = Gravity.CENTER; setTextColor(this@MainActivity.text); setPadding(dp(12), dp(9), dp(12), dp(9)); setBackgroundColor(accent); setOnClickListener { action() }
    }
    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun match() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun formatSize(bytes: Long): String = when { bytes <= 0 -> "غير معروف"; bytes < 1024 -> "$bytes B"; bytes < 1024*1024 -> "%.1f KB".format(bytes/1024.0); else -> "%.1f MB".format(bytes/1024.0/1024.0) }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    override fun onDestroy() { super.onDestroy(); worker.shutdown() }
}