package com.wendigo.zifiri

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object Art {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val rect = RectF()

    fun alpha(color: Int, a: Int): Int = Color.argb(a.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    fun shade(color: Int, f: Float): Int {
        val r = (Color.red(color) * f).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * f).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * f).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
    fun mix(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * tt).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * tt).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * tt).toInt()
        )
    }
    fun lighten(c: Int, f: Float): Int = mix(c, Color.WHITE, f)
    fun darken(c: Int, f: Float): Int = mix(c, Color.BLACK, f)

    private fun fill(c: Canvas, color: Int) {
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = color
    }
    private fun stroke(c: Canvas, color: Int, width: Float) {
        line.style = Paint.Style.STROKE
        line.shader = null
        line.color = color
        line.strokeWidth = width
        line.strokeCap = Paint.Cap.ROUND
    }

    private fun hash(x: Int, y: Int, salt: Int = 0): Float {
        var h = x * 374761393 + y * 668265263 + salt * 1274126177
        h = (h xor (h shr 13)) * 1274126177
        return ((h and 0x7FFFFFFF) % 1000) / 1000f
    }

    // ------------------------------------------------------------------
    // Tiles
    // ------------------------------------------------------------------

    fun drawFloor(c: Canvas, cx: Float, cy: Float, s: Float, depth: Int, vx: Int, vy: Int, time: Float, lit: Float) {
        val base = Biome.floorColor(depth, vx * 3 + vy)
        fill(c, base)
        c.drawRect(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2, p)
        val h = hash(vx, vy, depth)
        val h2 = hash(vx, vy, depth + 7)
        // Subtle stone seams.
        stroke(c, alpha(darken(base, 0.72f), 90), max(1f, s * 0.018f))
        if (h < 0.32f) c.drawLine(cx - s / 2, cy + (h2 - 0.5f) * s * 0.7f, cx + s / 2, cy + (h2 - 0.5f) * s * 0.7f, line)
        if (h > 0.72f) c.drawLine(cx + (h2 - 0.5f) * s * 0.7f, cy - s / 2, cx + (h2 - 0.5f) * s * 0.7f, cy + s / 2, line)
        // Moss / cracks / specks.
        when (Biome.forDepth(depth)) {
            Biome.SARNIC -> {
                if (h < 0.45f) {
                    fill(c, alpha(0xFF4F7A5F.toInt(), (40 + 50 * lit).toInt()))
                    c.drawCircle(cx + (h - 0.5f) * s * 0.55f, cy + (h2 - 0.5f) * s * 0.55f, s * (0.10f + h * 0.08f), p)
                }
            }
            Biome.KRISTAL -> {
                if (h > 0.78f) {
                    fill(c, alpha(0xFF8D7FE0.toInt(), (50 * lit).toInt()))
                    c.drawCircle(cx + (h - 0.5f) * s * 0.5f, cy + (h2 - 0.5f) * s * 0.5f, s * 0.07f, p)
                }
            }
            Biome.KUL -> {
                if (h > 0.55f) {
                    fill(c, alpha(0xFF7E5E48.toInt(), (50 * lit).toInt()))
                    c.drawCircle(cx + (h - 0.5f) * s * 0.6f, cy + (h2 - 0.5f) * s * 0.6f, s * 0.12f, p)
                }
            }
            Biome.MUHUR -> {
                if (h > 0.70f) {
                    stroke(c, alpha(0xFFC9A95A.toInt(), (50 * lit).toInt()), max(1f, s * 0.02f))
                    c.drawCircle(cx + (h - 0.5f) * s * 0.4f, cy + (h2 - 0.5f) * s * 0.4f, s * 0.13f, line)
                }
            }
            else -> {
                if (h > 0.80f) {
                    fill(c, alpha(0xFF6E3B4A.toInt(), (35 * lit).toInt()))
                    c.drawCircle(cx + (h - 0.5f) * s * 0.5f, cy + (h2 - 0.5f) * s * 0.5f, s * 0.10f, p)
                }
            }
        }
    }

    fun drawWall(c: Canvas, cx: Float, cy: Float, s: Float, depth: Int, vx: Int, vy: Int, lit: Float) {
        val base = Biome.wallColor(depth, vx * 5 + vy)
        val top = lighten(base, 0.16f)
        val side = darken(base, 0.42f)
        val h = s * 0.30f
        // Front face.
        fill(c, side)
        c.drawRect(cx - s / 2, cy - s / 2 + h, cx + s / 2, cy + s / 2, p)
        // Top face.
        fill(c, top)
        c.drawRect(cx - s / 2, cy - s / 2, cx + s / 2, cy - s / 2 + h, p)
        // Top edge highlight.
        stroke(c, alpha(lighten(base, 0.35f), (80 + 80 * lit).toInt()), max(1f, s * 0.025f))
        c.drawLine(cx - s / 2, cy - s / 2 + h * 0.15f, cx + s / 2, cy - s / 2 + h * 0.15f, line)
        // Brick seams.
        stroke(c, alpha(darken(base, 0.7f), 150), max(1f, s * 0.018f))
        val hh = hash(vx, vy, depth)
        val seamY = cy - s / 2 + h * (0.55f + 0.2f * hh)
        c.drawLine(cx - s / 2, seamY, cx + s / 2, seamY, line)
        if (hh > 0.5f) {
            c.drawLine(cx, cy - s / 2, cx, seamY, line)
        } else {
            c.drawLine(cx - s * 0.2f, seamY, cx - s * 0.2f, cy + s / 2, line)
        }
        // Deep bottom shadow.
        fill(c, alpha(0xFF000000.toInt(), (55 + 60 * lit).toInt()))
        c.drawRect(cx - s / 2, cy + s / 2 - s * 0.10f, cx + s / 2, cy + s / 2, p)
    }

    fun drawWater(c: Canvas, cx: Float, cy: Float, s: Float, depth: Int, vx: Int, vy: Int, time: Float, lit: Float) {
        val base = if (Biome.forDepth(depth) == Biome.KRISTAL) 0xFF233A52.toInt() else 0xFF183A3A.toInt()
        fill(c, base)
        c.drawRect(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2, p)
        val ph = hash(vx, vy, 31) * 6.28f
        stroke(c, alpha(0xFF6FAEB8.toInt(), (55 + 55 * lit).toInt()), max(1f, s * 0.025f))
        for (i in 0..2) {
            val yy = cy - s * 0.22f + i * s * 0.20f + sin(time * 1.6f + ph + i) * s * 0.035f
            c.drawLine(cx - s * 0.42f, yy, cx + s * 0.42f, yy + sin(time + ph + i * 2f) * s * 0.02f, line)
        }
        fill(c, alpha(0xFFBFEFF2.toInt(), (35 + 40 * lit).toInt()))
        c.drawCircle(cx + (hash(vx, vy, 77) - 0.5f) * s * 0.6f, cy + (hash(vx, vy, 78) - 0.5f) * s * 0.5f, s * 0.025f, p)
    }

    fun drawLava(c: Canvas, cx: Float, cy: Float, s: Float, time: Float, vx: Int, vy: Int, lit: Float) {
        fill(c, 0xFF3A1510.toInt())
        c.drawRect(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2, p)
        val ph = hash(vx, vy, 41) * 6.28f
        for (i in 0..3) {
            val xx = cx + sin(time * 0.7f + ph + i * 1.7f) * s * 0.22f
            val yy = cy + cos(time * 0.5f + ph + i * 2.1f) * s * 0.20f
            val col = when (i) { 0 -> 0xFFE07045.toInt(); 1 -> 0xFFF2A154.toInt(); 2 -> 0xFFD44E36.toInt(); else -> 0xFFFFC86B.toInt() }
            fill(c, alpha(col, (70 + 60 * lit).toInt()))
            c.drawCircle(xx, yy, s * (0.13f + 0.04f * i), p)
        }
        fill(c, alpha(0xFFFFD08A.toInt(), (45 + 55 * lit).toInt()))
        c.drawCircle(cx + sin(time * 1.1f + ph) * s * 0.2f, cy + cos(time * 0.9f + ph) * s * 0.2f, s * 0.05f, p)
    }

    fun drawRubble(c: Canvas, cx: Float, cy: Float, s: Float, depth: Int, vx: Int, vy: Int, lit: Float) {
        drawFloor(c, cx, cy, s, depth, vx, vy, 0f, lit)
        val base = darken(Biome.wallColor(depth, vx + vy), 0.75f)
        fill(c, base)
        for (i in 0..3) {
            val a = hash(vx, vy, 90 + i) * 6.28f
            val rr = s * (0.10f + hash(vx, vy, 120 + i) * 0.10f)
            c.drawCircle(cx + cos(a) * s * 0.22f, cy + sin(a) * s * 0.22f, rr, p)
        }
        fill(c, alpha(lighten(base, 0.2f), 100))
        c.drawCircle(cx - s * 0.12f, cy - s * 0.12f, s * 0.08f, p)
    }

    fun drawDoor(c: Canvas, cx: Float, cy: Float, s: Float, open: Boolean, lit: Float) {
        if (open) {
            fill(c, alpha(0xFF2A2118.toInt(), (140 + 100 * lit).toInt()))
            c.drawRect(cx - s / 2, cy - s / 2, cx - s * 0.35f, cy + s / 2, p)
            c.drawRect(cx + s * 0.35f, cy - s / 2, cx + s / 2, cy + s / 2, p)
            return
        }
        fill(c, 0xFF3B2A1C.toInt())
        rect.set(cx - s * 0.36f, cy - s * 0.44f, cx + s * 0.36f, cy + s * 0.44f)
        c.drawRoundRect(rect, s * 0.05f, s * 0.05f, p)
        stroke(c, 0xFF23180F.toInt(), max(1f, s * 0.025f))
        for (i in -1..1) {
            c.drawLine(cx + i * s * 0.17f, cy - s * 0.40f, cx + i * s * 0.17f, cy + s * 0.40f, line)
        }
        fill(c, 0xFF8A6A3A.toInt())
        c.drawRect(cx - s * 0.36f, cy - s * 0.10f, cx + s * 0.36f, cy - s * 0.04f, p)
        c.drawRect(cx - s * 0.36f, cy + s * 0.18f, cx + s * 0.36f, cy + s * 0.24f, p)
        fill(c, 0xFFD2A85C.toInt())
        c.drawCircle(cx + s * 0.22f, cy + s * 0.02f, s * 0.045f, p)
    }

    fun drawStairs(c: Canvas, cx: Float, cy: Float, s: Float, time: Float, lit: Float) {
        fill(c, 0xFF0B0D10.toInt())
        c.drawRect(cx - s * 0.42f, cy - s * 0.42f, cx + s * 0.42f, cy + s * 0.42f, p)
        for (i in 0..4) {
            val inset = s * (0.06f + i * 0.065f)
            val shade = 22 + i * 12
            fill(c, Color.rgb(shade, shade + 2, shade + 5))
            rect.set(cx - s * 0.42f + inset, cy - s * 0.42f + inset, cx + s * 0.42f - inset, cy + s * 0.42f - inset)
            c.drawRect(rect, p)
        }
        fill(c, alpha(Palette.AMBER_HI, (60 + 50 * sin(time * 2f) + 80 * lit).toInt().coerceIn(20, 180)))
        c.drawCircle(cx, cy, s * 0.07f, p)
        stroke(c, alpha(Palette.AMBER, 80), max(1f, s * 0.02f))
        c.drawCircle(cx, cy, s * 0.16f, line)
    }

    fun drawPortal(c: Canvas, cx: Float, cy: Float, s: Float, time: Float, lit: Float) {
        fill(c, 0xFF130A16.toInt())
        c.drawRect(cx - s * 0.45f, cy - s * 0.45f, cx + s * 0.45f, cy + s * 0.45f, p)
        for (i in 0..3) {
            stroke(c, alpha(if (i % 2 == 0) Palette.VIOLET else Palette.TEAL, (70 + 50 * lit).toInt()), max(1f, s * 0.03f))
            val r = s * (0.10f + i * 0.09f) + sin(time * 1.4f + i) * s * 0.015f
            c.drawCircle(cx, cy, r, line)
        }
        fill(c, alpha(Palette.AMBER_HI, 140))
        c.drawCircle(cx, cy, s * 0.05f, p)
    }

    fun drawPillar(c: Canvas, cx: Float, cy: Float, s: Float, depth: Int, vx: Int, vy: Int, lit: Float) {
        val base = Biome.wallColor(depth, vx * 2 + vy)
        fill(c, alpha(0xFF000000.toInt(), 100))
        c.drawOval(cx - s * 0.40f, cy + s * 0.22f, cx + s * 0.40f, cy + s * 0.46f, p)
        fill(c, darken(base, 0.5f))
        c.drawRect(cx - s * 0.26f, cy - s * 0.28f, cx + s * 0.26f, cy + s * 0.32f, p)
        fill(c, lighten(base, 0.12f))
        c.drawRect(cx - s * 0.30f, cy - s * 0.32f, cx + s * 0.30f, cy - s * 0.22f, p)
        c.drawOval(cx - s * 0.30f, cy - s * 0.40f, cx + s * 0.30f, cy - s * 0.22f, p)
        fill(c, darken(base, 0.65f))
        c.drawRect(cx - s * 0.12f, cy - s * 0.20f, cx - s * 0.04f, cy + s * 0.30f, p)
        stroke(c, alpha(lighten(base, 0.3f), 100), max(1f, s * 0.02f))
        c.drawLine(cx + s * 0.16f, cy - s * 0.18f, cx + s * 0.16f, cy + s * 0.26f, line)
    }

    fun drawTorchFlame(c: Canvas, cx: Float, cy: Float, s: Float, time: Float, seed: Int) {
        val f = 0.85f + 0.15f * sin(time * 9f + seed)
        fill(c, alpha(0xFFE0652F.toInt(), 160))
        c.drawOval(cx - s * 0.13f * f, cy - s * 0.30f * f, cx + s * 0.13f * f, cy + s * 0.06f * f, p)
        fill(c, alpha(0xFFFFB35C.toInt(), 220))
        c.drawOval(cx - s * 0.075f * f, cy - s * 0.20f * f, cx + s * 0.075f * f, cy + s * 0.02f * f, p)
        fill(c, alpha(0xFFFFF0C0.toInt(), 240))
        c.drawOval(cx - s * 0.03f, cy - s * 0.12f, cx + s * 0.03f, cy - s * 0.01f, p)
    }

    fun drawFeature(c: Canvas, f: Feature, cx: Float, cy: Float, s: Float, time: Float, lit: Float, data: Int) {
        when (f) {
            Feature.CHEST -> drawChest(c, cx, cy, s, false, lit)
            Feature.LOCKED_CHEST -> drawChest(c, cx, cy, s, true, lit)
            Feature.ALTAR -> drawAltar(c, cx, cy, s, time, lit)
            Feature.SHOP -> drawShop(c, cx, cy, s, time, lit)
            Feature.CRYSTAL -> drawCrystal(c, cx, cy, s, time, lit)
            Feature.MUSHROOM -> drawMushroom(c, cx, cy, s, lit)
            Feature.BONES -> drawBones(c, cx, cy, s, lit)
            Feature.WEED -> drawWeed(c, cx, cy, s, time, lit)
            Feature.TRAP_SPIKE -> if (data == 1) drawTrap(c, cx, cy, s, 0)
            Feature.TRAP_POISON -> if (data == 1) drawTrap(c, cx, cy, s, 1)
            Feature.TRAP_ALARM -> if (data == 1) drawTrap(c, cx, cy, s, 2)
            Feature.TRAP_TELEPORT -> if (data == 1) drawTrap(c, cx, cy, s, 3)
            else -> {}
        }
    }

    private fun drawChest(c: Canvas, cx: Float, cy: Float, s: Float, locked: Boolean, lit: Float) {
        fill(c, alpha(0xFF000000.toInt(), 90))
        c.drawOval(cx - s * 0.34f, cy + s * 0.20f, cx + s * 0.34f, cy + s * 0.36f, p)
        fill(c, 0xFF5B3D24.toInt())
        rect.set(cx - s * 0.32f, cy - s * 0.10f, cx + s * 0.32f, cy + s * 0.28f)
        c.drawRoundRect(rect, s * 0.05f, s * 0.05f, p)
        fill(c, 0xFF7A5530.toInt())
        rect.set(cx - s * 0.34f, cy - s * 0.26f, cx + s * 0.34f, cy - s * 0.02f)
        c.drawRoundRect(rect, s * 0.06f, s * 0.06f, p)
        fill(c, 0xFFD2A85C.toInt())
        c.drawRect(cx - s * 0.34f, cy - s * 0.06f, cx + s * 0.34f, cy - s * 0.01f, p)
        c.drawRect(cx - s * 0.05f, cy - s * 0.10f, cx + s * 0.05f, cy + s * 0.09f, p)
        fill(c, 0xFF3B2714.toInt())
        c.drawCircle(cx, cy + s * 0.02f, s * 0.035f, p)
        if (locked) {
            stroke(c, 0xFF9AA5AE.toInt(), max(1f, s * 0.045f))
            c.drawLine(cx - s * 0.30f, cy + s * 0.10f, cx + s * 0.30f, cy + s * 0.10f, line)
            fill(c, 0xFF9AA5AE.toInt())
            c.drawCircle(cx, cy + s * 0.10f, s * 0.06f, p)
        }
        fill(c, alpha(Palette.AMBER_HI, (35 + 45 * lit).toInt()))
        c.drawCircle(cx, cy - s * 0.05f, s * 0.4f, p)
    }

    private fun drawAltar(c: Canvas, cx: Float, cy: Float, s: Float, time: Float, lit: Float) {
        fill(c, alpha(0xFF000000.toInt(), 90))
        c.drawOval(cx - s * 0.34f, cy + s * 0.24f, cx + s * 0.34f, cy + s * 0.40f, p)
        fill(c, 0xFF4D4A42.toInt())
        c.drawRect(cx - s * 0.26f, cy - s * 0.02f, cx + s * 0.26f, cy + s * 0.32f, p)
        fill(c, 0xFF666156.toInt())
        c.drawRect(cx - s * 0.32f, cy - s * 0.14f, cx + s * 0.32f, cy + s * 0.02f, p)
        fill(c, 0xFF8A6533.toInt())
        c.drawOval(cx - s * 0.22f, cy - s * 0.24f, cx + s * 0.22f, cy - s * 0.06f, p)
        val f = 0.9f + 0.1f * sin(time * 5f)
        fill(c, alpha(0xFFFFB35C.toInt(), 170))
        c.drawOval(cx - s * 0.10f * f, cy - s * 0.40f * f, cx + s * 0.10f * f, cy - s * 0.06f, p)
        fill(c, alpha(0xFFFFF0C0.toInt(), 230))
        c.drawOval(cx - s * 0.045f, cy - s * 0.28f, cx + s * 0.045f, cy - s * 0.10f, p)
        stroke(c, alpha(Palette.AMBER_HI, (60 + 60 * lit).toInt()), max(1f, s * 0.02f))
        c.drawCircle(cx, cy - s * 0.20f, s * (0.28f + 0.02f * sin(time * 2f)), line)
    }

    private fun drawShop(c: Canvas, cx: Float, cy: Float, s: Float, time: Float, lit: Float) {
        fill(c, alpha(0xFF000000.toInt(), 100))
        c.drawOval(cx - s * 0.36f, cy + s * 0.22f, cx + s * 0.36f, cy + s * 0.40f, p)
        fill(c, 0xFF5B3D24.toInt())
        c.drawRect(cx - s * 0.30f, cy - s * 0.10f, cx + s * 0.30f, cy + s * 0.30f, p)
        fill(c, 0xFF7A5530.toInt())
        c.drawRect(cx - s * 0.34f, cy - s * 0.22f, cx + s * 0.34f, cy - s * 0.06f, p)
        // Little awning.
        fill(c, 0xFF9B4A4A.toInt())
        for (i in -2..2) {
            val x = cx + i * s * 0.13f
            c.drawRect(x - s * 0.06f, cy - s * 0.36f, x + s * 0.06f, cy - s * 0.24f, p)
        }
        fill(c, 0xFFD2A85C.toInt())
        c.drawCircle(cx - s * 0.12f, cy - s * 0.02f, s * 0.05f, p)
        c.drawCircle(cx + s * 0.06f, cy + s * 0.02f, s * 0.04f, p)
        drawTorchFlame(c, cx + s * 0.30f, cy - s * 0.20f, s * 0.5f, time, 3)
    }

    private fun drawCrystal(c: Canvas, cx: Float, cy: Float, s: Float, time: Float, lit: Float) {
        fill(c, alpha(0xFF000000.toInt(), 70))
        c.drawOval(cx - s * 0.24f, cy + s * 0.20f, cx + s * 0.24f, cy + s * 0.32f, p)
        val glow = (0.6f + 0.4f * sin(time * 2f + cx)).coerceIn(0f, 1f)
        fill(c, alpha(0xFF9C8CFF.toInt(), (30 + 50 * glow * lit).toInt()))
        c.drawCircle(cx, cy, s * 0.40f, p)
        val spikes = listOf(Triple(0f, -0.34f, 0.11f), Triple(-0.18f, -0.16f, 0.075f), Triple(0.19f, -0.12f, 0.085f))
        for ((i, sp) in spikes.withIndex()) {
            val xx = cx + sp.first * s
            val tip = cy + sp.second * s
            fill(c, if (i == 0) 0xFFB9A6FF.toInt() else 0xFF8A76D8.toInt())
            path.reset()
            path.moveTo(xx, tip)
            path.lineTo(xx + sp.third * s, cy + s * 0.18f)
            path.lineTo(xx - sp.third * s, cy + s * 0.18f)
            path.close()
            c.drawPath(path, p)
            stroke(c, alpha(0xFFE5DCFF.toInt(), 160), max(1f, s * 0.015f))
            c.drawLine(xx, tip + s * 0.03f, xx + sp.third * s * 0.4f, cy + s * 0.12f, line)
        }
    }

    private fun drawMushroom(c: Canvas, cx: Float, cy: Float, s: Float, lit: Float) {
        fill(c, 0xFFD7D0BC.toInt())
        c.drawRect(cx - s * 0.035f, cy - s * 0.02f, cx + s * 0.035f, cy + s * 0.18f, p)
        fill(c, 0xFF6FAE61.toInt())
        c.drawOval(cx - s * 0.17f, cy - s * 0.16f, cx + s * 0.17f, cy + s * 0.04f, p)
        fill(c, alpha(0xFF9BE07A.toInt(), (80 + 80 * lit).toInt()))
        c.drawCircle(cx - s * 0.06f, cy - s * 0.08f, s * 0.025f, p)
        c.drawCircle(cx + s * 0.07f, cy - s * 0.05f, s * 0.02f, p)
    }

    private fun drawBones(c: Canvas, cx: Float, cy: Float, s: Float, lit: Float) {
        fill(c, 0xFFC9C2AC.toInt())
        c.drawCircle(cx, cy - s * 0.03f, s * 0.11f, p)
        fill(c, 0xFF3A3730.toInt())
        c.drawCircle(cx - s * 0.035f, cy - s * 0.05f, s * 0.022f, p)
        c.drawCircle(cx + s * 0.035f, cy - s * 0.05f, s * 0.022f, p)
        stroke(c, 0xFFC9C2AC.toInt(), max(1f, s * 0.03f))
        c.drawLine(cx - s * 0.18f, cy + s * 0.15f, cx + s * 0.16f, cy + s * 0.08f, line)
        c.drawLine(cx - s * 0.13f, cy + s * 0.20f, cx + s * 0.20f, cy + s * 0.14f, line)
    }

    private fun drawWeed(c: Canvas, cx: Float, cy: Float, s: Float, time: Float, lit: Float) {
        stroke(c, 0xFF4F7A4F.toInt(), max(1f, s * 0.025f))
        for (i in 0..4) {
            val a = -1.5f + i * 0.35f + sin(time * 1.2f + i) * 0.06f
            c.drawLine(cx + (i - 2) * s * 0.06f, cy + s * 0.18f, cx + cos(a) * s * 0.16f, cy + s * 0.18f + sin(a) * s * 0.22f, line)
        }
    }

    private fun drawTrap(c: Canvas, cx: Float, cy: Float, s: Float, kind: Int) {
        when (kind) {
            0 -> {
                fill(c, 0xFF3A3A3A.toInt())
                c.drawRect(cx - s * 0.36f, cy - s * 0.36f, cx + s * 0.36f, cy + s * 0.36f, p)
                fill(c, 0xFFB9C3C8.toInt())
                for (i in -1..1) for (j in -1..1) {
                    path.reset()
                    val x = cx + i * s * 0.22f
                    val y = cy + j * s * 0.22f
                    path.moveTo(x - s * 0.05f, y + s * 0.06f)
                    path.lineTo(x, y - s * 0.09f)
                    path.lineTo(x + s * 0.05f, y + s * 0.06f)
                    path.close()
                    c.drawPath(path, p)
                }
            }
            1 -> {
                fill(c, alpha(0xFF6FAE61.toInt(), 100))
                c.drawCircle(cx, cy, s * 0.30f, p)
                fill(c, 0xFF2F4A2E.toInt())
                for (i in 0..4) {
                    c.drawCircle(cx + cos(i * 1.25f) * s * 0.18f, cy + sin(i * 1.25f) * s * 0.18f, s * 0.05f, p)
                }
            }
            2 -> {
                stroke(c, 0xFFE0B45C.toInt(), max(1f, s * 0.025f))
                c.drawCircle(cx, cy, s * 0.25f, line)
                c.drawCircle(cx, cy, s * 0.14f, line)
                c.drawLine(cx - s * 0.28f, cy, cx + s * 0.28f, cy, line)
            }
            else -> {
                stroke(c, 0xFFA78BFA.toInt(), max(1f, s * 0.025f))
                c.drawCircle(cx, cy, s * 0.26f, line)
                path.reset()
                path.moveTo(cx - s * 0.16f, cy - s * 0.16f)
                path.lineTo(cx + s * 0.16f, cy + s * 0.16f)
                path.moveTo(cx + s * 0.16f, cy - s * 0.16f)
                path.lineTo(cx - s * 0.16f, cy + s * 0.16f)
                c.drawPath(path, line)
            }
        }
    }

    // ------------------------------------------------------------------
    // Items
    // ------------------------------------------------------------------

    fun drawItem(c: Canvas, item: Item, cx: Float, cy: Float, s: Float, time: Float, glow: Boolean = true) {
        val def = item.def
        if (glow) {
            fill(c, alpha(def.color, 26))
            c.drawCircle(cx, cy, s * 0.85f, p)
        }
        val bob = sin(time * 2.2f + cx * 0.05f + cy * 0.03f) * s * 0.04f
        val x = cx
        val y = cy + bob
        when (def.kind) {
            ItemKind.WEAPON -> drawWeapon(c, item.id, x, y, s)
            ItemKind.ARMOR -> drawArmor(c, item.id, x, y, s)
            ItemKind.RELIC -> drawRelic(c, item.id, x, y, s, time)
            ItemKind.POTION -> drawPotion(c, def.color, x, y, s)
            ItemKind.SCROLL -> drawScroll(c, def.color, x, y, s)
            ItemKind.OIL -> drawFlask(c, x, y, s, 0xFFE3C05A.toInt())
            ItemKind.KEY -> drawKey(c, x, y, s)
            ItemKind.FOOD -> drawBread(c, x, y, s)
            ItemKind.QUEST -> if (item.id == Items.GOLD) drawCoin(c, x, y, s) else drawSeal(c, x, y, s, time)
        }
        if (item.upgrade > 0) {
            fill(c, Palette.AMBER_HI)
            c.drawCircle(cx + s * 0.34f, cy - s * 0.34f, s * 0.14f, p)
            fill(c, Palette.BG)
            p.textSize = s * 0.22f
            p.textAlign = Paint.Align.CENTER
            c.drawText("+", cx + s * 0.34f, cy - s * 0.26f, p)
        }
    }

    private fun drawWeapon(c: Canvas, id: String, cx: Float, cy: Float, s: Float) {
        when (id) {
            Items.EMBER_STAFF -> {
                stroke(c, 0xFF7A5530.toInt(), s * 0.075f)
                c.drawLine(cx - s * 0.24f, cy + s * 0.28f, cx + s * 0.16f, cy - s * 0.24f, line)
                fill(c, 0xFFE06A3F.toInt())
                c.drawCircle(cx + s * 0.19f, cy - s * 0.28f, s * 0.10f, p)
                fill(c, alpha(0xFFFFD08A.toInt(), 220))
                c.drawCircle(cx + s * 0.19f, cy - s * 0.28f, s * 0.045f, p)
            }
            Items.SHORT_BOW -> {
                stroke(c, 0xFFC4A46A.toInt(), s * 0.06f)
                rect.set(cx - s * 0.26f, cy - s * 0.30f, cx + s * 0.18f, cy + s * 0.30f)
                c.drawArc(rect, -70f, 140f, false, line)
                stroke(c, 0xFFB8B2A0.toInt(), max(1f, s * 0.018f))
                c.drawLine(cx - s * 0.12f, cy - s * 0.27f, cx - s * 0.12f, cy + s * 0.27f, line)
            }
            Items.CRYSTAL_SPEAR -> {
                stroke(c, 0xFF7A5530.toInt(), s * 0.055f)
                c.drawLine(cx - s * 0.28f, cy + s * 0.30f, cx + s * 0.18f, cy - s * 0.16f, line)
                fill(c, 0xFF9FC7E8.toInt())
                path.reset()
                path.moveTo(cx + s * 0.30f, cy - s * 0.32f)
                path.lineTo(cx + s * 0.12f, cy - s * 0.12f)
                path.lineTo(cx + s * 0.21f, cy - s * 0.06f)
                path.close()
                c.drawPath(path, p)
            }
            Items.ASH_AXE -> {
                stroke(c, 0xFF7A5530.toInt(), s * 0.07f)
                c.drawLine(cx - s * 0.20f, cy + s * 0.30f, cx + s * 0.12f, cy - s * 0.18f, line)
                fill(c, 0xFFC0C4C8.toInt())
                path.reset()
                path.moveTo(cx + s * 0.02f, cy - s * 0.24f)
                path.quadTo(cx + s * 0.34f, cy - s * 0.30f, cx + s * 0.28f, cy + s * 0.02f)
                path.quadTo(cx + s * 0.14f, cy - s * 0.10f, cx + s * 0.02f, cy - s * 0.24f)
                path.close()
                c.drawPath(path, p)
            }
            Items.VOID_DAGGER -> {
                stroke(c, 0xFF3B2A4A.toInt(), s * 0.06f)
                c.drawLine(cx - s * 0.12f, cy + s * 0.16f, cx + s * 0.22f, cy - s * 0.26f, line)
                fill(c, 0xFFB08BE0.toInt())
                path.reset()
                path.moveTo(cx + s * 0.32f, cy - s * 0.34f)
                path.lineTo(cx + s * 0.10f, cy - s * 0.16f)
                path.lineTo(cx + s * 0.22f, cy - s * 0.04f)
                path.close()
                c.drawPath(path, p)
                stroke(c, 0xFFD2A85C.toInt(), s * 0.05f)
                c.drawLine(cx - s * 0.20f, cy + s * 0.16f, cx - s * 0.06f, cy + s * 0.26f, line)
            }
            Items.LANTERN_HAMMER -> {
                stroke(c, 0xFF7A5530.toInt(), s * 0.075f)
                c.drawLine(cx - s * 0.18f, cy + s * 0.30f, cx + s * 0.10f, cy - s * 0.14f, line)
                fill(c, 0xFF9AA5AE.toInt())
                rect.set(cx - s * 0.02f, cy - s * 0.30f, cx + s * 0.28f, cy - s * 0.08f)
                c.drawRoundRect(rect, s * 0.05f, s * 0.05f, p)
                fill(c, alpha(0xFFFFB35C.toInt(), 200))
                c.drawCircle(cx + s * 0.13f, cy - s * 0.19f, s * 0.06f, p)
            }
            else -> {
                stroke(c, 0xFFB9C3C8.toInt(), s * 0.075f)
                c.drawLine(cx - s * 0.22f, cy + s * 0.28f, cx + s * 0.18f, cy - s * 0.22f, line)
                fill(c, 0xFFD2A85C.toInt())
                c.drawRect(cx - s * 0.24f, cy + s * 0.16f, cx - s * 0.02f, cy + s * 0.32f, p)
                stroke(c, 0xFFE5E9EB.toInt(), max(1f, s * 0.02f))
                c.drawLine(cx + s * 0.18f, cy - s * 0.22f, cx + s * 0.30f, cy - s * 0.34f, line)
            }
        }
    }

    private fun drawArmor(c: Canvas, id: String, cx: Float, cy: Float, s: Float) {
        val col = when (id) {
            Items.TORN_ROBE -> 0xFF6C7FA8.toInt()
            Items.SHADE_CLOAK -> 0xFF5E8C88.toInt()
            Items.CHAIN_SHIRT -> 0xFF9AA5AE.toInt()
            Items.CRYSTAL_MAIL -> 0xFF8CB8D8.toInt()
            Items.ASH_PLATE -> 0xFFB07858.toInt()
            Items.SEAL_AEGIS -> 0xFFE4C477.toInt()
            else -> 0xFFA67B52.toInt()
        }
        fill(c, col)
        path.reset()
        path.moveTo(cx - s * 0.28f, cy - s * 0.24f)
        path.lineTo(cx - s * 0.12f, cy - s * 0.30f)
        path.lineTo(cx, cy - s * 0.18f)
        path.lineTo(cx + s * 0.12f, cy - s * 0.30f)
        path.lineTo(cx + s * 0.28f, cy - s * 0.24f)
        path.lineTo(cx + s * 0.22f, cy + s * 0.28f)
        path.lineTo(cx - s * 0.22f, cy + s * 0.28f)
        path.close()
        c.drawPath(path, p)
        fill(c, alpha(lighten(col, 0.35f), 120))
        c.drawRect(cx - s * 0.10f, cy - s * 0.16f, cx + s * 0.10f, cy + s * 0.24f, p)
        if (id == Items.SEAL_AEGIS) {
            fill(c, Palette.AMBER_HI)
            c.drawCircle(cx, cy, s * 0.08f, p)
        }
    }

    private fun drawRelic(c: Canvas, id: String, cx: Float, cy: Float, s: Float, time: Float) {
        when (id) {
            Items.EMBER_HEART -> {
                fill(c, 0xFFE06A4E.toInt())
                heart(c, cx, cy, s * 0.26f)
                fill(c, alpha(0xFFFFD08A.toInt(), 160))
                c.drawCircle(cx - s * 0.08f, cy - s * 0.06f, s * 0.06f, p)
            }
            Items.MIST_BREATH -> {
                fill(c, alpha(0xFF74C5BC.toInt(), 170))
                for (i in 0..2) c.drawCircle(cx + sin(time + i) * s * 0.10f, cy - s * 0.10f + i * s * 0.11f, s * (0.14f - i * 0.02f), p)
            }
            Items.SEAL_SHARD -> {
                fill(c, 0xFFE8C66F.toInt())
                path.reset()
                path.moveTo(cx, cy - s * 0.30f)
                path.lineTo(cx + s * 0.20f, cy - s * 0.04f)
                path.lineTo(cx + s * 0.08f, cy + s * 0.26f)
                path.lineTo(cx - s * 0.16f, cy + s * 0.18f)
                path.lineTo(cx - s * 0.18f, cy - s * 0.10f)
                path.close()
                c.drawPath(path, p)
            }
            Items.OPEN_EYE -> {
                fill(c, 0xFFF0EDE0.toInt())
                c.drawOval(cx - s * 0.28f, cy - s * 0.14f, cx + s * 0.28f, cy + s * 0.14f, p)
                fill(c, 0xFF7A5BA8.toInt())
                c.drawCircle(cx, cy, s * 0.10f, p)
                fill(c, 0xFF120C18.toInt())
                c.drawCircle(cx, cy, s * 0.04f, p)
            }
            Items.BLOODSTONE -> {
                fill(c, 0xFFC45461.toInt())
                path.reset()
                path.moveTo(cx, cy - s * 0.28f)
                path.quadTo(cx + s * 0.28f, cy, cx, cy + s * 0.26f)
                path.quadTo(cx - s * 0.28f, cy, cx, cy - s * 0.28f)
                path.close()
                c.drawPath(path, p)
            }
            else -> {
                fill(c, 0xFFD2A85C.toInt())
                c.drawCircle(cx, cy, s * 0.22f, p)
            }
        }
    }

    private fun drawPotion(c: Canvas, color: Int, cx: Float, cy: Float, s: Float) {
        fill(c, alpha(0xFFBFEFF2.toInt(), 90))
        rect.set(cx - s * 0.15f, cy - s * 0.28f, cx + s * 0.15f, cy + s * 0.20f)
        c.drawRoundRect(rect, s * 0.10f, s * 0.10f, p)
        fill(c, color)
        rect.set(cx - s * 0.12f, cy - s * 0.04f, cx + s * 0.12f, cy + s * 0.17f)
        c.drawRoundRect(rect, s * 0.08f, s * 0.08f, p)
        fill(c, 0xFF7A5530.toInt())
        c.drawRect(cx - s * 0.07f, cy - s * 0.34f, cx + s * 0.07f, cy - s * 0.25f, p)
        fill(c, alpha(0xFFFFFFFF.toInt(), 150))
        c.drawCircle(cx - s * 0.05f, cy - s * 0.02f, s * 0.025f, p)
    }

    private fun drawScroll(c: Canvas, color: Int, cx: Float, cy: Float, s: Float) {
        fill(c, color)
        rect.set(cx - s * 0.24f, cy - s * 0.24f, cx + s * 0.24f, cy + s * 0.24f)
        c.drawRoundRect(rect, s * 0.05f, s * 0.05f, p)
        fill(c, 0xFFE8DFC4.toInt())
        rect.set(cx - s * 0.20f, cy - s * 0.20f, cx + s * 0.20f, cy + s * 0.20f)
        c.drawRect(rect, p)
        stroke(c, darken(color, 0.55f), max(1f, s * 0.02f))
        c.drawCircle(cx, cy, s * 0.10f, line)
        c.drawLine(cx - s * 0.06f, cy, cx + s * 0.06f, cy, line)
        c.drawLine(cx, cy - s * 0.06f, cx, cy + s * 0.06f, line)
    }

    private fun drawFlask(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        fill(c, alpha(color, 110))
        rect.set(cx - s * 0.17f, cy - s * 0.18f, cx + s * 0.17f, cy + s * 0.24f)
        c.drawRoundRect(rect, s * 0.10f, s * 0.10f, p)
        fill(c, color)
        rect.set(cx - s * 0.14f, cy + s * 0.02f, cx + s * 0.14f, cy + s * 0.21f)
        c.drawRoundRect(rect, s * 0.07f, s * 0.07f, p)
        fill(c, 0xFF8A6533.toInt())
        c.drawRect(cx - s * 0.06f, cy - s * 0.28f, cx + s * 0.06f, cy - s * 0.17f, p)
    }

    private fun drawKey(c: Canvas, cx: Float, cy: Float, s: Float) {
        stroke(c, 0xFFD2A85C.toInt(), s * 0.075f)
        c.drawCircle(cx - s * 0.14f, cy - s * 0.10f, s * 0.11f, line)
        c.drawLine(cx - s * 0.06f, cy - s * 0.02f, cx + s * 0.24f, cy + s * 0.24f, line)
        c.drawLine(cx + s * 0.12f, cy + s * 0.16f, cx + s * 0.20f, cy + s * 0.08f, line)
        c.drawLine(cx + s * 0.18f, cy + s * 0.22f, cx + s * 0.26f, cy + s * 0.14f, line)
    }

    private fun drawBread(c: Canvas, cx: Float, cy: Float, s: Float) {
        fill(c, 0xFFB98B54.toInt())
        c.drawOval(cx - s * 0.26f, cy - s * 0.16f, cx + s * 0.26f, cy + s * 0.18f, p)
        stroke(c, 0xFF7A5530.toInt(), max(1f, s * 0.02f))
        for (i in -1..1) c.drawLine(cx + i * s * 0.10f, cy - s * 0.10f, cx + i * s * 0.10f, cy + s * 0.10f, line)
    }

    private fun drawCoin(c: Canvas, cx: Float, cy: Float, s: Float) {
        fill(c, 0xFFE8C35C.toInt())
        c.drawCircle(cx, cy, s * 0.20f, p)
        fill(c, 0xFFFFE6A0.toInt())
        c.drawCircle(cx - s * 0.04f, cy - s * 0.04f, s * 0.09f, p)
        stroke(c, 0xFF8A6533.toInt(), max(1f, s * 0.025f))
        c.drawCircle(cx, cy, s * 0.20f, line)
    }

    private fun drawSeal(c: Canvas, cx: Float, cy: Float, s: Float, time: Float) {
        fill(c, alpha(Palette.AMBER_HI, (40 + 40 * sin(time * 2f)).toInt().coerceIn(10, 80)))
        c.drawCircle(cx, cy, s * 0.7f, p)
        fill(c, 0xFFE8C35C.toInt())
        c.drawCircle(cx, cy, s * 0.26f, p)
        fill(c, 0xFF3B2A1C.toInt())
        c.drawCircle(cx, cy, s * 0.18f, p)
        stroke(c, Palette.AMBER_HI, max(1f, s * 0.03f))
        c.drawCircle(cx, cy, s * 0.30f, line)
        fill(c, Palette.AMBER_HI)
        c.drawCircle(cx, cy, s * 0.07f, p)
    }

    private fun heart(c: Canvas, cx: Float, cy: Float, r: Float) {
        path.reset()
        path.moveTo(cx, cy + r * 0.8f)
        path.cubicTo(cx - r * 1.4f, cy - r * 0.1f, cx - r * 0.5f, cy - r * 1.1f, cx, cy - r * 0.4f)
        path.cubicTo(cx + r * 0.5f, cy - r * 1.1f, cx + r * 1.4f, cy - r * 0.1f, cx, cy + r * 0.8f)
        path.close()
        c.drawPath(path, p)
    }

    // ------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------

    fun drawPlayer(c: Canvas, cls: HeroClass, cx: Float, cy: Float, s: Float, time: Float, facingX: Float, facingY: Float,
                   attacking: Boolean, hurt: Boolean, alphaF: Float = 1f) {
        val a = (255 * alphaF).toInt().coerceIn(0, 255)
        // Shadow.
        fill(c, alpha(0xFF000000.toInt(), (90 * alphaF).toInt()))
        c.drawOval(cx - s * 0.34f, cy + s * 0.26f, cx + s * 0.34f, cy + s * 0.42f, p)
        val bob = sin(time * 2.4f) * s * 0.018f
        val lunge = if (attacking) s * 0.10f else 0f
        val fx = if (abs(facingX) > 0.1f) facingX / max(0.01f, max(abs(facingX), abs(facingY))) else 0f
        val fy = if (abs(facingY) > 0.1f) facingY / max(0.01f, max(abs(facingX), abs(facingY))) else 0f
        val px = cx + fx * lunge
        val py = cy + fy * lunge + bob
        val cloak = when (cls) {
            HeroClass.BLADE -> 0xFF2F4F58.toInt()
            HeroClass.EMBER -> 0xFF5B2E3A.toInt()
            HeroClass.SHADE -> 0xFF28484A.toInt()
        }
        val trim = when (cls) {
            HeroClass.BLADE -> 0xFFE3A153.toInt()
            HeroClass.EMBER -> 0xFFE0654F.toInt()
            HeroClass.SHADE -> 0xFF6FC7BE.toInt()
        }
        // Body / cloak.
        fill(c, alpha(cloak, a))
        path.reset()
        path.moveTo(px - s * 0.28f, py + s * 0.24f)
        path.lineTo(px - s * 0.18f, py - s * 0.18f)
        path.quadTo(px, py - s * 0.30f, px + s * 0.18f, py - s * 0.18f)
        path.lineTo(px + s * 0.28f, py + s * 0.24f)
        path.close()
        c.drawPath(path, p)
        fill(c, alpha(lighten(cloak, 0.18f), a))
        c.drawOval(px - s * 0.17f, py - s * 0.26f, px + s * 0.17f, py + s * 0.08f, p)
        // Hood / head.
        fill(c, alpha(0xFF1A2026.toInt(), a))
        c.drawCircle(px - fx * s * 0.03f, py - s * 0.22f, s * 0.16f, p)
        fill(c, alpha(0xFFF0C49B.toInt(), a))
        c.drawCircle(px - fx * s * 0.03f, py - s * 0.21f, s * 0.105f, p)
        // Eyes.
        fill(c, alpha(trim, a))
        val ex = px - fx * s * 0.02f
        c.drawCircle(ex - s * 0.045f + fx * s * 0.02f, py - s * 0.225f, s * 0.018f, p)
        c.drawCircle(ex + s * 0.045f + fx * s * 0.02f, py - s * 0.225f, s * 0.018f, p)
        // Trim.
        stroke(c, alpha(trim, a), max(1f, s * 0.025f))
        c.drawLine(px - s * 0.22f, py + s * 0.18f, px + s * 0.22f, py + s * 0.18f, line)
        // Weapon / emblem.
        when (cls) {
            HeroClass.BLADE -> {
                stroke(c, alpha(0xFFD8DEE2.toInt(), a), max(1f, s * 0.045f))
                c.drawLine(px + s * 0.30f, py + s * 0.16f, px + s * 0.42f, py - s * 0.28f, line)
                stroke(c, alpha(0xFF8A6533.toInt(), a), max(1f, s * 0.035f))
                c.drawLine(px + s * 0.24f, py + s * 0.10f, px + s * 0.34f, py + s * 0.20f, line)
            }
            HeroClass.EMBER -> {
                stroke(c, alpha(0xFF7A5530.toInt(), a), max(1f, s * 0.045f))
                c.drawLine(px + s * 0.24f, py + s * 0.26f, px + s * 0.36f, py - s * 0.26f, line)
                fill(c, alpha(0xFFE06A3F.toInt(), (a * 0.9f).toInt()))
                c.drawCircle(px + s * 0.37f, py - s * 0.29f, s * 0.075f, p)
                fill(c, alpha(0xFFFFD08A.toInt(), a))
                c.drawCircle(px + s * 0.37f, py - s * 0.29f, s * 0.03f, p)
            }
            HeroClass.SHADE -> {
                stroke(c, alpha(0xFFC4A46A.toInt(), a), max(1f, s * 0.035f))
                rect.set(px + s * 0.18f, py - s * 0.22f, px + s * 0.42f, py + s * 0.20f)
                c.drawArc(rect, -70f, 140f, false, line)
                stroke(c, alpha(0xFFE0D8C0.toInt(), a), max(1f, s * 0.014f))
                c.drawLine(px + s * 0.25f, py - s * 0.20f, px + s * 0.25f, py + s * 0.18f, line)
            }
        }
        // Lantern in other hand.
        val lx = px - s * 0.30f
        val ly = py + s * 0.04f
        fill(c, alpha(0xFF2C2118.toInt(), a))
        c.drawRect(lx - s * 0.07f, ly - s * 0.10f, lx + s * 0.07f, ly + s * 0.10f, p)
        fill(c, alpha(0xFFFFC86B.toInt(), a))
        c.drawRect(lx - s * 0.045f, ly - s * 0.07f, lx + s * 0.045f, ly + s * 0.07f, p)
        fill(c, alpha(0xFFFFF0C0.toInt(), a))
        c.drawCircle(lx, ly, s * 0.035f, p)
        if (hurt) {
            fill(c, alpha(0xFFFF5A5A.toInt(), 90))
            c.drawCircle(px, py, s * 0.42f, p)
        }
    }

    fun drawEnemy(c: Canvas, e: Actor, cx: Float, cy: Float, s: Float, time: Float, hurt: Boolean, alphaF: Float = 1f) {
        val a = (255 * alphaF).toInt().coerceIn(0, 255)
        val def = Mobs.def(e.enemyId ?: Mobs.RAT)
        val bob = sin(time * 2.1f + e.x * 0.7f + e.y * 0.5f) * s * 0.025f
        val lunge = if (System.currentTimeMillis() < e.attackUntil) s * 0.07f else 0f
        val fx = e.facingX
        val fy = e.facingY
        val px = cx + fx * lunge
        val py = cy + bob + fy * lunge
        fill(c, alpha(0xFF000000.toInt(), (75 * alphaF).toInt()))
        c.drawOval(cx - s * 0.34f, cy + s * 0.24f, cx + s * 0.34f, cy + s * 0.40f, p)
        if (e.elite) {
            stroke(c, alpha(0xFFE8C35C.toInt(), (90 * alphaF).toInt()), max(1f, s * 0.025f))
            c.drawCircle(px, py, s * 0.48f, line)
        }
        when (def.id) {
            Mobs.SLIME -> {
                fill(c, alpha(0xFF5C9B56.toInt(), a))
                c.drawOval(px - s * 0.34f, py - s * 0.20f, px + s * 0.34f, py + s * 0.32f, p)
                fill(c, alpha(0xFF8CD07A.toInt(), a))
                c.drawOval(px - s * 0.20f, py - s * 0.32f, px + s * 0.20f, py + s * 0.10f, p)
                fill(c, alpha(0xFF1A2A18.toInt(), a))
                c.drawCircle(px - s * 0.09f, py - s * 0.10f, s * 0.035f, p)
                c.drawCircle(px + s * 0.09f, py - s * 0.10f, s * 0.035f, p)
                fill(c, alpha(0xFFFFFFFF.toInt(), (80 * alphaF).toInt()))
                c.drawCircle(px - s * 0.09f, py - s * 0.18f, s * 0.045f, p)
            }
            Mobs.RAT -> {
                fill(c, alpha(0xFF7C6046.toInt(), a))
                c.drawOval(px - s * 0.28f, py - s * 0.10f, px + s * 0.26f, py + s * 0.24f, p)
                c.drawCircle(px + s * 0.18f, py - s * 0.04f, s * 0.14f, p)
                fill(c, alpha(0xFF8C6E52.toInt(), a))
                c.drawCircle(px + s * 0.12f, py - s * 0.20f, s * 0.075f, p)
                c.drawCircle(px + s * 0.27f, py - s * 0.20f, s * 0.075f, p)
                stroke(c, alpha(0xFF7C6046.toInt(), a), max(1f, s * 0.03f))
                c.drawLine(px - s * 0.26f, py + s * 0.16f, px - s * 0.40f, py + s * 0.05f, line)
                fill(c, alpha(0xFFE05A5A.toInt(), a))
                c.drawCircle(px + s * 0.208f, py - s * 0.07f, s * 0.023f, p)
            }
            Mobs.SPIDER -> {
                stroke(c, alpha(0xFF6E559A.toInt(), a), max(1f, s * 0.03f))
                for (i in 0..3) {
                    val yy = py - s * 0.08f + i * s * 0.08f
                    c.drawLine(px - s * 0.16f, yy, px - s * 0.38f, yy - s * 0.10f + i * s * 0.03f, line)
                    c.drawLine(px + s * 0.16f, yy, px + s * 0.38f, yy - s * 0.10f + i * s * 0.03f, line)
                }
                fill(c, alpha(0xFF8A6AC0.toInt(), a))
                c.drawCircle(px, py, s * 0.23f, p)
                fill(c, alpha(0xFFB9A0E8.toInt(), a))
                c.drawCircle(px - s * 0.04f, py - s * 0.05f, s * 0.08f, p)
                fill(c, alpha(0xFFE05A5A.toInt(), a))
                c.drawCircle(px - s * 0.08f, py - s * 0.08f, s * 0.028f, p)
                c.drawCircle(px + s * 0.08f, py - s * 0.08f, s * 0.028f, p)
                fill(c, alpha(0xFFC9B6F2.toInt(), a))
                path.reset()
                path.moveTo(px, py - s * 0.22f)
                path.lineTo(px + s * 0.07f, py - s * 0.38f)
                path.lineTo(px + s * 0.14f, py - s * 0.22f)
                path.close()
                c.drawPath(path, p)
            }
            Mobs.HOUND -> {
                fill(c, alpha(0xFFB05C36.toInt(), a))
                c.drawOval(px - s * 0.34f, py - s * 0.06f, px + s * 0.24f, py + s * 0.25f, p)
                c.drawCircle(px + s * 0.22f, py - s * 0.02f, s * 0.15f, p)
                fill(c, alpha(0xFFE08A55.toInt(), a))
                for (i in 0..2) {
                    path.reset()
                    val x = px + s * (0.02f + i * 0.08f)
                    path.moveTo(x, py - s * 0.08f)
                    path.lineTo(x + s * 0.05f, py - s * 0.25f)
                    path.lineTo(x + s * 0.10f, py - s * 0.08f)
                    path.close()
                    c.drawPath(path, p)
                }
                stroke(c, alpha(0xFFB05C36.toInt(), a), max(1f, s * 0.035f))
                c.drawLine(px - s * 0.32f, py + s * 0.10f, px - s * 0.46f, py - s * 0.04f, line)
                fill(c, alpha(0xFFFFD08A.toInt(), a))
                c.drawCircle(px + s * 0.27f, py - s * 0.04f, s * 0.028f, p)
            }
            Mobs.THORN -> {
                fill(c, alpha(0xFF4F7A4F.toInt(), a))
                c.drawOval(px - s * 0.24f, py - s * 0.12f, px + s * 0.24f, py + s * 0.28f, p)
                stroke(c, alpha(0xFF9BC47A.toInt(), a), max(1f, s * 0.03f))
                for (i in 0..6) {
                    val ang = -2.8f + i * 0.85f
                    c.drawLine(px, py + s * 0.04f, px + cos(ang) * s * 0.34f, py + sin(ang) * s * 0.34f, line)
                }
                fill(c, alpha(0xFFE05A5A.toInt(), a))
                c.drawCircle(px, py + s * 0.04f, s * 0.05f, p)
            }
            Mobs.BAT -> {
                val flap = sin(time * 10f + e.x) * 0.25f
                fill(c, alpha(0xFF6E559A.toInt(), a))
                path.reset()
                path.moveTo(px, py)
                path.quadTo(px - s * 0.44f, py - s * (0.10f + flap), px - s * 0.46f, py + s * 0.10f)
                path.quadTo(px - s * 0.22f, py + s * 0.04f, px, py + s * 0.10f)
                path.close()
                c.drawPath(path, p)
                path.reset()
                path.moveTo(px, py)
                path.quadTo(px + s * 0.44f, py - s * (0.10f + flap), px + s * 0.46f, py + s * 0.10f)
                path.quadTo(px + s * 0.22f, py + s * 0.04f, px, py + s * 0.10f)
                path.close()
                c.drawPath(path, p)
                fill(c, alpha(0xFF2A1A33.toInt(), a))
                c.drawCircle(px, py, s * 0.15f, p)
                fill(c, alpha(0xFFE07045.toInt(), a))
                c.drawCircle(px - s * 0.05f, py - s * 0.03f, s * 0.022f, p)
                c.drawCircle(px + s * 0.05f, py - s * 0.03f, s * 0.022f, p)
            }
            Mobs.SKELETON -> {
                fill(c, alpha(0xFFC9C2AC.toInt(), a))
                c.drawCircle(px, py - s * 0.12f, s * 0.15f, p)
                fill(c, alpha(0xFF3A3730.toInt(), a))
                c.drawCircle(px - s * 0.05f, py - s * 0.14f, s * 0.028f, p)
                c.drawCircle(px + s * 0.05f, py - s * 0.14f, s * 0.028f, p)
                c.drawRect(px - s * 0.06f, py - s * 0.02f, px + s * 0.06f, py + s * 0.01f, p)
                stroke(c, alpha(0xFFC9C2AC.toInt(), a), max(1f, s * 0.035f))
                for (i in 0..2) c.drawLine(px - s * 0.14f, py + s * 0.04f + i * s * 0.07f, px + s * 0.14f, py + s * 0.04f + i * s * 0.07f, line)
                c.drawLine(px - s * 0.18f, py + s * 0.24f, px - s * 0.08f, py + s * 0.06f, line)
                c.drawLine(px + s * 0.18f, py + s * 0.24f, px + s * 0.08f, py + s * 0.06f, line)
                stroke(c, alpha(0xFFB9C3C8.toInt(), a), max(1f, s * 0.035f))
                c.drawLine(px + s * 0.22f, py + s * 0.22f, px + s * 0.34f, py - s * 0.22f, line)
            }
            Mobs.FLAME -> {
                val f = 0.9f + 0.1f * sin(time * 8f + e.x)
                fill(c, alpha(0xFFE0652F.toInt(), (170 * alphaF).toInt()))
                c.drawOval(px - s * 0.24f * f, py - s * 0.42f * f, px + s * 0.24f * f, py + s * 0.24f, p)
                fill(c, alpha(0xFFFFB35C.toInt(), a))
                c.drawOval(px - s * 0.15f * f, py - s * 0.28f * f, px + s * 0.15f * f, py + s * 0.18f, p)
                fill(c, alpha(0xFF3A1510.toInt(), a))
                c.drawCircle(px - s * 0.07f, py - s * 0.08f, s * 0.03f, p)
                c.drawCircle(px + s * 0.07f, py - s * 0.08f, s * 0.03f, p)
            }
            Mobs.SHADE -> {
                fill(c, alpha(0xFF1A1424.toInt(), (210 * alphaF).toInt()))
                path.reset()
                path.moveTo(px, py - s * 0.34f)
                path.quadTo(px + s * 0.30f, py - s * 0.10f, px + s * 0.24f, py + s * 0.34f)
                path.lineTo(px + s * 0.10f, py + s * 0.22f)
                path.lineTo(px, py + s * 0.36f)
                path.lineTo(px - s * 0.10f, py + s * 0.22f)
                path.lineTo(px - s * 0.24f, py + s * 0.34f)
                path.quadTo(px - s * 0.30f, py - s * 0.10f, px, py - s * 0.34f)
                path.close()
                c.drawPath(path, p)
                fill(c, alpha(0xFFB9A7D9.toInt(), a))
                c.drawCircle(px - s * 0.08f, py - s * 0.12f, s * 0.025f, p)
                c.drawCircle(px + s * 0.08f, py - s * 0.12f, s * 0.025f, p)
                stroke(c, alpha(0xFF7F6FA8.toInt(), a), max(1f, s * 0.02f))
                c.drawLine(px + s * 0.20f, py + s * 0.20f, px + s * 0.30f, py - s * 0.10f, line)
            }
            Mobs.BOSS_CRYSTAL -> drawBossCrystal(c, px, py, s, time, a, alphaF)
            Mobs.BOSS_ASH -> drawBossAsh(c, px, py, s, time, a, alphaF)
            Mobs.BOSS_VOID -> drawBossVoid(c, px, py, s, time, a, alphaF)
            else -> {
                fill(c, alpha(def.color, a))
                c.drawCircle(px, py, s * 0.24f, p)
            }
        }
        if (hurt) {
            fill(c, alpha(0xFFFF5A5A.toInt(), 95))
            c.drawCircle(px, py, s * 0.45f, p)
        }
        // Elite crown.
        if (e.elite) {
            fill(c, alpha(Palette.AMBER_HI, a))
            path.reset()
            path.moveTo(px - s * 0.15f, py - s * 0.40f)
            path.lineTo(px - s * 0.10f, py - s * 0.50f)
            path.lineTo(px - s * 0.04f, py - s * 0.41f)
            path.lineTo(px, py - s * 0.51f)
            path.lineTo(px + s * 0.04f, py - s * 0.41f)
            path.lineTo(px + s * 0.10f, py - s * 0.50f)
            path.lineTo(px + s * 0.15f, py - s * 0.40f)
            path.close()
            c.drawPath(path, p)
        }
        // HP pip for wounded enemies.
        if (e.alive && e.hp < e.maxHp && !e.boss) {
            val w = s * 0.5f
            fill(c, alpha(0xFF000000.toInt(), 130))
            c.drawRect(cx - w / 2, cy + s * 0.40f, cx + w / 2, cy + s * 0.46f, p)
            fill(c, 0xFFE05A5A.toInt())
            c.drawRect(cx - w / 2, cy + s * 0.40f, cx - w / 2 + w * (e.hp.toFloat() / e.maxHp), cy + s * 0.46f, p)
        }
    }

    private fun drawBossCrystal(c: Canvas, cx: Float, cy: Float, s: Float, time: Float, a: Int, alphaF: Float) {
        fill(c, alpha(0xFF6E559A.toInt(), (110 * alphaF).toInt()))
        c.drawCircle(cx, cy, s * 0.70f, p)
        stroke(c, alpha(0xFFB9A0E8.toInt(), a), max(1f, s * 0.03f))
        for (i in 0..5) {
            val ang = i * 1.047f + time * 0.2f
            c.drawLine(cx, cy, cx + cos(ang) * s * 0.62f, cy + sin(ang) * s * 0.62f, line)
        }
        fill(c, alpha(0xFF8A6AC0.toInt(), a))
        c.drawCircle(cx, cy, s * 0.34f, p)
        fill(c, alpha(0xFFC4B0F0.toInt(), a))
        for (i in 0..4) {
            val ang = i * 1.256f
            path.reset()
            path.moveTo(cx + cos(ang) * s * 0.18f, cy + sin(ang) * s * 0.18f)
            path.lineTo(cx + cos(ang - 0.2f) * s * 0.52f, cy + sin(ang - 0.2f) * s * 0.52f)
            path.lineTo(cx + cos(ang + 0.2f) * s * 0.46f, cy + sin(ang + 0.2f) * s * 0.46f)
            path.close()
            c.drawPath(path, p)
        }
        fill(c, alpha(0xFFE05A5A.toInt(), a))
        c.drawCircle(cx - s * 0.12f, cy - s * 0.06f, s * 0.035f, p)
        c.drawCircle(cx + s * 0.12f, cy - s * 0.06f, s * 0.035f, p)
    }

    private fun drawBossAsh(c: Canvas, cx: Float, cy: Float, s: Float, time: Float, a: Int, alphaF: Float) {
        fill(c, alpha(0xFF8A4A2E.toInt(), a))
        c.drawOval(cx - s * 0.46f, cy - s * 0.30f, cx + s * 0.46f, cy + s * 0.40f, p)
        fill(c, alpha(0xFFB07858.toInt(), a))
        c.drawCircle(cx, cy - s * 0.28f, s * 0.22f, p)
        fill(c, alpha(0xFF2A1610.toInt(), a))
        c.drawCircle(cx - s * 0.08f, cy - s * 0.30f, s * 0.035f, p)
        c.drawCircle(cx + s * 0.08f, cy - s * 0.30f, s * 0.035f, p)
        fill(c, alpha(0xFFE0A050.toInt(), (140 * alphaF).toInt()))
        for (i in 0..4) {
            val x = cx + (i - 2) * s * 0.20f
            c.drawCircle(x, cy - s * 0.48f - sin(time * 3f + i) * s * 0.05f, s * 0.04f, p)
        }
        stroke(c, alpha(0xFF5B2E1C.toInt(), a), max(1f, s * 0.05f))
        c.drawLine(cx - s * 0.40f, cy + s * 0.08f, cx - s * 0.30f, cy + s * 0.42f, line)
        c.drawLine(cx + s * 0.40f, cy + s * 0.08f, cx + s * 0.30f, cy + s * 0.42f, line)
    }

    private fun drawBossVoid(c: Canvas, cx: Float, cy: Float, s: Float, time: Float, a: Int, alphaF: Float) {
        fill(c, alpha(0xFFD55C7A.toInt(), (50 * alphaF).toInt()))
        c.drawCircle(cx, cy, s * 0.75f, p)
        stroke(c, alpha(0xFFD55C7A.toInt(), a), max(1f, s * 0.03f))
        for (i in 0..2) c.drawCircle(cx, cy, s * (0.40f + i * 0.12f) + sin(time * 2f + i) * s * 0.02f, line)
        fill(c, alpha(0xFF231020.toInt(), a))
        path.reset()
        path.moveTo(cx, cy - s * 0.50f)
        path.quadTo(cx + s * 0.46f, cy - s * 0.10f, cx + s * 0.34f, cy + s * 0.44f)
        path.lineTo(cx + s * 0.12f, cy + s * 0.28f)
        path.lineTo(cx, cy + s * 0.48f)
        path.lineTo(cx - s * 0.12f, cy + s * 0.28f)
        path.lineTo(cx - s * 0.34f, cy + s * 0.44f)
        path.quadTo(cx - s * 0.46f, cy - s * 0.10f, cx, cy - s * 0.50f)
        path.close()
        c.drawPath(path, p)
        fill(c, alpha(0xFFE8C35C.toInt(), a))
        c.drawCircle(cx - s * 0.12f, cy - s * 0.16f, s * 0.05f, p)
        c.drawCircle(cx + s * 0.12f, cy - s * 0.16f, s * 0.05f, p)
        fill(c, alpha(0xFFD55C7A.toInt(), a))
        c.drawCircle(cx, cy + s * 0.02f, s * 0.07f, p)
    }

    // ------------------------------------------------------------------
    // UI icons
    // ------------------------------------------------------------------

    fun drawEmblem(c: Canvas, cls: HeroClass, cx: Float, cy: Float, r: Float, color: Int = Palette.AMBER_HI) {
        fill(c, alpha(color, 26))
        c.drawCircle(cx, cy, r, p)
        stroke(c, alpha(color, 120), max(1f, r * 0.05f))
        c.drawCircle(cx, cy, r, line)
        when (cls) {
            HeroClass.BLADE -> {
                stroke(c, color, max(1.5f, r * 0.09f))
                c.drawLine(cx - r * 0.38f, cy + r * 0.38f, cx + r * 0.38f, cy - r * 0.38f, line)
                c.drawLine(cx - r * 0.30f, cy + r * 0.10f, cx + r * 0.10f, cy + r * 0.42f, line)
                c.drawCircle(cx - r * 0.42f, cy + r * 0.44f, r * 0.09f, line)
            }
            HeroClass.EMBER -> {
                fill(c, color)
                path.reset()
                path.moveTo(cx, cy - r * 0.46f)
                path.quadTo(cx + r * 0.36f, cy - r * 0.02f, cx, cy + r * 0.44f)
                path.quadTo(cx - r * 0.36f, cy - r * 0.02f, cx, cy - r * 0.46f)
                path.close()
                c.drawPath(path, p)
                fill(c, Palette.AMBER_HI)
                c.drawCircle(cx, cy + r * 0.10f, r * 0.12f, p)
            }
            HeroClass.SHADE -> {
                stroke(c, color, max(1.5f, r * 0.075f))
                c.drawArc(RectF(cx - r * 0.44f, cy - r * 0.44f, cx + r * 0.10f, cy + r * 0.44f), -70f, 140f, false, line)
                c.drawLine(cx - r * 0.18f, cy - r * 0.44f, cx - r * 0.18f, cy + r * 0.44f, line)
                fill(c, color)
                c.drawCircle(cx - r * 0.04f, cy, r * 0.07f, p)
            }
        }
    }

    fun drawBagIcon(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        fill(c, alpha(color, 40))
        c.drawCircle(cx, cy, r, p)
        stroke(c, color, max(1f, r * 0.10f))
        rect.set(cx - r * 0.42f, cy - r * 0.22f, cx + r * 0.42f, cy + r * 0.42f)
        c.drawRoundRect(rect, r * 0.16f, r * 0.16f, line)
        c.drawLine(cx - r * 0.24f, cy - r * 0.22f, cx - r * 0.16f, cy - r * 0.48f, line)
        c.drawLine(cx + r * 0.24f, cy - r * 0.22f, cx + r * 0.16f, cy - r * 0.48f, line)
        c.drawLine(cx - r * 0.16f, cy - r * 0.48f, cx + r * 0.16f, cy - r * 0.48f, line)
        c.drawCircle(cx, cy + r * 0.10f, r * 0.06f, line)
    }

    fun drawHourglassIcon(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        fill(c, alpha(color, 40))
        c.drawCircle(cx, cy, r, p)
        stroke(c, color, max(1f, r * 0.09f))
        c.drawLine(cx - r * 0.34f, cy - r * 0.40f, cx + r * 0.34f, cy - r * 0.40f, line)
        c.drawLine(cx - r * 0.34f, cy + r * 0.40f, cx + r * 0.34f, cy + r * 0.40f, line)
        c.drawLine(cx - r * 0.26f, cy - r * 0.40f, cx + r * 0.26f, cy + r * 0.40f, line)
        c.drawLine(cx + r * 0.26f, cy - r * 0.40f, cx - r * 0.26f, cy + r * 0.40f, line)
        fill(c, color)
        c.drawCircle(cx, cy + r * 0.18f, r * 0.10f, p)
    }

    fun drawMapIcon(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        fill(c, alpha(color, 40))
        c.drawCircle(cx, cy, r, p)
        stroke(c, color, max(1f, r * 0.08f))
        path.reset()
        path.moveTo(cx - r * 0.38f, cy - r * 0.30f)
        path.lineTo(cx - r * 0.12f, cy - r * 0.40f)
        path.lineTo(cx + r * 0.12f, cy - r * 0.26f)
        path.lineTo(cx + r * 0.38f, cy - r * 0.36f)
        path.lineTo(cx + r * 0.38f, cy + r * 0.30f)
        path.lineTo(cx + r * 0.12f, cy + r * 0.40f)
        path.lineTo(cx - r * 0.12f, cy + r * 0.26f)
        path.lineTo(cx - r * 0.38f, cy + r * 0.36f)
        path.close()
        c.drawPath(path, line)
        c.drawLine(cx - r * 0.12f, cy - r * 0.40f, cx - r * 0.12f, cy + r * 0.26f, line)
        c.drawLine(cx + r * 0.12f, cy - r * 0.26f, cx + r * 0.12f, cy + r * 0.40f, line)
    }

    fun drawMenuIcon(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        fill(c, alpha(color, 40))
        c.drawCircle(cx, cy, r, p)
        stroke(c, color, max(1f, r * 0.11f))
        for (i in -1..1) c.drawLine(cx - r * 0.34f, cy + i * r * 0.24f, cx + r * 0.34f, cy + i * r * 0.24f, line)
    }

    fun drawWaitIcon(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) = drawHourglassIcon(c, cx, cy, r, color)

    fun drawCrosshair(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, time: Float) {
        stroke(c, color, max(1f, r * 0.05f))
        c.drawCircle(cx, cy, r * (0.8f + 0.08f * sin(time * 4f)), line)
        c.drawLine(cx - r * 1.25f, cy, cx - r * 0.35f, cy, line)
        c.drawLine(cx + r * 0.35f, cy, cx + r * 1.25f, cy, line)
        c.drawLine(cx, cy - r * 1.25f, cx, cy - r * 0.35f, line)
        c.drawLine(cx, cy + r * 0.35f, cx, cy + r * 1.25f, line)
        fill(c, alpha(color, 120))
        c.drawCircle(cx, cy, r * 0.12f, p)
    }

    fun drawDpad(c: Canvas, cx: Float, cy: Float, r: Float, time: Float, active: Int) {
        fill(c, alpha(0xFF000000.toInt(), 70))
        c.drawCircle(cx, cy, r * 1.10f, p)
        stroke(c, alpha(Palette.STROKE, 120), max(1f, r * 0.02f))
        c.drawCircle(cx, cy, r * 1.02f, line)
        for (i in 0..3) {
            val ang = i * Math.PI.toFloat() / 2f - Math.PI.toFloat() / 2f
            val dx = cos(ang)
            val dy = sin(ang)
            val ac = if (active == i) Palette.AMBER_HI else Palette.TEXT_DIM
            fill(c, alpha(ac, if (active == i) 110 else 55))
            c.drawCircle(cx + dx * r * 0.56f, cy + dy * r * 0.56f, r * 0.28f, p)
            fill(c, ac)
            path.reset()
            path.moveTo(cx + dx * r * 0.78f, cy + dy * r * 0.78f)
            path.lineTo(cx + dx * r * 0.38f - dy * r * 0.18f, cy + dy * r * 0.38f + dx * r * 0.18f)
            path.lineTo(cx + dx * r * 0.38f + dy * r * 0.18f, cy + dy * r * 0.38f - dx * r * 0.18f)
            path.close()
            c.drawPath(path, p)
        }
        fill(c, alpha(Palette.AMBER, 70))
        c.drawCircle(cx, cy, r * 0.12f, p)
    }

    fun drawBar(c: Canvas, x: Float, y: Float, w: Float, h: Float, frac: Float, color: Int, back: Int, radius: Float) {
        fill(c, back)
        rect.set(x, y, x + w, y + h)
        c.drawRoundRect(rect, radius, radius, p)
        val f = frac.coerceIn(0f, 1f)
        if (f > 0f) {
            fill(c, color)
            rect.set(x, y, x + max(h, w * f), y + h)
            c.drawRoundRect(rect, radius, radius, p)
            fill(c, alpha(0xFFFFFFFF.toInt(), 40))
            rect.set(x + h * 0.2f, y + h * 0.16f, x + max(h, w * f) - h * 0.2f, y + h * 0.42f)
            c.drawRoundRect(rect, radius, radius, p)
        }
        stroke(c, alpha(0xFF000000.toInt(), 120), max(1f, h * 0.08f))
        rect.set(x, y, x + w, y + h)
        c.drawRoundRect(rect, radius, radius, line)
    }
}
