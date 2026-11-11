package com.solmacro

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

private const val WC = ViewGroup.LayoutParams.WRAP_CONTENT
private const val MC = ViewGroup.LayoutParams.MATCH_PARENT

class Glyph(ctx: Context, private val kind: Int) : View(ctx) {
    var color: Int = Color.WHITE
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        p.color = color
        p.style = Paint.Style.FILL
        p.strokeWidth = s * 0.1f
        p.strokeCap = Paint.Cap.ROUND
        when (kind) {
            PLAY -> {
                val t = Path()
                t.moveTo(cx - s * 0.22f, cy - s * 0.32f)
                t.lineTo(cx + s * 0.34f, cy)
                t.lineTo(cx - s * 0.22f, cy + s * 0.32f)
                t.close()
                c.drawPath(t, p)
            }
            BARS -> {
                c.drawRoundRect(RectF(cx - s * 0.38f, cy + s * 0.05f, cx - s * 0.18f, cy + s * 0.38f), 3f, 3f, p)
                c.drawRoundRect(RectF(cx - s * 0.1f, cy - s * 0.12f, cx + s * 0.1f, cy + s * 0.38f), 3f, 3f, p)
                c.drawRoundRect(RectF(cx + s * 0.18f, cy - s * 0.38f, cx + s * 0.38f, cy + s * 0.38f), 3f, 3f, p)
            }
            GEAR -> {
                p.style = Paint.Style.STROKE
                c.drawCircle(cx, cy, s * 0.2f, p)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val x1 = cx + (Math.cos(a) * s * 0.28).toFloat()
                    val y1 = cy + (Math.sin(a) * s * 0.28).toFloat()
                    val x2 = cx + (Math.cos(a) * s * 0.42).toFloat()
                    val y2 = cy + (Math.sin(a) * s * 0.42).toFloat()
                    c.drawLine(x1, y1, x2, y2, p)
                }
            }
            BOOK -> {
                c.drawRoundRect(RectF(cx - s * 0.4f, cy - s * 0.3f, cx - s * 0.03f, cy + s * 0.32f), 3f, 3f, p)
                c.drawRoundRect(RectF(cx + s * 0.03f, cy - s * 0.3f, cx + s * 0.4f, cy + s * 0.32f), 3f, 3f, p)
            }
            CLOCK -> {
                p.style = Paint.Style.STROKE
                c.drawCircle(cx, cy, s * 0.38f, p)
                c.drawLine(cx, cy, cx, cy - s * 0.22f, p)
                c.drawLine(cx, cy, cx + s * 0.16f, cy + s * 0.08f, p)
            }
            STAR -> {
                val t = Path()
                t.moveTo(cx, cy - s * 0.45f)
                t.quadTo(cx, cy, cx + s * 0.45f, cy)
                t.quadTo(cx, cy, cx, cy + s * 0.45f)
                t.quadTo(cx, cy, cx - s * 0.45f, cy)
                t.quadTo(cx, cy, cx, cy - s * 0.45f)
                t.close()
                c.drawPath(t, p)
            }
            BOLT -> {
                val t = Path()
                t.moveTo(cx + s * 0.1f, cy - s * 0.45f)
                t.lineTo(cx - s * 0.25f, cy + s * 0.05f)
                t.lineTo(cx, cy + s * 0.05f)
                t.lineTo(cx - s * 0.1f, cy + s * 0.45f)
                t.lineTo(cx + s * 0.25f, cy - s * 0.1f)
                t.lineTo(cx, cy - s * 0.1f)
                t.close()
                c.drawPath(t, p)
            }
            CHEV -> {
                p.style = Paint.Style.STROKE
                c.drawLine(cx - s * 0.12f, cy - s * 0.22f, cx + s * 0.12f, cy, p)
                c.drawLine(cx + s * 0.12f, cy, cx - s * 0.12f, cy + s * 0.22f, p)
            }
            else -> {
                p.style = Paint.Style.STROKE
                c.drawLine(cx - s * 0.25f, cy - s * 0.25f, cx + s * 0.25f, cy + s * 0.25f, p)
                c.drawLine(cx + s * 0.25f, cy - s * 0.25f, cx - s * 0.25f, cy + s * 0.25f, p)
            }
        }
    }

    companion object {
        const val PLAY = 0
        const val BARS = 1
        const val GEAR = 2
        const val BOOK = 3
        const val CLOCK = 4
        const val STAR = 5
        const val BOLT = 6
        const val CHEV = 7
        const val CLOSE = 8
    }
}

class Tog(ctx: Context, var on: Boolean, private val change: (Boolean) -> Unit) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setOnClickListener {
            on = !on
            change(on)
            invalidate()
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.color = if (on) 0xFFEDEDF0.toInt() else 0xFF3A3A42.toInt()
        c.drawRoundRect(RectF(0f, 0f, w, h), h / 2, h / 2, p)
        p.color = if (on) 0xFF111114.toInt() else Color.WHITE
        c.drawCircle(if (on) w - h / 2 else h / 2, h / 2, h * 0.38f, p)
    }
}

class PlayBtn(ctx: Context) : View(ctx) {
    // 0 = idle (shows play), 1 = running (shows stop)
    var mode = 0
        set(v) {
            if (field == v) return
            field = v
            animateMorph(v)
        }
    var paused = false

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tri = Path()
    private val spk = Path()
    private val argb = ArgbEvaluator()

    private var angle = 0f
    private var spd = 25f
    private var lastT = 0L
    private var tint = 0xFFEDEDF0.toInt()
    private var morph = 0f
    private var ripple = 0f
    private var press = 0f

    private var loop: ValueAnimator? = null
    private var morphAnim: ValueAnimator? = null
    private var rippleAnim: ValueAnimator? = null
    private var pressAnim: ValueAnimator? = null

    private fun withAlpha(c: Int, a: Int): Int =
        Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    private fun animateMorph(v: Int) {
        morphAnim?.cancel()
        val a = ValueAnimator.ofFloat(morph, if (v == 1) 1f else 0f)
        a.duration = 380
        a.interpolator = OvershootInterpolator(1.4f)
        a.addUpdateListener {
            morph = it.animatedValue as Float
            invalidate()
        }
        a.start()
        morphAnim = a
        startRipple()
    }

    private fun startRipple() {
        rippleAnim?.cancel()
        val a = ValueAnimator.ofFloat(0.001f, 1f)
        a.duration = 650
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener {
            ripple = it.animatedValue as Float
            if (ripple >= 1f) ripple = 0f
            invalidate()
        }
        a.start()
        rippleAnim = a
    }

    private fun pressTo(target: Float) {
        pressAnim?.cancel()
        val a = ValueAnimator.ofFloat(press, target)
        if (target > 0f) {
            a.duration = 110
            a.interpolator = DecelerateInterpolator()
        } else {
            a.duration = 420
            a.interpolator = OvershootInterpolator(3f)
        }
        a.addUpdateListener {
            press = it.animatedValue as Float
            invalidate()
        }
        a.start()
        pressAnim = a
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> pressTo(1f)
            MotionEvent.ACTION_UP -> pressTo(0f)
            MotionEvent.ACTION_CANCEL -> pressTo(0f)
        }
        return super.onTouchEvent(e)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // pop-in when the panel opens
        alpha = 0f
        scaleX = 0.7f
        scaleY = 0.7f
        animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(450)
            .setInterpolator(OvershootInterpolator(1.6f)).start()
        lastT = SystemClock.uptimeMillis()
        loop?.cancel()
        val l = ValueAnimator.ofFloat(0f, 1f)
        l.duration = 1000
        l.repeatCount = ValueAnimator.INFINITE
        l.interpolator = LinearInterpolator()
        l.addUpdateListener {
            val now = SystemClock.uptimeMillis()
            val dt = ((now - lastT).coerceIn(0L, 100L)) / 1000f
            lastT = now
            val targetSpd = if (mode == 1) (if (paused) 0f else 150f) else 25f
            spd += (targetSpd - spd) * minOf(1f, dt * 4f)
            angle = (angle + spd * dt) % 360f
            val targetCol = if (mode == 1) (if (paused) 0xFFFFB84D.toInt() else 0xFF38D39F.toInt()) else 0xFFEDEDF0.toInt()
            tint = argb.evaluate(minOf(1f, dt * 5f), tint, targetCol) as Int
            invalidate()
        }
        l.start()
        loop = l
    }

    override fun onDetachedFromWindow() {
        loop?.cancel()
        morphAnim?.cancel()
        rippleAnim?.cancel()
        pressAnim?.cancel()
        animate().cancel()
        super.onDetachedFromWindow()
    }

    private fun spark(c: Canvas, x: Float, y: Float, s: Float, a: Float) {
        spk.reset()
        spk.moveTo(x, y - s)
        spk.quadTo(x, y, x + s, y)
        spk.quadTo(x, y, x, y + s)
        spk.quadTo(x, y, x - s, y)
        spk.quadTo(x, y, x, y - s)
        spk.close()
        p.style = Paint.Style.FILL
        p.color = withAlpha(Color.WHITE, (255 * a).toInt())
        c.drawPath(spk, p)
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val base = minOf(width, height) * 0.30f
        val r = base * (1f - 0.10f * press)
        val now = SystemClock.uptimeMillis()
        val breath = ((Math.sin(now / 650.0) + 1.0) / 2.0).toFloat()
        val live = mode == 1 && !paused

        // soft breathing glow
        p.style = Paint.Style.FILL
        p.color = withAlpha(tint, (14 + 38 * breath).toInt())
        c.drawCircle(cx, cy, r * (1.3f + 0.14f * breath), p)

        // tilted ring with a small planet orbiting along it
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.04f
        p.color = 0x66FFFFFF
        c.save()
        c.rotate(-18f, cx, cy)
        val ra = r * 1.9f
        val rb = r * 0.62f
        c.drawOval(RectF(cx - ra, cy - rb, cx + ra, cy + rb), p)
        val t = Math.toRadians(angle.toDouble())
        val px = cx + (Math.cos(t) * ra).toFloat()
        val py = cy + (Math.sin(t) * rb).toFloat()
        p.style = Paint.Style.FILL
        p.color = withAlpha(tint, 70)
        c.drawCircle(px, py, r * 0.14f, p)
        p.color = Color.WHITE
        c.drawCircle(px, py, r * 0.075f, p)
        c.restore()

        // twinkling sparkles
        val a1 = ((Math.sin(now / 420.0) + 1.0) / 2.0).toFloat()
        val a2 = ((Math.sin(now / 530.0 + 2.0) + 1.0) / 2.0).toFloat()
        val a3 = ((Math.sin(now / 610.0 + 4.0) + 1.0) / 2.0).toFloat()
        spark(c, cx - r * 1.55f, cy - r * 0.95f, r * (0.10f + 0.10f * a1), 0.25f + 0.75f * a1)
        spark(c, cx + r * 1.5f, cy + r * 0.9f, r * (0.08f + 0.10f * a2), 0.25f + 0.75f * a2)
        spark(c, cx + r * 0.9f, cy - r * 1.3f, r * (0.06f + 0.08f * a3), 0.25f + 0.75f * a3)

        // faint outer circle
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.05f
        p.color = 0x33FFFFFF
        c.drawCircle(cx, cy, r * 1.18f, p)

        // spinning arc while running
        if (mode == 1) {
            p.strokeWidth = r * 0.10f
            p.strokeCap = Paint.Cap.ROUND
            p.color = withAlpha(tint, 230)
            val rr = r * 1.18f
            c.drawArc(RectF(cx - rr, cy - rr, cx + rr, cy + rr), angle * 2f, 80f, false, p)
            p.strokeCap = Paint.Cap.BUTT
        }

        // main circle
        p.strokeWidth = r * 0.12f
        p.color = Color.WHITE
        c.drawCircle(cx, cy, r, p)

        // tap ripple
        if (ripple > 0f) {
            val maxR = minOf(width, height) / 2f - 2f
            val rad = r * 1.1f + (maxR - r * 1.1f) * ripple
            p.strokeWidth = r * 0.08f * (1f - ripple) + 1f
            p.color = withAlpha(tint, (170 * (1f - ripple)).toInt())
            c.drawCircle(cx, cy, rad, p)
        }

        // icon: play morphs into stop
        p.style = Paint.Style.FILL
        if (morph < 0.999f) {
            c.save()
            c.rotate(90f * morph, cx, cy)
            val sc = 1f - 0.6f * morph
            c.scale(sc, sc, cx, cy)
            tri.reset()
            tri.moveTo(cx - r * 0.3f, cy - r * 0.45f)
            tri.lineTo(cx + r * 0.5f, cy)
            tri.lineTo(cx - r * 0.3f, cy + r * 0.45f)
            tri.close()
            p.color = withAlpha(Color.WHITE, (255 * (1f - morph)).toInt())
            c.drawPath(tri, p)
            c.restore()
        }
        if (morph > 0.001f) {
            c.save()
            c.rotate(-90f * (1f - morph), cx, cy)
            val sc = 0.4f + 0.6f * morph
            c.scale(sc, sc, cx, cy)
            p.color = withAlpha(Color.WHITE, (255 * morph).toInt())
            c.drawRoundRect(RectF(cx - r * 0.34f, cy - r * 0.34f, cx + r * 0.34f, cy + r * 0.34f), r * 0.08f, r * 0.08f, p)
            c.restore()
        }
    }
}

class PanelUi(private val svc: MacroService, widthPx: Int) {
    private val ctx: Context = svc
    private val d = ctx.resources.displayMetrics.density
    private val ui = Handler(Looper.getMainLooper())
    val root = ScrollView(ctx)
    private val narrow = widthPx < (620 * d)
    private val pages = ArrayList<View>()
    private val navRows = ArrayList<LinearLayout>()
    private val navGlyphs = ArrayList<Glyph>()
    private val navTexts = ArrayList<TextView>()
    private var hint: ObjectAnimator? = null
    private var noticeUntil = 0L

    private lateinit var pill: TextView
    private lateinit var dot: View
    private lateinit var play: PlayBtn
    private lateinit var tapT: TextView
    private lateinit var tapS: TextView
    private lateinit var pauseB: TextView
    private lateinit var vCatch: TextView
    private lateinit var vTime: TextView
    private lateinit var vState: TextView
    private lateinit var statsText: TextView
    private lateinit var capVal: TextView
    private lateinit var tapVal: TextView

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            ui.postDelayed(this, 500)
        }
    }

    private val muted = 0xFF9A9AA5.toInt()
    private val cardBg = 0xFF121216.toInt()
    private val line = 0x26FFFFFF

    private fun dp(v: Int) = (v * d).toInt()
    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    private fun lin(o: Int) = LinearLayout(ctx).apply { orientation = o }

    private fun shape(fill: Int, r: Int, stroke: Int = 0) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(r).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun tv(t: String, sp: Float, color: Int = Color.WHITE, bold: Boolean = false) = TextView(ctx).apply {
        text = t
        textSize = sp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    init {
        val col = lin(LinearLayout.VERTICAL)
        col.background = shape(0xFF060608.toInt(), 24, 0x44FFFFFF)
        col.setPadding(dp(12), dp(8), dp(12), dp(12))
        col.addView(header())
        val body = lin(LinearLayout.HORIZONTAL)
        body.addView(sidebar(), lp(dp(108), WC))
        val center = FrameLayout(ctx)
        pages.add(pageMacro())
        pages.add(pageStats())
        pages.add(pageSettings())
        pages.add(pageCredits())
        for (p in pages) center.addView(p, FrameLayout.LayoutParams(MC, MC))
        body.addView(center, lp(0, WC, 1f).apply { leftMargin = dp(8) })
        if (!narrow) body.addView(rightColumn(), lp(dp(190), WC).apply { leftMargin = dp(8) })
        col.addView(body, lp(MC, WC).apply { topMargin = dp(4) })
        col.addView(statsBar(), lp(MC, WC).apply { topMargin = dp(8) })
        root.addView(col)
        showPage(0)
        refresh()
    }

    fun start() { ui.post(tick) }

    fun stop() {
        ui.removeCallbacks(tick)
        hint?.cancel()
        hint = null
    }

    private fun loadLogo(v: ImageView) {
        val f = File(ctx.filesDir, "float_icon.png")
        if (Cfg.get(ctx, "icon_custom") == 1 && f.exists()) {
            val b = BitmapFactory.decodeFile(f.absolutePath)
            if (b != null) { v.setImageBitmap(b); return }
        }
        v.setImageResource(R.drawable.brry_icon)
    }

    private fun circleBtn(kind: Int, click: () -> Unit): View {
        val f = FrameLayout(ctx)
        f.background = shape(0xFF141418.toInt(), 21, 0x33FFFFFF)
        f.addView(Glyph(ctx, kind), FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        f.setOnClickListener { click() }
        return f
    }

    private fun header(): View {
        val h = lin(LinearLayout.HORIZONTAL)
        h.gravity = Gravity.CENTER_VERTICAL
        h.setPadding(dp(4), dp(4), dp(4), dp(8))
        val logo = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = shape(0xFF000000.toInt(), 12, 0x55FFFFFF)
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        loadLogo(logo)
        h.addView(logo, lp(dp(44), dp(44)))
        val sep = View(ctx)
        sep.setBackgroundColor(0x33FFFFFF)
        h.addView(sep, lp(dp(1), dp(32)).apply { leftMargin = dp(12); rightMargin = dp(12) })
        val titles = lin(LinearLayout.VERTICAL)
        val name = tv("Brry", 24f, Color.WHITE, true)
        name.setTypeface(name.typeface, Typeface.BOLD_ITALIC)
        titles.addView(name)
        titles.addView(tv("Sol’s RNG Macro", 12f, muted))
        h.addView(titles, lp(0, WC, 1f))
        val pb = lin(LinearLayout.HORIZONTAL)
        pb.gravity = Gravity.CENTER_VERTICAL
        pb.setPadding(dp(14), dp(7), dp(14), dp(7))
        pb.background = shape(0xFF0F2A1C.toInt(), 20, 0x5538D39F)
        dot = View(ctx)
        pb.addView(dot, lp(dp(10), dp(10)))
        pill = tv("Ready", 14f, Color.WHITE, true)
        pb.addView(pill, lp(WC, WC).apply { leftMargin = dp(8) })
        h.addView(pb)
        h.addView(circleBtn(Glyph.GEAR) { showPage(2) }, lp(dp(42), dp(42)).apply { leftMargin = dp(8) })
        h.addView(circleBtn(Glyph.CLOSE) { svc.closePanel() }, lp(dp(42), dp(42)).apply { leftMargin = dp(8) })
        return h
    }

    private fun sidebar(): View {
        val col = lin(LinearLayout.VERTICAL)
        val names = listOf("Macro", "Stats", "Settings", "Credits")
        val kinds = listOf(Glyph.PLAY, Glyph.BARS, Glyph.GEAR, Glyph.BOOK)
        for (i in names.indices) {
            val row = lin(LinearLayout.HORIZONTAL)
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(10), dp(9), dp(10), dp(9))
            val g = Glyph(ctx, kinds[i])
            row.addView(g, lp(dp(20), dp(20)))
            val t = tv(names[i], 14f)
            row.addView(t, lp(WC, WC).apply { leftMargin = dp(8) })
            row.setOnClickListener { showPage(i) }
            col.addView(row, lp(MC, WC).apply { topMargin = dp(4) })
            navRows.add(row)
            navGlyphs.add(g)
            navTexts.add(t)
        }
        val ver = lin(LinearLayout.VERTICAL)
        ver.background = shape(cardBg, 14, line)
        ver.setPadding(dp(10), dp(8), dp(10), dp(8))
        ver.addView(tv("Brry v1.2", 12f))
        ver.addView(tv("Made for Sol’s RNG", 10f, muted))
        col.addView(ver, lp(MC, WC).apply { topMargin = dp(10) })
        return col
    }

    private fun showPage(i: Int) {
        for (k in pages.indices) pages[k].visibility = if (k == i) View.VISIBLE else View.GONE
        for (k in navRows.indices) {
            val sel = k == i
            navRows[k].background = if (sel) shape(0xFFEDEDF0.toInt(), 18) else null
            val c = if (sel) 0xFF0A0A0C.toInt() else Color.WHITE
            navGlyphs[k].color = c
            navGlyphs[k].invalidate()
            navTexts[k].setTextColor(c)
        }
        if (i == 0 || i == 1) refresh()
    }

    private fun card(title: String?, kind: Int = Glyph.STAR): LinearLayout {
        val c = lin(LinearLayout.VERTICAL)
        c.background = shape(0xFF0E0E11.toInt(), 18, line)
        c.setPadding(dp(10), dp(8), dp(10), dp(10))
        if (title != null) {
            val t = lin(LinearLayout.HORIZONTAL)
            t.gravity = Gravity.CENTER
            t.background = shape(0xFF17171B.toInt(), 12)
            t.setPadding(dp(8), dp(6), dp(8), dp(6))
            t.addView(Glyph(ctx, kind), lp(dp(18), dp(18)))
            t.addView(tv(title, 15f, Color.WHITE, true), lp(WC, WC).apply { leftMargin = dp(8) })
            c.addView(t, lp(MC, WC))
        }
        return c
    }

    private fun pageMacro(): View {
        val c = card("Auto Fish")
        c.gravity = Gravity.CENTER_HORIZONTAL
        play = PlayBtn(ctx)
        play.setOnClickListener {
            if (!svc.isRunning() && Cfg.get(ctx, "pts_ok") != 1) {
                needSet()
            } else {
                svc.toggle()
                ui.postDelayed({ refresh() }, 80)
            }
        }
        c.addView(play, lp(dp(170), dp(120)).apply { topMargin = dp(2) })
        tapT = tv("Tap to Start", 18f, Color.WHITE, true)
        c.addView(tapT, lp(WC, WC))
        tapS = tv("Let Brry do the work…", 12f, muted)
        c.addView(tapS, lp(WC, WC))
        pauseB = tv("Pause", 13f, Color.WHITE, true)
        pauseB.setPadding(dp(18), dp(6), dp(18), dp(6))
        pauseB.background = shape(0xFF1E1E24.toInt(), 14, line)
        pauseB.visibility = View.GONE
        pauseB.setOnClickListener { svc.togglePause() }
        c.addView(pauseB, lp(WC, WC).apply { topMargin = dp(6) })
        return c
    }

    private fun pageStats(): View {
        val c = card("Stats", Glyph.BARS)
        statsText = tv("", 13f, Color.WHITE)
        statsText.typeface = Typeface.MONOSPACE
        c.addView(statsText, lp(MC, WC).apply { topMargin = dp(8) })
        return c
    }

    private fun actionRow(label: String, click: () -> Unit): View {
        val r = lin(LinearLayout.HORIZONTAL)
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = shape(0xFF17171B.toInt(), 12, line)
        r.setPadding(dp(12), dp(10), dp(12), dp(10))
        r.addView(tv(label, 14f), lp(0, WC, 1f))
        r.addView(Glyph(ctx, Glyph.CHEV), lp(dp(18), dp(18)))
        r.setOnClickListener { click() }
        return r
    }

    private fun pageSettings(): View {
        val c = card("Settings", Glyph.GEAR)
        c.addView(actionRow("Set button positions") { svc.closePanel(); svc.setPoints() }, lp(MC, WC).apply { topMargin = dp(8) })
        c.addView(actionRow("Set biome area (for biome watch)") { svc.closePanel(); svc.setBiomeRegion() }, lp(MC, WC).apply { topMargin = dp(6) })
        c.addView(actionRow("Set extra points P1-P6 (for timed tasks)") { svc.closePanel(); svc.setExtraPoints() }, lp(MC, WC).apply { topMargin = dp(6) })
        c.addView(actionRow("Test detection") { svc.test() }, lp(MC, WC).apply { topMargin = dp(6) })
        c.addView(actionRow("Start fast capture") { svc.openApp(true) }, lp(MC, WC).apply { topMargin = dp(6) })
        c.addView(actionRow("Open app settings") { svc.openApp(false) }, lp(MC, WC).apply { topMargin = dp(6) })
        return c
    }

    private fun pageCredits(): View {
        val c = card("Credits", Glyph.BOOK)
        c.addView(tv("Brry v1.2\nMade for Sol’s RNG.\n\nNot affiliated with Roblox. Macros can break game rules and get accounts banned.", 13f, muted), lp(MC, WC).apply { topMargin = dp(10) })
        return c
    }

    private fun row(c: LinearLayout, label: String, right: View, rightW: Int = 46) {
        val r = lin(LinearLayout.HORIZONTAL)
        r.gravity = Gravity.CENTER_VERTICAL
        r.addView(tv(label, 14f), lp(0, WC, 1f))
        r.addView(right, lp(dp(rightW), dp(26)))
        c.addView(r, lp(MC, WC).apply { topMargin = dp(8) })
    }

    private fun rightColumn(): View {
        val col = lin(LinearLayout.VERTICAL)
        val a = card("Macro Settings", Glyph.BOLT)
        row(a, "Auto Sell", Tog(ctx, Cfg.get(ctx, "sell") == 1) { Cfg.put(ctx, "sell", if (it) 1 else 0) })
        row(a, "OpenCV detect", Tog(ctx, Cfg.get(ctx, "cv") == 1) { Cfg.put(ctx, "cv", if (it) 1 else 0) })
        row(a, "Anti-AFK", Tog(ctx, Cfg.get(ctx, "afk") == 1) { Cfg.put(ctx, "afk", if (it) 1 else 0) })
        row(a, "Biome watch", Tog(ctx, Cfg.get(ctx, "bio") == 1) { Cfg.put(ctx, "bio", if (it) 1 else 0) })
        row(a, "Screenshots", Tog(ctx, Cfg.get(ctx, "shoton") == 1) { Cfg.put(ctx, "shoton", if (it) 1 else 0) })
        row(a, "Debug overlay", Tog(ctx, Cfg.get(ctx, "debug") == 1) { Cfg.put(ctx, "debug", if (it) 1 else 0) })
        tapVal = tv("", 12f, Color.WHITE)
        tapVal.gravity = Gravity.CENTER
        tapVal.background = shape(0xFF1E1E24.toInt(), 13, line)
        tapVal.setOnClickListener {
            val opts = intArrayOf(50, 70, 90, 120)
            val cur = Cfg.get(ctx, "tapms")
            var nxt = opts[0]
            for (k in opts.indices) if (opts[k] == cur && k + 1 < opts.size) nxt = opts[k + 1]
            Cfg.put(ctx, "tapms", nxt)
            refresh()
        }
        row(a, "Tap delay", tapVal, 70)
        col.addView(a, lp(MC, WC))
        val b = card("Capture", Glyph.STAR)
        capVal = tv("", 12f, Color.WHITE)
        capVal.gravity = Gravity.CENTER
        capVal.background = shape(0xFF1E1E24.toInt(), 13, line)
        capVal.setOnClickListener { if (!Cap.active) svc.openApp(true) }
        row(b, "Fast capture", capVal, 70)
        col.addView(b, lp(MC, WC).apply { topMargin = dp(8) })
        return col
    }

    private fun seg(kind: Int, label: String, value: TextView): View {
        val s = lin(LinearLayout.HORIZONTAL)
        s.gravity = Gravity.CENTER_VERTICAL
        s.addView(Glyph(ctx, kind), lp(dp(22), dp(22)))
        val t = lin(LinearLayout.VERTICAL)
        t.addView(tv(label, 11f, muted))
        t.addView(value)
        s.addView(t, lp(WC, WC).apply { leftMargin = dp(8) })
        return s
    }

    private fun statsBar(): View {
        val bar = lin(LinearLayout.HORIZONTAL)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.background = shape(0xFF0E0E11.toInt(), 16, line)
        bar.setPadding(dp(12), dp(8), dp(12), dp(8))
        vCatch = tv("0", 16f, Color.WHITE, true)
        vTime = tv("00:00:00", 16f, Color.WHITE, true)
        vState = tv("Idle", 13f, Color.WHITE, true)
        bar.addView(seg(Glyph.BARS, "Total Catches", vCatch), lp(0, WC, 1f))
        bar.addView(seg(Glyph.CLOCK, "Runtime", vTime), lp(0, WC, 1f))
        bar.addView(seg(Glyph.STAR, "Status", vState), lp(0, WC, 1f))
        return bar
    }

    // Start was tapped before the button positions were set: warn, shake the button, jump to Settings.
    private fun needSet() {
        svc.needPoints()
        val s = dp(10).toFloat()
        ObjectAnimator.ofFloat(play, "translationX", 0f, -s, s, -s * 0.8f, s * 0.8f, -s * 0.4f, 0f).apply {
            duration = 400
            start()
        }
        tapS.text = "Set the button positions first"
        tapS.setTextColor(0xFFFFB84D.toInt())
        tapS.alpha = 1f
        noticeUntil = SystemClock.uptimeMillis() + 3500L
        showPage(2)
    }

    private fun refresh() {
        val running = svc.isRunning()
        val paused = svc.isPaused()
        val col = if (paused) 0xFFFFB84D.toInt() else 0xFF38D39F.toInt()
        dot.background = shape(col, 6)
        pill.text = if (paused) "Paused" else if (running) "Running" else "Ready"
        val tt = if (running) "Tap to Stop" else "Tap to Start"
        if (tapT.text.toString() != tt) {
            tapT.text = tt
            tapT.translationY = dp(10).toFloat()
            tapT.animate().translationY(0f).setDuration(260).setInterpolator(OvershootInterpolator(2f)).start()
        }
        if (SystemClock.uptimeMillis() >= noticeUntil) {
            if (noticeUntil != 0L) {
                noticeUntil = 0L
                tapS.setTextColor(muted)
            }
            val ts = if (running) Dbg.state else "Let Brry do the work…"
            if (tapS.text.toString() != ts) {
                tapS.text = ts
                tapS.alpha = 0f
                tapS.animate().alpha(1f).setDuration(220).start()
            }
        }
        play.paused = paused
        play.mode = if (running) 1 else 0
        play.invalidate()
        pauseB.visibility = if (running) View.VISIBLE else View.GONE
        pauseB.text = if (paused) "Resume" else "Pause"
        if (!running && hint == null) {
            hint = ObjectAnimator.ofFloat(tapT, "alpha", 1f, 0.45f).apply {
                duration = 900
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                start()
            }
        } else if (running && hint != null) {
            hint?.cancel()
            hint = null
            tapT.alpha = 1f
        }
        vCatch.text = Dbg.catches.toString()
        val sec = if (Dbg.startMs > 0 && running) (android.os.SystemClock.uptimeMillis() - Dbg.startMs) / 1000 else 0L
        vTime.text = String.format("%02d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60)
        vState.text = if (running) Dbg.state else "Idle"
        statsText.text = "Catches   ${Dbg.catches}\nFailed    ${Dbg.fails}\nIn zone   ${Dbg.inZone}%\nSells     ${Dbg.sells}\nAvg bite  ${Dbg.biteMs} ms\nChecks/s  ${Dbg.fps}\nLatency   ${Dbg.latency} ms\nCapture   ${if (Cap.active) "fast" else "off"}\nBiome     ${Dbg.biome.ifEmpty { "-" }}"
        if (::capVal.isInitialized) capVal.text = if (Cap.active) "On" else "Off, tap"
        if (::tapVal.isInitialized) tapVal.text = "${Cfg.get(ctx, "tapms")} ms"
    }
}
