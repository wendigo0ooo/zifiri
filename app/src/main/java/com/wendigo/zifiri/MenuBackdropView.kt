package com.wendigo.zifiri

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class MenuBackdropView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private var time = 0f

    private class Ember(var x: Float, var y: Float, var speed: Float, var size: Float, var phase: Float, var teal: Boolean)

    private val embers = ArrayList<Ember>()

    init {
        for (i in 0 until 54) {
            embers.add(
                Ember(
                    (Math.random()).toFloat(),
                    (Math.random()).toFloat(),
                    0.008f + (Math.random() * 0.022).toFloat(),
                    0.6f + (Math.random() * 2.2).toFloat(),
                    (Math.random() * 6.28).toFloat(),
                    Math.random() < 0.18
                )
            )
        }
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAttachedToWindow) return
            time = (SystemClock.uptimeMillis() % 1_000_000L) / 1000f
            updateEmbers()
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }

    private fun updateEmbers() {
        for (e in embers) {
            e.y -= e.speed
            e.x += sin(time * 0.6f + e.phase) * 0.00035f
            if (e.y < -0.04f) {
                e.y = 1.04f
                e.x = (Math.random()).toFloat()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        fill.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(0xFF0A0D13.toInt(), 0xFF07090C.toInt(), 0xFF040507.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, fill)
        fill.shader = null

        // Distant arches.
        val baseY = h * 0.78f
        val archW = w * 0.30f
        val archH = h * 0.50f
        for (i in -1..1) {
            val cx = w / 2f + i * w * 0.36f
            path.reset()
            path.moveTo(cx - archW / 2f, baseY)
            path.lineTo(cx - archW / 2f, baseY - archH * 0.55f)
            path.quadTo(cx - archW / 2f, baseY - archH, cx, baseY - archH)
            path.quadTo(cx + archW / 2f, baseY - archH, cx + archW / 2f, baseY - archH * 0.55f)
            path.lineTo(cx + archW / 2f, baseY)
            line.color = 0xFF10161D.toInt()
            line.strokeWidth = dp(18f)
            canvas.drawPath(path, line)
            line.color = Art.alpha(0xFF31404C.toInt(), if (i == 0) 90 else 50)
            line.strokeWidth = max(1f, dp(1f))
            canvas.drawPath(path, line)
        }
        fill.color = 0xFF07090C.toInt()
        canvas.drawRect(0f, baseY, w, h, fill)
        line.color = Art.alpha(Palette.AMBER_DIM, 55)
        line.strokeWidth = max(1f, dp(1f))
        canvas.drawLine(0f, baseY, w, baseY, line)

        // Faint seal behind the title.
        val scx = w / 2f
        val scy = h * 0.36f
        val sr = min(w, h) * 0.24f
        line.color = Art.alpha(Palette.AMBER, 18)
        line.strokeWidth = max(1f, dp(1.2f))
        canvas.drawCircle(scx, scy, sr, line)
        canvas.drawCircle(scx, scy, sr * 0.82f, line)
        for (i in 0 until 14) {
            val a = time * 0.05f + i * (Math.PI * 2 / 14)
            val r0 = sr * 0.86f
            val r1 = sr * (if (i % 3 == 0) 0.96f else 0.91f)
            line.color = Art.alpha(Palette.AMBER, if (i % 3 == 0) 26 else 14)
            canvas.drawLine(
                scx + cos(a).toFloat() * r0, scy + sin(a).toFloat() * r0,
                scx + cos(a).toFloat() * r1, scy + sin(a).toFloat() * r1, line
            )
        }

        // Fog.
        for (i in 0..4) {
            val yy = h * (0.52f + i * 0.075f) + sin(time * 0.24f + i) * dp(7f)
            val rw = w * (0.55f + i * 0.10f)
            fill.shader = RadialGradient(
                w / 2f + sin(time * 0.18f + i * 1.7f) * w * 0.10f, yy, rw,
                intArrayOf(Art.alpha(if (i % 2 == 0) 0xFF3E5A5E.toInt() else 0xFF4A4658.toInt(), 15), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawOval(
                w / 2f - rw, yy - rw * 0.16f, w / 2f + rw, yy + rw * 0.16f, fill
            )
        }
        fill.shader = null

        // Embers.
        for (e in embers) {
            val flicker = 0.55f + 0.45f * sin(time * 2.2f + e.phase)
            val col = if (e.teal) Palette.TEAL else Palette.AMBER_HI
            fill.color = Art.alpha(col, (110 * flicker).toInt())
            val px = e.x * w
            val py = e.y * h
            canvas.drawCircle(px, py, dp(e.size), fill)
            fill.color = Art.alpha(col, (26 * flicker).toInt())
            canvas.drawCircle(px, py, dp(e.size * 3.4f), fill)
        }

        // Vignette.
        fill.shader = RadialGradient(
            w / 2f, h * 0.46f, max(w, h) * 0.72f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, 0xCC000000.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
    }
}
