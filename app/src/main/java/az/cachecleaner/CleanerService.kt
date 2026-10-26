package az.cachecleaner

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class CleanerService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var chip: TextView? = null
    private var chipJob: Job? = null

    // ---------- Lifecycle ----------

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        chipJob?.cancel()
        chipJob = scope.launch { CleanerState.state.collect { renderChip(it) } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        job?.cancel()
        removeChip()
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        removeChip()
        if (instance === this) instance = null
        scope.cancel()
        super.onDestroy()
    }

    // ---------- Public API ----------

    fun startClean() {
        if (job?.isActive == true) return
        job = scope.launch { runClean() }
    }

    fun stopClean() {
        job?.cancel()
    }

    // ---------- Main loop ----------

    private suspend fun runClean() {
        val apps = withContext(Dispatchers.Default) { loadPackages() }
        CleanerState.modify {
            UiState(phase = Phase.RUNNING, total = apps.size, startMs = System.currentTimeMillis())
        }
        var completed = false
        try {
            delay(800) // PiP-in tam açılmasını gözlə
            for ((pkg, label) in apps) {
                currentCoroutineContext().ensureActive()
                CleanerState.modify { it.copy(currentApp = label) }

                val bytes: Long? = try {
                    withTimeoutOrNull(25_000L) { cleanOne(pkg, label) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "skip $pkg", e)
                    null
                }

                CleanerState.modify { s ->
                    s.copy(
                        processed = s.processed + 1,
                        bytesCleaned = s.bytesCleaned + (bytes ?: 0L),
                        okCount = s.okCount + if (bytes != null) 1 else 0,
                        skipped = s.skipped + if (bytes == null) 1 else 0,
                        cleaned = if (bytes != null && bytes > 0L) s.cleaned + CleanedApp(label, pkg, bytes) else s.cleaned
                    )
                }
            }
            completed = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "runClean failed", e)
        } finally {
            CleanerState.modify {
                it.copy(
                    phase = if (completed) Phase.FINISHED else Phase.STOPPED,
                    endMs = System.currentTimeMillis(),
                    currentApp = ""
                )
            }
            try {
                startActivity(
                    Intent(this, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "cannot return to app", e)
            }
            removeChip()
        }
    }

    private fun loadPackages(): List<Pair<String, String>> {
        val pm = packageManager
        @Suppress("DEPRECATION")
        val list = if (Build.VERSION.SDK_INT >= 33)
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        else pm.getInstalledApplications(0)
        return list.filter { it.enabled && it.packageName != packageName }
            .map { it.packageName to pm.getApplicationLabel(it).toString() }
            .sortedBy { it.second.lowercase() }
    }

    // ---------- Bir tətbiqin keşini təmizlə ----------
    // Qaytarır: təmizlənən bayt (0 = ölçü məlum deyil / keş boş), null = keçildi.

    private suspend fun cleanOne(pkg: String, label: String): Long? {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            return null
        }

        var storageTaps = 0
        var menuTaps = 0
        var sinceTap = 99
        var pageConfirmed = false
        val miui = Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")

        for (step in 0 until 30) {
            delay(300)
            sinceTap++
            val root = rootInActiveWindow ?: continue
            if (root.packageName?.toString() == packageName) continue

            val nodes = snapshot(root)

            // Əvvəlki tətbiqin köhnə ekranına basmamaq üçün: hədəf tətbiqin adı görünməlidir
            if (!pageConfirmed) {
                pageConfirmed = nodes.any { it.label()?.equals(label, ignoreCase = true) == true }
                if (!pageConfirmed && step < 8) continue
                pageConfirmed = true
            }

            // 1) "Clear cache" düyməsi
            val btn = nodes.firstOrNull { Matchers.isCacheButton(it.label()) }
            if (btn != null) {
                val target = clickable(btn)
                if (target != null) {
                    if (!target.isEnabled) return 0L          // keş artıq boşdur
                    val before = cacheSize(nodes)
                    if (before == 0L) return 0L
                    if (!target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) continue
                    return verify(before)
                }
            }
            if (sinceTap < 3) continue

            // 2) "Storage" / "Storage & cache" sətri
            if (storageTaps < 2) {
                val row = nodes.firstOrNull { Matchers.isStorageRow(it.label()) && clickable(it) != null }
                if (row != null) {
                    clickable(row)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    storageTaps++
                    sinceTap = 0
                    continue
                }
            }

            // 3) Yalnız Xiaomi: "Clear data" menyu açır, oradan yalnız "Clear cache" seçilir
            if (miui && menuTaps < 1) {
                val entry = nodes.firstOrNull { Matchers.isMiuiMenuEntry(it.label()) && clickable(it) != null }
                if (entry != null) {
                    clickable(entry)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    menuTaps++
                    sinceTap = 0
                    continue
                }
            }

            // Menyu/dialog açılıb, amma keş seçimi yoxdursa: heç nəyi təsdiqləmədən geri
            if (menuTaps > 0 && sinceTap >= 4) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                return null
            }
        }
        return null
    }

    /** Kliklədən sonra ölçünün azaldığını yoxla. */
    private suspend fun verify(before: Long?): Long {
        if (before == null) return 0L
        for (i in 0 until 4) {
            delay(250)
            val root = rootInActiveWindow ?: continue
            val after = cacheSize(snapshot(root)) ?: return before
            if (after < before) return before - after
        }
        return 0L
    }

    // ---------- Node köməkçiləri ----------

    private fun AccessibilityNodeInfo.label(): String? =
        (text ?: contentDescription)?.toString()?.takeIf { it.isNotBlank() }

    private fun snapshot(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 40 || out.size > 600) return
            out.add(n)
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        return out
    }

    private fun clickable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var c: AccessibilityNodeInfo? = n
        var d = 0
        while (c != null && d < 6) {
            if (c.isClickable) return c
            c = c.parent
            d++
        }
        return null
    }

    private fun cacheSize(nodes: List<AccessibilityNodeInfo>): Long? {
        val texts = nodes.mapNotNull { it.label() }
        for (i in texts.indices) {
            if (!Matchers.isCacheTitle(texts[i])) continue
            Matchers.parseSize(texts[i])?.let { return it }
            val end = minOf(i + 3, texts.lastIndex)
            for (j in (i + 1)..end) {
                Matchers.parseSize(texts[j])?.let { return it }
            }
        }
        return null
    }

    // ---------- Üzən "Dayandır" çipi (başqa tətbiqlərin üstündə) ----------

    private fun renderChip(s: UiState) {
        if (s.phase != Phase.RUNNING) {
            removeChip()
            return
        }
        if (chip == null) addChip()
        chip?.text = "⏹  ${s.percent}%  ·  Dayandır"
    }

    private fun addChip() {
        val tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(44, 22, 44, 22)
            background = GradientDrawable().apply {
                setColor(0xDD000000.toInt())
                cornerRadius = 80f
            }
            setOnClickListener { stopClean() }
        }
        // Əlçatanlıq xidməti üçün xüsusi overlay tipi: ayrıca icazə tələb etmir
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 120
        }
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(tv, lp)
            chip = tv
        } catch (e: Exception) {
            Log.w(TAG, "overlay failed", e)
        }
    }

    private fun removeChip() {
        val c = chip ?: return
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(c)
        } catch (e: Exception) {
            Log.w(TAG, "removeChip", e)
        }
        chip = null
    }

    companion object {
        private const val TAG = "CacheCleaner"

        @Volatile
        var instance: CleanerService? = null
    }
}
