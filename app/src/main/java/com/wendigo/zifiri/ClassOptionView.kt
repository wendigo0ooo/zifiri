package com.wendigo.zifiri

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.max
import kotlin.math.min

class ClassOptionView(context: Context, val hero: HeroClass) : View(context) {
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val txtBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val rect = RectF()

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "${hero.title}, ${hero.subtitle}. ${hero.desc}"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED)
            dp(320f).toInt() else MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, dp(124f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = dp(14f)
        rect.set(0f, 0f, w, h)

        fill.color = Art.alpha(Palette.PANEL, if (isPressed) 245 else 220)
        canvas.drawRoundRect(rect, r, r, fill)
        line.color = Art.alpha(hero.accent, if (isPressed) 230 else 120)
        line.strokeWidth = max(1f, dp(if (isPressed) 1.8f else 1.1f))
        canvas.drawRoundRect(rect, r, r, line)

        fill.color = Art.alpha(hero.accent, 40)
        rect.set(0f, dp(14f), dp(3.5f), h - dp(14f))
        canvas.drawRoundRect(rect, dp(2f), dp(2f), fill)

        val emblemR = dp(25f)
        val ex = dp(18f) + emblemR
        val ey = h / 2f
        Art.drawEmblem(canvas, hero, ex, ey, emblemR, hero.accent)

        val left = ex + emblemR + dp(15f)
        val right = w - dp(16f)
        txtBold.textSize = dp(16f)
        txtBold.color = Palette.TEXT
        canvas.drawText(hero.title, left, dp(31f), txtBold)

        txt.textSize = dp(10f)
        txt.color = hero.accent
        canvas.drawText(hero.subtitle.uppercase(), left, dp(46f), txt)

        txt.textSize = dp(10.5f)
        txt.color = Palette.TEXT_DIM
        val maxW = right - left
        val lines = wrap(hero.desc, maxW, 2)
        var y = dp(64f)
        for (l in lines) {
            canvas.drawText(l, left, y, txt)
            y += dp(13f)
        }

        txt.textSize = dp(9.5f)
        txt.color = Palette.TEXT_FAINT
        canvas.drawText(
            "CAN ${hero.hp}   GÜÇ ${hero.atkMin}-${hero.atkMax}   DİRENÇ ${hero.def}",
            left, h - dp(14f), txt
        )

        txtBold.textSize = dp(10f)
        txtBold.color = Art.alpha(hero.accent, 230)
        txtBold.textAlign = Paint.Align.RIGHT
        canvas.drawText("SEÇ ›", w - dp(16f), dp(31f), txtBold)
        txtBold.textAlign = Paint.Align.LEFT
    }

    private fun wrap(text: String, maxWidth: Float, maxLines: Int): List<String> {
        val words = text.split(' ')
        val out = ArrayList<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (txt.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) out.add(current)
                current = word
                if (out.size == maxLines) break
            }
        }
        if (out.size < maxLines && current.isNotEmpty()) out.add(current)
        if (out.size >= maxLines) {
            val last = out[maxLines - 1]
            if (txt.measureText(last) > maxWidth) {
                val fit = txt.breakText(last, true, maxWidth, null)
                out[maxLines - 1] = last.substring(0, max(0, fit - 1)) + "…"
            }
        }
        return out
    }
}
