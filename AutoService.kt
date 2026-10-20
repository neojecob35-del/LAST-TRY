package com.example.ighelper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class AutoService : AccessibilityService() {

    companion object {
        @Volatile var instance: AutoService? = null
        const val IG = "com.instagram.android"
        const val PREFS = "cfg"

        fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

        fun likesToday(c: Context): Int {
            val p = c.getSharedPreferences(PREFS, MODE_PRIVATE)
            return if (p.getString("day", "") == today()) p.getInt("likes", 0) else 0
        }

        fun savesToday(c: Context): Int {
            val p = c.getSharedPreferences(PREFS, MODE_PRIVATE)
            return if (p.getString("day", "") == today()) p.getInt("saves", 0) else 0
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    @Volatile var running = false

    private val stepRunnable = Runnable { cycle() }

    // ---------- Floating popup (shows only while Instagram is in front) ----------
    private val uiHandler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var popup: LinearLayout? = null
    private var popupParams: WindowManager.LayoutParams? = null
    private var body: LinearLayout? = null
    private var statusView: TextView? = null
    private var toggleBtn: Button? = null
    private var popupShown = false
    private var collapsed = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildPopup() {
        if (popup != null) return
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2111111")); cornerRadius = dp(16).toFloat()
            }
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "IG Helper  ⠿"; setTextColor(Color.WHITE); textSize = 14f
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val min = TextView(this).apply {
            text = "—"; setTextColor(Color.WHITE); textSize = 18f; setPadding(dp(14), 0, dp(4), 0)
        }
        header.addView(min)
        box.addView(header)

        val b = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        statusView = TextView(this).apply { setTextColor(Color.WHITE); textSize = 12f; setPadding(0, dp(6), 0, dp(6)) }
        b.addView(statusView)
        toggleBtn = Button(this).apply {
            isAllCaps = false
            setOnClickListener { if (running) stop() else start(); refreshPopup() }
        }
        b.addView(toggleBtn)
        b.addView(Button(this).apply {
            text = "Settings"; isAllCaps = false
            setOnClickListener {
                startActivity(Intent(this@AutoService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        })
        box.addView(b)
        body = b

        min.setOnClickListener {
            collapsed = !collapsed
            b.visibility = if (collapsed) android.view.View.GONE else android.view.View.VISIBLE
            min.text = if (collapsed) "+" else "—"
        }

        val lp = WindowManager.LayoutParams(
            dp(200), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(12); y = dp(90) }
        popupParams = lp

        // drag by the header
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0
        header.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = lp.x; sy = lp.y; true }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = sx + (e.rawX - dx).toInt(); lp.y = sy + (e.rawY - dy).toInt()
                    if (popupShown) wm?.updateViewLayout(box, lp)
                    true
                }
                else -> false
            }
        }
        popup = box
    }

    private fun setPopupVisible(show: Boolean) {
        val v = popup ?: return
        if (show == popupShown) return
        try {
            if (show) wm?.addView(v, popupParams) else wm?.removeView(v)
            popupShown = show
        } catch (_: Exception) {}
    }

    private fun refreshPopup() {
        val p = prefs()
        statusView?.text = (if (running) "● Running" else "○ Stopped") +
            "\n❤ ${likesToday(this)}/${p.getInt("likeLimit", 20)}   💾 ${savesToday(this)}/${p.getInt("saveLimit", 7)}"
        toggleBtn?.text = if (running) "Stop" else "Start"
    }

    private val uiTicker = object : Runnable {
        override fun run() {
            val inIg = rootInActiveWindow?.packageName?.toString() == IG
            setPopupVisible(inIg)
            if (inIg) refreshPopup()
            uiHandler.postDelayed(this, 1000)
        }
    }

    override fun onServiceConnected() {
        instance = this
        buildPopup()
        uiHandler.removeCallbacksAndMessages(null)
        uiHandler.post(uiTicker)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stop() }
    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        uiHandler.removeCallbacksAndMessages(null)
        setPopupVisible(false)
        instance = null
        return super.onUnbind(intent)
    }

    fun start() {
        running = true
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(stepRunnable, 1500)
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    private fun bump(key: String) {
        val p = prefs()
        val day = today()
        if (p.getString("day", "") != day) {
            p.edit().putString("day", day).putInt("likes", 0).putInt("saves", 0).apply()
        }
        p.edit().putInt(key, p.getInt(key, 0) + 1).apply()
    }

    private fun cycle() {
        if (!running) return
        val p = prefs()
        if (likesToday(this) >= p.getInt("likeLimit", 20)) {
            running = false   // daily limit reached
            return
        }
        if (rootInActiveWindow?.packageName?.toString() != IG) {
            handler.postDelayed(stepRunnable, 2000)   // wait until Instagram is open
            return
        }

        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()

        // 1) Like: double tap in the middle of the reel
        doubleTap(w * 0.5f + jitter(), h * 0.45f + jitter())
        handler.postDelayed({ if (running) bump("likes") }, 300)

        // 2) Save (only while under the daily save limit)
        handler.postDelayed({
            if (running && savesToday(this) < p.getInt("saveLimit", 7)) save(w, h)
        }, 900)

        // 3) Swipe to next reel
        val doSwipe = p.getBoolean("swipe", true)
        handler.postDelayed({
            if (running && doSwipe) swipeUp(w, h)
        }, 1700)

        // 4) Wait a random time before the next reel
        val minS = p.getInt("minDelay", 4)
        val maxS = maxOf(p.getInt("maxDelay", 9), minS + 1)
        val wait = 1700L + Random.nextInt(minS * 1000, maxS * 1000) + 800L
        handler.postDelayed(stepRunnable, wait)
    }

    private fun save(w: Float, h: Float) {
        val root = rootInActiveWindow
        val node = root?.let { findSaveNode(it) }
        if (node != null) {
            val d = node.contentDescription?.toString()?.lowercase() ?: ""
            if (d.contains("remove") || d.contains("unsave") || d == "saved") return // already saved
            var n: AccessibilityNodeInfo? = node
            while (n != null && !n.isClickable) n = n.parent
            if (n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                bump("saves")
                return
            }
        }
        // Fallback: tap where the bookmark icon is on screen
        tap(w * 0.92f, h * 0.775f)
        bump("saves")
    }

    private fun findSaveNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val d = node.contentDescription?.toString()?.lowercase()
        if (d == "save" || d == "add to saved" || d == "saved" ||
            d == "remove from saved" || d == "unsave") return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            val r = findSaveNode(c)
            if (r != null) return r
        }
        return null
    }

    private fun jitter() = Random.nextInt(-25, 26).toFloat()

    private fun stroke(x: Float, y: Float, start: Long): GestureDescription.StrokeDescription {
        val path = Path().apply { moveTo(x, y) }
        return GestureDescription.StrokeDescription(path, start, 50)
    }

    private fun tap(x: Float, y: Float) {
        val g = GestureDescription.Builder().addStroke(stroke(x, y, 0)).build()
        dispatchGesture(g, null, null)
    }

    private fun doubleTap(x: Float, y: Float) {
        val g = GestureDescription.Builder()
            .addStroke(stroke(x, y, 0))
            .addStroke(stroke(x, y, 130))
            .build()
        dispatchGesture(g, null, null)
    }

    private fun swipeUp(w: Float, h: Float) {
        val path = Path().apply {
            moveTo(w * 0.5f + jitter(), h * 0.72f)
            lineTo(w * 0.5f + jitter(), h * 0.28f)
        }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 260))
            .build()
        dispatchGesture(g, null, null)
    }
}
