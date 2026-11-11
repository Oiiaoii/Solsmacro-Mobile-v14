package com.solmacro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

private typealias WL = WindowManager.LayoutParams

private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

class MacroService : AccessibilityService() {

    companion object { var inst: MacroService? = null }

    private class Step(val x: Int, val y: Int, val hold: Long, val wait: Long)

    private val h = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var panel: LinearLayout? = null
    private var startBtn: TextView? = null
    private var running = false
    private var catches = 0
    private var tl: List<Step> = emptyList()
    private var idx = 0
    private var lastPkg = ""

    // detection state
    private var detectMode = false
    private var phase = 0
    private var miss = 0
    private var sellIdx = 0
    private var t0 = 0L
    private var lastShot = 0L
    private var lastMark = -1.0
    private var lastHeld = false
    private var votes = 0
    private val bg = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var dbgView: DebugView? = null
    private var pauseBtn: TextView? = null
    private var iconView: ImageView? = null
    private var panelUi: PanelUi? = null
    private var biteAvg = 0.0
    private var wZone = 0.0
    private var nextTapAt = 0L
    private var tapCount = 0
    private var framesN = 0
    private var framesIn = 0
    private var histN = 0
    private val tipHist = DoubleArray(3)
    private var lastTap = 0L
    private var screenW = 2340
    private var biteN = 0
    private var paused = false
    private var lastSeen = 0L
    private var holdUntil = 0L
    private var failCasts = 0
    private var fpsT = 0L
    private var fpsN = 0
    private var dbgCount = 0
    private var vel = 0.0
    private var lastM = -1.0
    private var lastMT = 0L
    private var roi: IntArray? = null
    private var roiN = 0
    private var roiMiss = 0
    private var lastGesture = 0L
    private var lastShotSent = 0L
    private val taskLast = LongArray(3)
    private var curTask = 0
    private var bioCur = ""
    private var bioPend = ""
    private var bioLast = 0L
    @Volatile private var bioBusy = false
    private var bioWarned = false
    private val th = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            try { services() } catch (e: Exception) { Dbg.add("Background tick error: " + e.message) }
            th.postDelayed(this, 5000L)
        }
    }

    override fun onServiceConnected() {
        inst = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        Cv.init()
        showPanel()
        th.removeCallbacks(ticker)
        th.postDelayed(ticker, 5000L)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val p = e.packageName?.toString() ?: return
            if (p != packageName && p != "com.android.systemui") lastPkg = p
        }
    }

    override fun onInterrupt() { stop() }

    override fun onUnbind(intent: Intent?): Boolean {
        th.removeCallbacks(ticker)
        closePanel()
        stop()
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
        inst = null
        return super.onUnbind(intent)
    }

    private fun foreground(): String =
        if (lastPkg.isNotEmpty()) lastPkg else (rootInActiveWindow?.packageName?.toString() ?: "")

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    fun togglePause() {
        if (!running) return
        if (paused) {
            paused = false
            pauseBtn?.text = "PAUSE"
            Dbg.add("Resumed")
            if (phase == 6) phase = 0
            if (detectMode) dnext() else next()
        } else {
            paused = true
            h.removeCallbacksAndMessages(null)
            pauseBtn?.text = "RESUME"
            Dbg.state = "Paused"
            Dbg.add("Paused")
        }
    }

    private fun tapPoint(name: String) {
        val c = applicationContext
        tap(Cfg.get(c, name + "_x"), Cfg.get(c, name + "_y"), 60L)
    }

    private fun moveHold(dir: String, ms: Long) {
        val c = applicationContext
        val r = Cfg.get(c, "joyr")
        val d = when (dir) { "w" -> "up"; "s" -> "down"; "a" -> "left"; "d" -> "right"; else -> dir }
        val dx = when (d) { "left" -> -r; "right" -> r; else -> 0 }
        val dy = when (d) { "up" -> -r; "down" -> r; else -> 0 }
        val cx = Cfg.get(c, "joy_x").toFloat()
        val cy = Cfg.get(c, "joy_y").toFloat()
        val p1 = Path().apply { moveTo(cx, cy); lineTo(cx + dx, cy + dy) }
        val s1 = GestureDescription.StrokeDescription(p1, 0L, 120L, true)
        val ok = dispatchGesture(GestureDescription.Builder().addStroke(s1).build(), object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                val p2 = Path().apply { moveTo(cx + dx, cy + dy) }
                val s2 = s1.continueStroke(p2, 0L, maxOf(ms, 1L), false)
                dispatchGesture(GestureDescription.Builder().addStroke(s2).build(), null, null)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Dbg.add("Move cancelled")
            }
        }, null)
        if (!ok) Dbg.add("Android refused the move")
    }

    private fun camSwipe(dir: String, amt: Int) {
        val c = applicationContext
        val cx = Cfg.get(c, "cam_x").toFloat()
        val cy = Cfg.get(c, "cam_y").toFloat()
        val dx = when (dir) { "left" -> -amt; "right" -> amt; else -> 0 }
        val dy = when (dir) { "up" -> -amt; "down" -> amt; else -> 0 }
        val p = Path().apply { moveTo(cx, cy); lineTo(cx + dx, cy + dy) }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0L, 250L)).build(), null, null)
    }

    // script lines: move up 1.5 | camera left 200 | shiftlock | tap inv | wait 1
    private fun runScript(text: String, done: () -> Unit) {
        val lines = text.lines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
        fun step(i: Int) {
            if (!running) return
            if (i >= lines.size) { done(); return }
            val p = lines[i].split(" ").filter { it.isNotEmpty() }
            var wait = 100L
            try {
                when (p[0].lowercase()) {
                    "wait" -> wait = (p[1].toDouble() * 1000).toLong()
                    "move" -> { val ms = (p[2].toDouble() * 1000).toLong(); moveHold(p[1].lowercase(), ms); wait = ms + 300 }
                    "camera" -> { camSwipe(p[1].lowercase(), p.getOrNull(2)?.toInt() ?: 250); wait = 450 }
                    "shiftlock" -> { tapPoint("shift"); wait = 350 }
                    "tap" -> {
                        val rx = p.getOrNull(1)?.toIntOrNull()
                        val ry = p.getOrNull(2)?.toIntOrNull()
                        if (rx != null && ry != null) tap(rx, ry, 60L) else tapPoint(p[1].lowercase())
                        wait = 350
                    }
                    else -> Dbg.add("Unknown script line: " + lines[i])
                }
            } catch (e: Exception) {
                Dbg.add("Bad script line: " + lines[i])
            }
            h.postDelayed({ step(i + 1) }, wait)
        }
        step(0)
    }

    // ---------- timed macro loop ----------
    private fun build(): List<Step> {
        val c = applicationContext
        val l = ArrayList<Step>()
        l.add(Step(Cfg.get(c, "cast_x"), Cfg.get(c, "cast_y"), Cfg.get(c, "cast").toLong(), Cfg.get(c, "wait").toLong()))
        repeat(Cfg.get(c, "taps")) {
            l.add(Step(Cfg.get(c, "reel_x"), Cfg.get(c, "reel_y"), 40L, Cfg.get(c, "gap").toLong()))
        }
        val every = Cfg.get(c, "every")
        if (Cfg.get(c, "sell") == 1 && every > 0 && (catches + 1) % every == 0) {
            for (k in listOf("inv", "sellall", "confirm")) {
                l.add(Step(Cfg.get(c, k + "_x"), Cfg.get(c, k + "_y"), 50L, Cfg.get(c, "mdelay").toLong()))
            }
        }
        return l
    }

    fun toggle() { if (running) stop() else start() }

    fun needPoints() {
        toast("Set the button positions first (Settings > Set button positions)")
        Dbg.add("Start blocked: button positions not set")
    }

    private fun start() {
        if (Cfg.get(applicationContext, "pts_ok") != 1) { needPoints(); return }
        if (foreground() != Cfg.ROBLOX) { toast("Open Roblox first"); return }
        Cfg.put(applicationContext, "detect", 1)
        Cfg.put(applicationContext, "full", 1)
        detectMode = true
        if (detectMode && Build.VERSION.SDK_INT < 30) { toast("Detection needs Android 11 or newer"); return }
        if (detectMode && Cfg.get(applicationContext, "full") == 1 && !Cap.active) {
            toast("Start fast capture in the app first")
            Dbg.add("Fast capture is off, not started")
            return
        }
        running = true
        idx = 0
        phase = 0
        miss = 0
        catches = 0
        Dbg.catches = 0
        Dbg.sells = 0
        Dbg.fails = 0
        Dbg.startMs = SystemClock.uptimeMillis()
        lastGesture = Dbg.startMs
        lastShotSent = Dbg.startMs
        for (i in taskLast.indices) taskLast[i] = Dbg.startMs
        Dbg.state = "Starting"
        Dbg.add("Started, detection=$detectMode")
        Hook.send(applicationContext, "Macro started", "Screen detection: $detectMode", 0x38D39F)
        paused = false
        failCasts = 0
        biteN = 0
        label()
        runScript(Cfg.gets(applicationContext, "script_start")) {
            if (detectMode) {
                dnext()
            } else {
                tl = build()
                next()
            }
        }
    }

    fun stop() {
        val was = running
        running = false
        paused = false
        pauseBtn?.text = "PAUSE"
        h.removeCallbacksAndMessages(null)
        hideDebug()
        Dbg.state = "Idle"
        label()
        if (was) {
            Dbg.add("Stopped after $catches catches")
            Hook.send(applicationContext, "Macro stopped", "$catches catches, ${Dbg.sells} sells", 0xFF6B6B)
        }
    }

    private fun next() {
        if (!running) return
        if (foreground() != Cfg.ROBLOX) { stop(); toast("Roblox closed, macro stopped"); return }
        if (idx >= tl.size) { onCatch(); if (doneCheck()) return; tl = build(); idx = 0 }
        val s = tl[idx++]
        tap(s.x, s.y, s.hold)
        h.postDelayed({ next() }, s.hold + s.wait)
    }

    private fun tap(x: Int, y: Int, hold: Long) {
        lastGesture = SystemClock.uptimeMillis()
        val p = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0L, maxOf(hold, 1L)))
            .build()
        val ok = dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Dbg.add("Tap cancelled at $x,$y")
            }
        }, null)
        if (!ok) Dbg.add("Android refused the tap at $x,$y")
    }

    private fun label() {
        startBtn?.text = if (running) "STOP ($catches)" else "START"
        val bgd = iconView?.background as? GradientDrawable
        bgd?.setStroke((2 * resources.displayMetrics.density).toInt(), if (running) 0xFF38D39F.toInt() else 0xFFFFFFFF.toInt())
    }

    private fun biteLimit(): Long {
        val c = applicationContext
        val maxMs = Cfg.get(c, "maxwait") * 1000L
        if (Cfg.get(c, "adapt") == 1 && biteN >= 3) {
            return (biteAvg * 2.5).toLong().coerceIn(8000L, maxOf(maxMs, 8000L))
        }
        return maxMs
    }

    private fun onCatch() {
        catches++
        Dbg.catches = catches
        label()
        val every = Cfg.get(applicationContext, "whevery")
        if (every > 0 && catches % every == 0) {
            Hook.send(applicationContext, "Progress", "$catches catches, ${Dbg.sells} sells", 0x3EA6FF)
        }
    }

    private fun showDbg(r: DoubleArray?) {
        if (!::wm.isInitialized) return
        val c = applicationContext
        var v = dbgView
        if (v == null) {
            v = DebugView(this)
            wm.addView(v, WL(MATCH, MATCH, WL.TYPE_ACCESSIBILITY_OVERLAY,
                WL.FLAG_NOT_TOUCHABLE or WL.FLAG_NOT_FOCUSABLE or WL.FLAG_LAYOUT_IN_SCREEN or WL.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT))
            dbgView = v
        }
        val x1 = minOf(Cfg.get(c, "r1_x"), Cfg.get(c, "r2_x"))
        val x2 = maxOf(Cfg.get(c, "r1_x"), Cfg.get(c, "r2_x"))
        val y1 = minOf(Cfg.get(c, "r1_y"), Cfg.get(c, "r2_y"))
        val y2 = maxOf(Cfg.get(c, "r1_y"), Cfg.get(c, "r2_y"))
        v.rect.set(x1, y1, x2, y2)
        val need = maxOf(Cfg.get(c, "minpx"), 1)
        if (r == null) {
            v.color = Color.RED
            v.label = "No screenshot"
            v.zmax = -1.0
            v.mpos = -1.0
        } else {
            val zn = r[0].toInt()
            val mn = r[3].toInt()
            val pct = minOf(100, zn * 100 / need)
            v.color = if (zn >= need && mn >= 3) Color.GREEN else if (zn >= need / 2) Color.YELLOW else Color.RED
            v.label = "zone match $pct%   marker ${mn}px   ${Dbg.state}"
            val auto = r.size >= 10 && r[5] >= 0
            v.vert = if (auto) r[5] == 1.0 else Cfg.get(c, "axis") == 1
            if (auto) v.rect.set(r[6].toInt(), r[7].toInt(), r[8].toInt(), r[9].toInt())
            v.zmin = r[1]
            v.zmax = r[2]
            v.mpos = if (mn > 0) r[4] / mn else -1.0
        }
        v.invalidate()
    }

    private fun hideDebug() {
        if (!::wm.isInitialized) return
        dbgView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        dbgView = null
    }

    // ---------- screen detection loop ----------
    private fun dnext() {
        if (!running) return
        if (foreground() != Cfg.ROBLOX) { stop(); toast("Roblox closed, macro stopped"); return }
        val c = applicationContext
        Dbg.state = when (phase) { 0 -> "Casting"; 1 -> "Waiting for the bar"; 2 -> "Reeling"; 3 -> "Selling"; 7 -> "Checking result"; 8 -> "Running task"; else -> "Walking" }
        when (phase) {
            0 -> {
                val due = dueTask()
                if (due >= 0) {
                    curTask = due
                    phase = 8
                    dnext()
                    return
                }
                tap(Cfg.get(c, "cast_x"), Cfg.get(c, "cast_y"), Cfg.get(c, "cast").toLong())
                phase = 1
                t0 = SystemClock.uptimeMillis()
                h.postDelayed({ dnext() }, Cfg.get(c, "cast") + Cfg.get(c, "castdelay").toLong())
            }
            1 -> track { r ->
                if (!running) return@track
                val n = r?.get(0)?.toInt() ?: 0
                if (n >= Cfg.get(c, "minpx")) {
                    phase = 2
                    failCasts = 0
                    val bt = SystemClock.uptimeMillis() - t0
                    biteAvg = if (biteN == 0) bt.toDouble() else 0.7 * biteAvg + 0.3 * bt
                    biteN++
                    Dbg.biteMs = biteAvg.toInt()
                    wZone = screenW * 0.04
                    lastTap = 0L
                    nextTapAt = 0L
                    tapCount = 0
                    framesN = 0
                    framesIn = 0
                    histN = 0
                    lastMark = -1.0
                    lastHeld = false
                    votes = 0
                    roi = null
                    roiN = 0
                    roiMiss = 0
                    vel = 0.0
                    lastM = -1.0
                    lastMT = 0L
                    t0 = SystemClock.uptimeMillis()
                    lastSeen = t0
                    Dbg.add("Bar found (zone $n px)")
                } else if (SystemClock.uptimeMillis() - t0 > biteLimit()) {
                    failCasts++
                    Dbg.add("Bar not found, cast again ($failCasts/3)")
                    if (failCasts >= 3) {
                        stop()
                        toast("Bar not found 3 times, stopped. Check COLOR and TEST.")
                        Hook.send(applicationContext, "Macro stopped", "The reeling bar was not found 3 times in a row.", 0xFF6B6B)
                        return@track
                    }
                    phase = 0
                }
                h.postDelayed({ dnext() }, 15L)
            }
            2 -> track { r ->
                if (!running) return@track
                if (Cfg.get(c, "full") == 1) { reelFull(r); return@track }
                var held = false
                if (r != null && r[0].toInt() >= Cfg.get(c, "minpx")) {
                    lastSeen = SystemClock.uptimeMillis()
                    if (r[3].toInt() >= 3) {
                        val zc = (r[1] + r[2]) / 2.0
                        val mc = r[4] / r[3]
                        var dir = Cfg.get(c, "holddir")
                        if (lastHeld && lastMark >= 0) {
                            val d = mc - lastMark
                            if (Math.abs(d) > 6) {
                                if ((d > 0) == (dir == 1)) votes++ else votes--
                                if (votes <= -3) {
                                    dir = 1 - dir
                                    Cfg.put(c, "holddir", dir)
                                    votes = 0
                                    toast("Hold direction flipped")
                                }
                            }
                        }
                        val db = Cfg.get(c, "deadband")
                        held = if (dir == 0) mc > zc + db else mc < zc - db
                        if (held && SystemClock.uptimeMillis() >= holdUntil - 40L) {
                            val hm = Cfg.get(c, "holdms").toLong()
                            tap(Cfg.get(c, "reel_x"), Cfg.get(c, "reel_y"), hm)
                            holdUntil = SystemClock.uptimeMillis() + hm
                        }
                        lastMark = mc
                    }
                }
                lastHeld = held
                dbgCount++
                if (dbgCount % 15 == 0) Dbg.add("reel zone=${r?.get(0)?.toInt()} marker=${r?.get(3)?.toInt()} held=$held")
                if (Cfg.get(c, "debug") == 1) showDbg(r)
                if (SystemClock.uptimeMillis() - lastSeen > Cfg.get(c, "gonems").toLong() || SystemClock.uptimeMillis() - t0 > 60000L) {
                    onCatch()
                    if (doneCheck()) return@track
                    val every = Cfg.get(c, "every")
                    phase = if (Cfg.get(c, "sell") == 1 && every > 0 && catches % every == 0) 4 else 0
                    sellIdx = 0
                    h.postDelayed({ dnext() }, Cfg.get(c, "postcatch").toLong())
                } else {
                    h.postDelayed({ dnext() }, 20L)
                }
            }
            7 -> track { r ->
                if (!running) return@track
                val failed = r != null && r.size >= 11 && r[10] >= 150
                var sellNext = false
                Dbg.add(if (failed) "Result: failed, tapping the close (X) button" else "Result: caught, tapping the close (X) button")
                tapPoint("dismiss")
                h.postDelayed({ tapPoint("dismiss") }, 450L)
                if (failed) {
                    Dbg.fails++
                } else {
                    onCatch()
                    if (doneCheck()) return@track
                    val every = Cfg.get(c, "every")
                    sellNext = Cfg.get(c, "sell") == 1 && every > 0 && catches % every == 0
                }
                phase = if (sellNext) 4 else 0
                sellIdx = 0
                h.postDelayed({ dnext() }, 1100L)
            }
            4 -> {
                phase = 6
                runScript(Cfg.gets(c, "script_pre")) { phase = 3; sellIdx = 0; dnext() }
            }
            5 -> {
                phase = 6
                runScript(Cfg.gets(c, "script_post")) { phase = 0; dnext() }
            }
            8 -> {
                phase = 6
                val ti = curTask
                Dbg.add("Running timed task " + (ti + 1))
                runScript(Cfg.gets(c, "t" + (ti + 1) + "s")) {
                    taskLast[ti] = SystemClock.uptimeMillis()
                    phase = 0
                    dnext()
                }
            }
            6 -> { }
            else -> {
                val k = listOf("inv", "sellall", "confirm")[sellIdx]
                sellIdx++
                tap(Cfg.get(c, k + "_x"), Cfg.get(c, k + "_y"), 50L)
                if (sellIdx >= 3) {
                    phase = 5
                    Dbg.sells++
                    Dbg.add("Sold (#${Dbg.sells})")
                    Hook.send(applicationContext, "Sold items", "Sell #${Dbg.sells} after $catches catches", 0x38D39F)
                }
                h.postDelayed({ dnext() }, Cfg.get(c, "mdelay").toLong())
            }
        }
    }

    private fun shot(cb: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < 30) { cb(null); return }
        val wait = maxOf(0L, 380L - (SystemClock.uptimeMillis() - lastShot))
        h.postDelayed({
            lastShot = SystemClock.uptimeMillis()
            takeScreenshot(Display.DEFAULT_DISPLAY, bg, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                    val hb = r.hardwareBuffer
                    val hw = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)
                    val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                    hw?.recycle()
                    hb.close()
                    cb(bmp)
                }
                override fun onFailure(errorCode: Int) { cb(null) }
            })
        }, wait)
    }

    private fun track(cb: (DoubleArray?) -> Unit) {
        val t = SystemClock.uptimeMillis()
        val after = { r: DoubleArray? ->
            val now = SystemClock.uptimeMillis()
            Dbg.latency = (now - t).toInt()
            fpsN++
            if (now - fpsT >= 1000L) { Dbg.fps = fpsN; fpsN = 0; fpsT = now }
            cb(r)
        }
        val c = applicationContext
        val full = Cfg.get(c, "full") == 1
        if (Cap.active) {
            val dm = android.util.DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(dm)
            Cap.ensure(dm.widthPixels, dm.heightPixels, dm.densityDpi)
            screenW = dm.widthPixels
            if (full) {
                // once the bar is found, only look at a small box around it (faster and finer)
                val rb = roi
                if (phase == 2 && rb != null && roiN < 30) {
                    roiN++
                    val rs = if (dm.widthPixels >= 1600) 2 else 1
                    bg.execute {
                        val reg = Cap.region(rb[0], rb[1], rb[2] - rb[0], rb[3] - rb[1], rs)
                        val r = if (reg == null) null else roiScan(reg)
                        h.post {
                            if (r == null || r[0] < Cfg.get(c, "minpx")) {
                                roiMiss++
                                if (roiMiss >= 2) roi = null
                            } else {
                                roiMiss = 0
                            }
                            after(r)
                        }
                    }
                    return
                }
                val st = maxOf(2, dm.widthPixels / 500)
                bg.execute {
                    val reg = Cap.region(0, 0, 100000, 100000, st)
                    val res: DoubleArray? = if (reg == null) null else {
                        val cvr = if (Cv.ready && Cfg.get(c, "cv") == 1) {
                            Cv.scanFull(reg.px, reg.w, reg.h, reg.step, Cfg.get(c, "color"), Cfg.get(c, "color2"), Cfg.get(c, "tol"))
                        } else null
                        cvr ?: autoScan(reg.px, reg.w, reg.h, reg.step)
                    }
                    h.post {
                        if (phase == 2 && res != null && res[0] >= Cfg.get(c, "minpx") && res[9] > res[7]) {
                            roi = intArrayOf(res[6].toInt(), res[7].toInt() - 6, res[8].toInt(), res[9].toInt() + 6)
                            roiN = 0
                            roiMiss = 0
                        }
                        after(res)
                    }
                }
                return
            }
            val x1 = minOf(Cfg.get(c, "r1_x"), Cfg.get(c, "r2_x"))
            val x2 = maxOf(Cfg.get(c, "r1_x"), Cfg.get(c, "r2_x"))
            val y1 = minOf(Cfg.get(c, "r1_y"), Cfg.get(c, "r2_y"))
            val y2 = maxOf(Cfg.get(c, "r1_y"), Cfg.get(c, "r2_y"))
            bg.execute {
                val reg = Cap.region(x1, y1, x2 - x1, y2 - y1)
                val r = if (reg == null) null else scanPx(reg.px, reg.x, reg.y, reg.w, reg.h)
                h.post { after(r) }
            }
            return
        }
        shot { b ->
            val r = if (b == null) null else if (full) {
                val st = maxOf(2, b.width / 500)
                val sb = Bitmap.createScaledBitmap(b, maxOf(1, b.width / st), maxOf(1, b.height / st), false)
                val ar = IntArray(sb.width * sb.height)
                sb.getPixels(ar, 0, sb.width, 0, 0, sb.width, sb.height)
                val res = autoScan(ar, sb.width, sb.height, st)
                sb.recycle()
                b.recycle()
                res
            } else {
                scan(b).also { b.recycle() }
            }
            h.post { after(r) }
        }
    }

    // Fast scan of the small box around the bar found by the full scan (same output layout as autoScan).
    private fun roiScan(g: Reg): DoubleArray {
        val c = applicationContext
        val zc = Cfg.get(c, "color")
        val mk = Cfg.get(c, "color2")
        val tol = Cfg.get(c, "tol")
        val out = doubleArrayOf(0.0, 0.0, 0.0, 0.0, -1.0, 1.0,
            g.x.toDouble(), g.y.toDouble(), (g.x + g.w * g.step).toDouble(), (g.y + g.h * g.step).toDouble(), 0.0)
        val w = g.w
        val hh = g.h
        if (w < 4 || hh < 3) return out
        val ya = hh / 4
        val yb = maxOf(ya, hh - 1 - hh / 4)
        val rows = yb - ya + 1
        var cells = 0
        var zl = -1
        var zr = -1
        var zrun = 0
        var zStart = -1
        var tip = -1
        var bn = 0
        var brun = 0
        var bStart = -1
        for (xx in 0..w) {
            var zn = 0
            var bc = 0
            if (xx < w) {
                for (yy in ya..yb) {
                    val p = g.px[yy * w + xx]
                    if (near(p, zc, tol)) zn++
                    if (near(p, mk, tol)) bc++
                }
            }
            if (xx < w && zn * 2 >= rows) {
                if (zrun == 0) zStart = xx
                zrun++
                cells += zn
            } else {
                if (zrun >= 2) {
                    if (zl < 0) zl = zStart
                    zr = xx
                }
                zrun = 0
            }
            if (xx < w && bc * 2 >= rows) {
                if (brun == 0) bStart = xx
                brun++
            } else {
                if (brun >= 3 && bStart * g.step <= 0.03 * screenW) { tip = xx; bn = brun }
                brun = 0
            }
        }
        if (zl < 0) return out
        out[0] = cells.toDouble() * g.step * g.step
        out[1] = (g.x + zl * g.step).toDouble()
        out[2] = (g.x + zr * g.step).toDouble()
        out[3] = bn.toDouble()
        out[4] = if (tip >= 0) (g.x + tip * g.step).toDouble() else -1.0
        return out
    }

    private class Cand(var x0: Int, var x1: Int, var y0: Int, var y1: Int, var area: Int)

    // Whole-screen search for the reeling bar of Sol's RNG: a dark green zone (long and thin),
    // and the blue fill whose right end is the marker.
    // Returns [zonePx, zoneLeft, zoneRight, blueCols, tipX(-1 if none), 1, boxX1, boxY1, boxX2, boxY2, redSamples]
    private fun autoScan(px: IntArray, w: Int, h: Int, step: Int): DoubleArray {
        val c = applicationContext
        val out = doubleArrayOf(0.0, 0.0, 0.0, 0.0, -1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val zc = Cfg.get(c, "color")
        val mk = Cfg.get(c, "color2")
        val tol = Cfg.get(c, "tol")
        val red = 0xFFFF4041L.toInt()
        var redN = 0
        val cands = ArrayList<Cand>()
        var prev = ArrayList<Cand>()
        for (yy in 0 until h) {
            val cur = ArrayList<Cand>()
            val base = yy * w
            var xx = 0
            while (xx < w) {
                val p = px[base + xx]
                if (near(p, zc, tol)) {
                    var e = xx
                    while (e + 1 < w && near(px[base + e + 1], zc, tol)) e++
                    if (e - xx + 1 >= 3) {
                        var m: Cand? = null
                        for (q in prev) if (xx <= q.x1 && e >= q.x0) { m = q; break }
                        if (m != null) {
                            m.x0 = minOf(m.x0, xx)
                            m.x1 = maxOf(m.x1, e)
                            m.y1 = yy
                            m.area += e - xx + 1
                            if (!cur.contains(m)) cur.add(m)
                        } else {
                            val n = Cand(xx, e, yy, yy, e - xx + 1)
                            cands.add(n)
                            cur.add(n)
                        }
                    }
                    xx = e + 1
                } else {
                    if (near(p, red, 40)) redN++
                    xx++
                }
            }
            prev = cur
        }
        out[10] = redN.toDouble()
        val barL = w / 2.0 - 0.386 * h
        val barR = w / 2.0 + 0.386 * h
        var best: Cand? = null
        for (k in cands) {
            val cw = k.x1 - k.x0 + 1
            val ch = k.y1 - k.y0 + 1
            if (ch < 3 || cw < 2 * ch) continue
            if (ch < 0.025 * h || ch > 0.06 * h) continue
            if (cw < 0.02 * w || cw > w / 2) continue
            if (k.area < cw * ch / 2) continue
            val cx = (k.x0 + k.x1) / 2.0
            val cy = (k.y0 + k.y1) / 2.0
            if (cx < barL || cx > barR || cy < 0.4 * h || cy > 0.8 * h) continue
            if (best == null || k.area > best.area) best = k
        }
        val b = best ?: return out
        val wz = b.x1 - b.x0 + 1
        out[0] = b.area.toDouble() * step * step / 2.0
        out[1] = (b.x0 * step).toDouble()
        out[2] = ((b.x1 + 1) * step).toDouble()
        val ya = b.y0 + (b.y1 - b.y0) / 4
        val yb = b.y1 - (b.y1 - b.y0) / 4
        val xa = maxOf(0, barL.toInt() - 1)
        val xb = minOf(w - 1, b.x1 + wz)
        var tip = -1
        var bn = 0
        var run = 0
        var runStart = -1
        for (xx in xa..xb + 1) {
            var ok = false
            if (xx <= xb) {
                var cnt = 0
                for (yy in ya..yb) if (near(px[yy * w + xx], mk, tol)) cnt++
                ok = cnt * 2 >= (yb - ya + 1)
            }
            if (ok) {
                if (run == 0) runStart = xx
                run++
            } else {
                if (run >= 3 && (runStart - xa) <= 0.03 * w) { tip = xx; bn = run }
                run = 0
            }
        }
        out[3] = bn.toDouble()
        out[4] = if (tip >= 0) (tip * step).toDouble() else -1.0
        out[6] = (xa * step).toDouble()
        out[7] = ((b.y0 - 2) * step).toDouble()
        out[8] = ((xb + 1) * step).toDouble()
        out[9] = ((b.y1 + 3) * step).toDouble()
        return out
    }

    // One decision for the reeling bar. Each tap lifts the blue fill a fixed amount and the fill drains on its own,
    // so holding the marker still needs about drain/gain taps per second. The tap rate is that hold rate, raised when
    // the (predicted) marker is left of the zone middle and lowered to zero when it is past it.
    private fun reelFull(r: DoubleArray?) {
        val c = applicationContext
        val now = SystemClock.uptimeMillis()
        var gap = 0L
        if (r != null && r[0].toInt() >= Cfg.get(c, "minpx")) {
            lastSeen = now
            wZone = maxOf(wZone, r[2] - r[1])
            val zr = r[2]
            val zc = zr - wZone / 2.0
            // measured on a 2340 px wide screen: one tap lifts the fill about 58 px, it drains about 215 px/s
            val gain = 0.0248 * screenW
            val drain = 0.0919 * screenW
            val haveTip = r[4] >= 0
            var tip = r[4]
            if (!haveTip) tip = zc - 4 * gain
            tipHist[histN % 3] = tip
            histN++
            val m = if (histN >= 3) tipHist.copyOf().also { it.sort() }[1] else tip
            // marker speed (px/s), smoothed, and a look-ahead for capture and tap delay
            if (haveTip && lastM >= 0 && lastMT > 0 && now > lastMT) {
                val v = (m - lastM) * 1000.0 / (now - lastMT)
                vel = (0.6 * vel + 0.4 * v).coerceIn(-6 * drain, 6 * drain)
            } else if (!haveTip) {
                vel = 0.0
            }
            lastM = if (haveTip) m else -1.0
            lastMT = now
            val ahead = (Dbg.latency + 30).coerceIn(30, 160)
            val off = (vel * ahead / 1000.0).coerceIn(-2 * gain, 2 * gain)
            val en = (zc - (m + off)) / maxOf(wZone, 1.0)
            val fast = maxOf(Cfg.get(c, "tapms"), 40).toLong()
            val maxRate = 1000.0 / fast
            var rate = (drain / gain) * (1.0 + 2.5 * en)
            if (rate > maxRate) rate = maxRate
            if (rate < 0.0) rate = 0.0
            gap = if (rate < 0.5) 0L else (1000.0 / rate).toLong()
            if (gap > 0L && now - lastTap >= gap) {
                tap(Cfg.get(c, "reel_x"), Cfg.get(c, "reel_y"), 25L)
                lastTap = now
                tapCount++
            }
            framesN++
            if (m >= zr - wZone && m <= zr) framesIn++
            dbgCount++
            if (dbgCount % 20 == 0) Dbg.add("reel tip=${m.toInt()} zone=${(zr - wZone).toInt()}-${zr.toInt()} gap=$gap vel=${vel.toInt()}")
            if (Cfg.get(c, "debug") == 1) showDbg(r)
        }
        if (now - lastSeen > Cfg.get(c, "gonems").toLong() || now - t0 > 60000L) {
            val pct = 100 * framesIn / maxOf(framesN, 1)
            Dbg.inZone = pct
            Dbg.add("Reel done: marker in the zone $pct% of the time, $tapCount taps")
            phase = 7
            h.postDelayed({ dnext() }, Cfg.get(c, "postcatch").toLong())
        } else {
            h.postDelayed({ dnext() }, 10L)
        }
    }

    private fun scanPx(px: IntArray, x1: Int, y1: Int, w: Int, hh: Int): DoubleArray {
        val c = applicationContext
        val out = doubleArrayOf(0.0, 1e9, -1.0, 0.0, 0.0)
        val zc = Cfg.get(c, "color")
        val mk = Cfg.get(c, "color2")
        val tol = Cfg.get(c, "tol")
        val vert = Cfg.get(c, "axis") == 1
        var i = 0
        while (i < px.size) {
            val p = px[i]
            val pos = (if (vert) y1 + i / w else x1 + i % w).toDouble()
            if (near(p, zc, tol)) {
                out[0] += 1.0
                if (pos < out[1]) out[1] = pos
                if (pos > out[2]) out[2] = pos
            } else if (near(p, mk, tol)) {
                out[3] += 1.0
                out[4] += pos
            }
            i += 2
        }
        return out
    }

    private fun near(p: Int, t: Int, tol: Int): Boolean =
        Math.abs(((p shr 16) and 255) - ((t shr 16) and 255)) <= tol &&
        Math.abs(((p shr 8) and 255) - ((t shr 8) and 255)) <= tol &&
        Math.abs((p and 255) - (t and 255)) <= tol

    // returns [zoneCount, zoneMin, zoneMax, markerCount, markerSum] along the chosen axis
    private fun scan(b: Bitmap): DoubleArray {
        val c = applicationContext
        val out = doubleArrayOf(0.0, 1e9, -1.0, 0.0, 0.0)
        val x1 = minOf(Cfg.get(c, "r1_x"), Cfg.get(c, "r2_x")).coerceIn(0, b.width - 1)
        val x2 = maxOf(Cfg.get(c, "r1_x"), Cfg.get(c, "r2_x")).coerceIn(0, b.width)
        val y1 = minOf(Cfg.get(c, "r1_y"), Cfg.get(c, "r2_y")).coerceIn(0, b.height - 1)
        val y2 = maxOf(Cfg.get(c, "r1_y"), Cfg.get(c, "r2_y")).coerceIn(0, b.height)
        val w = x2 - x1
        val hh = y2 - y1
        if (w <= 0 || hh <= 0) return out
        val px = IntArray(w * hh)
        b.getPixels(px, 0, w, x1, y1, w, hh)
        val zc = Cfg.get(c, "color")
        val mk = Cfg.get(c, "color2")
        val tol = Cfg.get(c, "tol")
        val vert = Cfg.get(c, "axis") == 1
        var i = 0
        while (i < px.size) {
            val p = px[i]
            val pos = (if (vert) y1 + i / w else x1 + i % w).toDouble()
            if (near(p, zc, tol)) {
                out[0] += 1.0
                if (pos < out[1]) out[1] = pos
                if (pos > out[2]) out[2] = pos
            } else if (near(p, mk, tol)) {
                out[3] += 1.0
                out[4] += pos
            }
            i += 2
        }
        return out
    }

    private fun shotMain(cb: (Bitmap?) -> Unit) = shot { b -> h.post { cb(b) } }

    private fun detect(cb: (Int) -> Unit) {
        shot { b ->
            val n = if (b == null) 0 else count(b).also { b.recycle() }
            h.post { cb(n) }
        }
    }

    private fun doneCheck(): Boolean {
        val m = Cfg.get(applicationContext, "maxcatch")
        if (m > 0 && catches >= m) { stop(); toast("Reached $m catches, stopped"); return true }
        return false
    }

    private fun count(b: Bitmap): Int {
        val c = applicationContext
        val x1 = minOf(Cfg.get(c, "r1_x"), Cfg.get(c, "r2_x")).coerceIn(0, b.width - 1)
        val x2 = maxOf(Cfg.get(c, "r1_x"), Cfg.get(c, "r2_x")).coerceIn(0, b.width)
        val y1 = minOf(Cfg.get(c, "r1_y"), Cfg.get(c, "r2_y")).coerceIn(0, b.height - 1)
        val y2 = maxOf(Cfg.get(c, "r1_y"), Cfg.get(c, "r2_y")).coerceIn(0, b.height)
        val w = x2 - x1
        val hh = y2 - y1
        if (w <= 0 || hh <= 0) return 0
        val px = IntArray(w * hh)
        b.getPixels(px, 0, w, x1, y1, w, hh)
        val tc = Cfg.get(c, "color")
        val tr = (tc shr 16) and 255
        val tg = (tc shr 8) and 255
        val tb = tc and 255
        val tol = Cfg.get(c, "tol")
        var n = 0
        var i = 0
        while (i < px.size) {
            val p = px[i]
            if (Math.abs(((p shr 16) and 255) - tr) <= tol &&
                Math.abs(((p shr 8) and 255) - tg) <= tol &&
                Math.abs((p and 255) - tb) <= tol) n++
            i += 3
        }
        return n
    }

    fun test() {
        if (Build.VERSION.SDK_INT < 30) { toast("Detection needs Android 11 or newer"); return }
        track { r ->
            toast(if (r == null) "Could not capture the screen" else "Zone px: ${r[0].toInt()}, marker px: ${r[3].toInt()} (zone needs ${Cfg.get(applicationContext, "minpx")})")
            Dbg.add("TEST zone=${r?.get(0)?.toInt()} marker=${r?.get(3)?.toInt()}")
            showDbg(r)
            if (!running) h.postDelayed({ hideDebug() }, 4000L)
        }
    }

    // ---------- calibration on a frozen screenshot ----------
    private fun calibrate() {
        if (Build.VERSION.SDK_INT < 30) { toast("Detection needs Android 11 or newer"); return }
        toast("Start fishing now. The screen is captured in 8 seconds, so have the reeling bar showing.")
        h.postDelayed({ calibrateNow() }, 8000L)
    }

    private fun calibrateNow() {
        if (Build.VERSION.SDK_INT < 30) { toast("Detection needs Android 11 or newer"); return }
        shotMain { bmp ->
            if (bmp == null) { toast("Could not capture the screen"); return@shotMain }
            val c = applicationContext
            val box = FrameLayout(this)
            val img = ImageView(this).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.FIT_XY
            }
            box.addView(img, FrameLayout.LayoutParams(MATCH, MATCH))
            val prompts = listOf(
                "1/4: tap the TOP-LEFT corner of the reeling bar area",
                "2/4: tap the BOTTOM-RIGHT corner of the bar area",
                "3/4: tap the GREEN zone (its color is saved)",
                "4/4: tap the filled (blue) part of the bar (its color is saved)"
            )
            val first = if (Cfg.get(c, "full") == 1) 2 else 0
            val t = pill(prompts[first], 0xDD000000.toInt())
            box.addView(t, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = 60 })
            var step = first
            box.setOnTouchListener { v, e ->
                if (e.action == MotionEvent.ACTION_UP) {
                    val x = (e.x / v.width * bmp.width).toInt().coerceIn(0, bmp.width - 1)
                    val y = (e.y / v.height * bmp.height).toInt().coerceIn(0, bmp.height - 1)
                    when (step) {
                        0 -> { Cfg.put(c, "r1_x", x); Cfg.put(c, "r1_y", y) }
                        1 -> { Cfg.put(c, "r2_x", x); Cfg.put(c, "r2_y", y) }
                        2 -> Cfg.put(c, "color", bmp.getPixel(x, y))
                        else -> Cfg.put(c, "color2", bmp.getPixel(x, y))
                    }
                    step++
                    if (step >= 4) {
                        wm.removeView(box)
                        toast("Saved. Tap TEST while the bar is showing.")
                    } else {
                        t.text = prompts[step]
                    }
                }
                true
            }
            wm.addView(box, WL(MATCH, MATCH, WL.TYPE_ACCESSIBILITY_OVERLAY,
                WL.FLAG_LAYOUT_IN_SCREEN or WL.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT))
        }
    }

    // ---------- floating panel ----------
    private fun pill(t: String, color: Int) = TextView(this).apply {
        text = t
        setTextColor(Color.WHITE)
        textSize = 13f
        setPadding(34, 22, 34, 22)
        background = GradientDrawable().apply { setColor(color); cornerRadius = 60f }
    }

    fun refreshIcon() { loadIcon() }

    private fun loadIcon() {
        val v = iconView ?: return
        val f = java.io.File(filesDir, "float_icon.png")
        if (Cfg.get(applicationContext, "icon_custom") == 1 && f.exists()) {
            val b = android.graphics.BitmapFactory.decodeFile(f.absolutePath)
            if (b != null) { v.setImageBitmap(b); return }
        }
        v.setImageResource(R.drawable.brry_icon)
    }

    fun isRunning() = running
    fun isPaused() = paused

    fun openApp(capture: Boolean) {
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("capture", capture)
        try { startActivity(i) } catch (e: Exception) { Dbg.add("Could not open the app: " + e.message) }
    }

    fun openPanel() {
        if (panelUi != null) return
        val dm = resources.displayMetrics
        val wPx = minOf(dm.widthPixels - (24 * dm.density).toInt(), (760 * dm.density).toInt())
        val ui = PanelUi(this, wPx)
        val lp = WL(wPx, WRAP, WL.TYPE_ACCESSIBILITY_OVERLAY,
            WL.FLAG_NOT_FOCUSABLE or WL.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.CENTER
        }
        wm.addView(ui.root, lp)
        ui.root.alpha = 0f
        ui.root.scaleX = 0.96f
        ui.root.scaleY = 0.96f
        ui.root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
        ui.start()
        panelUi = ui
    }

    fun closePanel() {
        val ui = panelUi ?: return
        ui.stop()
        try { wm.removeView(ui.root) } catch (_: Exception) {}
        panelUi = null
    }

    private fun showPanel() {
        val d = resources.displayMetrics.density
        val size = (56 * d).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                setShape(GradientDrawable.OVAL)
                setColor(0xFF000000.toInt())
                setStroke((2 * d).toInt(), 0xFFFFFFFF.toInt())
            }
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            alpha = 0.92f
        }
        iconView = icon
        loadIcon()
        box.addView(icon, LinearLayout.LayoutParams(size, size))
        val lp = WL(WRAP, WRAP, WL.TYPE_ACCESSIBILITY_OVERLAY,
            WL.FLAG_NOT_FOCUSABLE or WL.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 120
        }
        drag(icon, box, lp) { if (panelUi == null) openPanel() else closePanel() }
        wm.addView(box, lp)
        panel = box
    }

    private fun drag(v: View, row: View, lp: WL, click: () -> Unit) {
        var dx = 0f
        var dy = 0f
        var sx = 0
        var sy = 0
        var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = lp.x; sy = lp.y; moved = false; v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start() }
                MotionEvent.ACTION_MOVE -> {
                    val mx = (e.rawX - dx).toInt()
                    val my = (e.rawY - dy).toInt()
                    if (Math.abs(mx) > 12 || Math.abs(my) > 12) moved = true
                    if (moved) { lp.x = sx + mx; lp.y = sy + my; wm.updateViewLayout(row, lp) }
                }
                MotionEvent.ACTION_UP -> { v.animate().scaleX(1f).scaleY(1f).setDuration(120).start(); if (!moved) click() }
            }
            true
        }
    }

    // ---------- tap position picker ----------
    fun setPoints() = pickAt(Cfg.keys, 0) {
        Cfg.put(applicationContext, "pts_ok", 1)
        toast("Positions saved")
    }

    fun setExtraPoints() = pickAt(Cfg.extra, 0) { toast("Extra points saved") }

    fun setBiomeRegion() = pickAt(Cfg.bioKeys, 0) {
        bioWarned = false
        toast("Biome area saved")
    }

    private fun pickAt(keys: List<String>, i: Int, done: () -> Unit) {
        if (i >= keys.size) { done(); return }
        val k = keys[i]
        val box = FrameLayout(this).apply { setBackgroundColor(0x99000000.toInt()) }
        val t = TextView(this).apply {
            text = "${i + 1}/${keys.size}: tap the ${Cfg.label[k] ?: Cfg.label2[k] ?: k}"
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
        }
        box.addView(t, FrameLayout.LayoutParams(MATCH, MATCH))
        box.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) {
                Cfg.put(applicationContext, k + "_x", e.rawX.toInt())
                Cfg.put(applicationContext, k + "_y", e.rawY.toInt())
                wm.removeView(box)
                pickAt(keys, i + 1, done)
            }
            true
        }
        wm.addView(box, WL(MATCH, MATCH, WL.TYPE_ACCESSIBILITY_OVERLAY,
            WL.FLAG_LAYOUT_IN_SCREEN or WL.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT))
    }

    // ---------- timed tasks (auto craft, buy, biome items: your own scripts) ----------
    private fun dueTask(): Int {
        val c = applicationContext
        val now = SystemClock.uptimeMillis()
        for (i in 0..2) {
            val k = "t" + (i + 1)
            if (Cfg.get(c, k + "on") != 1) continue
            val min = Cfg.get(c, k + "min")
            if (min <= 0 || Cfg.gets(c, k + "s").isBlank()) continue
            if (now - taskLast[i] >= min * 60000L) return i
        }
        return -1
    }

    // ---------- background services: anti-kick, screenshots, biome watch ----------
    private val biomeNames = listOf("windy", "rainy", "snowy", "sandstorm", "hell", "starfall", "corruption", "null", "glitched", "dreamspace", "cyberspace", "normal")
    private val rareBiomes = setOf("null", "glitched", "dreamspace", "cyberspace")

    private fun services() {
        val c = applicationContext
        val now = SystemClock.uptimeMillis()
        val inRoblox = foreground() == Cfg.ROBLOX
        // anti-kick: if nothing was tapped for a while (paused, long wait) nudge the camera a little
        if (running && inRoblox && Cfg.get(c, "afk") == 1) {
            val every = maxOf(Cfg.get(c, "afkmin"), 1) * 60000L
            if (now - lastGesture >= every) {
                lastGesture = now
                Dbg.add("Anti-kick: small camera move")
                camSwipe("right", 30)
                h.postDelayed({ camSwipe("left", 30) }, 700L)
            }
        }
        // periodic screenshot to the webhooks
        if (running && Cap.active && Cfg.get(c, "shoton") == 1) {
            val every = maxOf(Cfg.get(c, "shotmin"), 1) * 60000L
            if (now - lastShotSent >= every) {
                lastShotSent = now
                bg.execute {
                    val reg = Cap.region(0, 0, 100000, 100000, 2)
                    if (reg != null) {
                        val bmp = Bitmap.createBitmap(reg.px, reg.w, reg.h, Bitmap.Config.ARGB_8888)
                        val o = java.io.ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 70, o)
                        bmp.recycle()
                        Hook.sendImage(c, "Screenshot after " + catches + " catches", o.toByteArray())
                    }
                }
            }
        }
        // biome watch: read the biome name from the screen
        if (inRoblox && Cap.active && Cfg.get(c, "bio") == 1 && !bioBusy &&
            now - bioLast >= maxOf(Cfg.get(c, "biosec"), 2) * 1000L) {
            biomeWatch()
        }
    }

    private fun biomeWatch() {
        val c = applicationContext
        val x1 = minOf(Cfg.get(c, "bio1_x"), Cfg.get(c, "bio2_x"))
        val x2 = maxOf(Cfg.get(c, "bio1_x"), Cfg.get(c, "bio2_x"))
        val y1 = minOf(Cfg.get(c, "bio1_y"), Cfg.get(c, "bio2_y"))
        val y2 = maxOf(Cfg.get(c, "bio1_y"), Cfg.get(c, "bio2_y"))
        if (x2 - x1 < 20 || y2 - y1 < 8) {
            if (!bioWarned) {
                bioWarned = true
                Dbg.add("Biome watch: set the biome area first (Panel > Settings > Set biome area)")
            }
            return
        }
        bioLast = SystemClock.uptimeMillis()
        bioBusy = true
        bg.execute {
            try {
                val reg = Cap.region(x1, y1, x2 - x1, y2 - y1, 1)
                if (reg != null) {
                    val bmp = Bitmap.createBitmap(reg.px, reg.w, reg.h, Bitmap.Config.ARGB_8888)
                    val big = if (bmp.height < 80) Bitmap.createScaledBitmap(bmp, bmp.width * 2, bmp.height * 2, true) else bmp
                    val text = Ocr.read(big)
                    if (big !== bmp) big.recycle()
                    bmp.recycle()
                    if (text != null) {
                        val flat = text.lowercase().filter { it in 'a'..'z' }
                        val hit = biomeNames.firstOrNull { flat.contains(it) }
                        if (hit != null) {
                            if (hit == bioCur) {
                                bioPend = ""
                            } else if (hit == bioPend) {
                                val old = bioCur
                                bioCur = hit
                                bioPend = ""
                                Dbg.biome = hit.uppercase()
                                Dbg.add("Biome: " + hit.uppercase())
                                val desc = if (old.isEmpty()) "Current biome (read from the screen, may be wrong)"
                                    else "Changed from " + old.uppercase() + " (read from the screen, may be wrong)"
                                Hook.send(c, "Biome: " + hit.uppercase(), desc, if (hit in rareBiomes) 0xFFB84D else 0x3EA6FF)
                            } else {
                                bioPend = hit
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Dbg.add("Biome watch error: " + e.message)
            } finally {
                bioBusy = false
            }
        }
    }
}
