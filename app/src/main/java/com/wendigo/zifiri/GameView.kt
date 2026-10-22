package com.wendigo.zifiri

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class GameView(context: Context, val game: Game, private val host: Host) : View(context) {
    interface Host {
        fun openInventory()
        fun openPause()
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val txtBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val txtSerif = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("serif", Typeface.BOLD)
    }
    private val rect = RectF()
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var time = 0f
    private var lastFrame = 0L
    private var camX = 0f
    private var camY = 0f
    private var cameraInit = false
    private var tileSize = 40f
    private var shake = 0f

    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, var maxLife: Float, var color: Int, var size: Float
    )

    private class FloatText(val x: Float, val y: Float, val text: String, val color: Int, val born: Long)
    private class Shot(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val color: Int, val kind: Int, val born: Long)
    private class Ring(val x: Float, val y: Float, val color: Int, val born: Long)

    private val particles = ArrayList<Particle>()
    private val floats = ArrayList<FloatText>()
    private val shots = ArrayList<Shot>()
    private val rings = ArrayList<Ring>()

    private var bannerTitle = ""
    private var bannerSub = ""
    private var bannerColor = Palette.AMBER_HI
    private var bannerBorn = 0L

    private var aimMode = false
    private var mapOpen = false
    private var walkTarget: Pair<Int, Int>? = null
    private var nextWalkAt = 0L
    private var heldDir = -1
    private var nextHoldAt = 0L
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var dragMoved = false
    private var uiConsumed = false

    private var flashSkill = 0L
    private var flashBag = 0L
    private var flashWait = 0L
    private var flashMap = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAttachedToWindow) return
            val now = frameTimeNanos / 1_000_000L
            val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame).coerceAtMost(80L)) / 1000f
            lastFrame = now
            time = (SystemClock.uptimeMillis() % 1_000_000L) / 1000f
            processEvents()
            update(dt)
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        Sfx.init(context)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrame = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }

    fun stopWalking() {
        walkTarget = null
        heldDir = -1
        aimMode = false
    }

    fun refresh() {
        invalidate()
    }

    // ------------------------------------------------------------------
    // Events / update
    // ------------------------------------------------------------------

    private fun processEvents() {
        if (game.events.isEmpty()) return
        val now = System.currentTimeMillis()
        val it = game.events.iterator()
        while (it.hasNext()) {
            when (val ev = it.next()) {
                is VisualEvent.FloatText -> floats.add(
                    FloatText(ev.x + 0.5f, ev.y + 0.2f, ev.text, ev.color, now)
                )
                is VisualEvent.Burst -> {
                    val count = ev.count.coerceIn(1, 48)
                    for (i in 0 until count) {
                        val a = (Math.random() * Math.PI * 2).toFloat()
                        val sp = (0.7f + Math.random().toFloat() * 1.9f) * ev.power
                        particles.add(
                            Particle(
                                ev.x + 0.5f, ev.y + 0.5f,
                                cos(a) * sp, sin(a) * sp,
                                0.45f + Math.random().toFloat() * 0.55f,
                                1f, ev.color,
                                tileSize * (0.03f + Math.random().toFloat() * 0.05f)
                            )
                        )
                    }
                    if (particles.size > 320) {
                        while (particles.size > 320) particles.removeAt(0)
                    }
                }
                is VisualEvent.Projectile -> shots.add(
                    Shot(ev.x0 + 0.5f, ev.y0 + 0.5f, ev.x1 + 0.5f, ev.y1 + 0.5f, ev.color, ev.kind, now)
                )
                is VisualEvent.Ring -> rings.add(Ring(ev.x + 0.5f, ev.y + 0.5f, ev.color, now))
                is VisualEvent.Shake -> if (Store.shakeScreen) shake = min(18f, shake + ev.amount)
                is VisualEvent.Sound -> {
                    Sfx.play(ev.id, 0.94f + (Math.random().toFloat() * 0.12f))
                    if (Store.haptic && ev.id in HAPTIC_SOUNDS) {
                        val ms = when (ev.id) {
                            "crit", "levelup", "boss" -> 45L
                            "hit", "hurt" -> 18L
                            else -> 26L
                        }
                        Sfx.vibrate(context, ms)
                    }
                }
                is VisualEvent.Banner -> {
                    bannerTitle = ev.title
                    bannerSub = ev.sub
                    bannerColor = ev.color
                    bannerBorn = now
                }
            }
            it.remove()
        }
    }

    private fun update(dt: Float) {
        val now = System.currentTimeMillis()

        if (shake > 0.1f) shake = max(0f, shake - dt * 40f)

        val pit = particles.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            p.life -= dt
            if (p.life <= 0f) {
                pit.remove()
                continue
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vx *= (1f - dt * 2.2f)
            p.vy *= (1f - dt * 2.2f)
            p.vy -= dt * 0.35f
        }
        floats.removeAll { now - it.born > 1100 }
        shots.removeAll { now - it.born > 520 }
        rings.removeAll { now - it.born > 650 }

        if (walkTarget != null) {
            if (game.gameOver || game.victory) {
                walkTarget = null
            } else if (now >= nextWalkAt) {
                stepTowardTarget()
            }
        }

        if (heldDir >= 0 && now >= nextHoldAt) {
            doHeldMove(heldDir)
            nextHoldAt = now + 155
        }
    }

    private fun stepTowardTarget() {
        val target = walkTarget ?: return
        if (target.first == game.player.x && target.second == game.player.y) {
            walkTarget = null
            return
        }
        if (game.enemyAt(target.first, target.second) != null) {
            walkTarget = null
            return
        }
        val path = Pathfinder.findPath(
            game.level, game.player.x, game.player.y, target.first, target.second,
            { x, y ->
                val t = game.level.tileOrNull(x, y)
                val door = t?.isDoor() == true
                (!game.level.isWalkable(x, y) && !door) || game.enemyAt(x, y) != null
            }
        )
        if (path.isEmpty()) {
            walkTarget = null
            return
        }
        val next = path.first()
        val dx = next.first - game.player.x
        val dy = next.second - game.player.y
        if (max(abs(dx), abs(dy)) != 1) {
            walkTarget = null
            return
        }
        if (!game.tryMove(dx, dy)) {
            walkTarget = null
            return
        }
        nextWalkAt = System.currentTimeMillis() + 100
        if (game.gameOver || game.victory) walkTarget = null
    }

    private fun doHeldMove(dir: Int) {
        val d = when (dir) {
            0 -> 0 to -1
            1 -> 1 to 0
            2 -> 0 to 1
            else -> -1 to 0
        }
        walkTarget = null
        game.tryMove(d.first, d.second)
    }

    private fun startWalk(tx: Int, ty: Int) {
        walkTarget = tx to ty
        nextWalkAt = System.currentTimeMillis()
    }

    // ------------------------------------------------------------------
    // Touch
    // ------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val now = System.currentTimeMillis()

        if (mapOpen) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                mapOpen = false
                performClick()
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                downAt = now
                dragMoved = false
                uiConsumed = false

                if (hitMenu(x, y)) {
                    uiConsumed = true
                    stopWalking()
                    host.openPause()
                    return true
                }
                if (hitBag(x, y)) {
                    uiConsumed = true
                    stopWalking()
                    flashBag = now
                    Sfx.play("open")
                    host.openInventory()
                    return true
                }
                if (hitSkill(x, y)) {
                    uiConsumed = true
                    flashSkill = now
                    onSkillPressed()
                    return true
                }
                if (hitWait(x, y)) {
                    uiConsumed = true
                    flashWait = now
                    game.waitTurn()
                    Sfx.play("wait")
                    return true
                }
                if (hitMap(x, y)) {
                    uiConsumed = true
                    flashMap = now
                    mapOpen = true
                    return true
                }
                val dir = hitDpad(x, y)
                if (dir >= 0) {
                    uiConsumed = true
                    heldDir = dir
                    doHeldMove(dir)
                    nextHoldAt = now + 300
                    return true
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (uiConsumed) return true
                if (abs(x - downX) > dp(18f) || abs(y - downY) > dp(18f)) dragMoved = true
                if (heldDir >= 0) {
                    val dir = hitDpad(x, y)
                    if (dir >= 0 && dir != heldDir) {
                        heldDir = dir
                        doHeldMove(dir)
                        nextHoldAt = now + 300
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                heldDir = -1
                if (uiConsumed) return true
                if (!dragMoved && now - downAt < 450) {
                    handleTap(x, y)
                    performClick()
                } else if (dragMoved) {
                    val dx = x - downX
                    val dy = y - downY
                    if (max(abs(dx), abs(dy)) > dp(42f)) {
                        walkTarget = null
                        if (abs(dx) > abs(dy)) game.tryMove(if (dx > 0) 1 else -1, 0)
                        else game.tryMove(0, if (dy > 0) 1 else -1)
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                heldDir = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleTap(sx: Float, sy: Float) {
        val tx = ((sx + camX) / tileSize).toInt()
        val ty = ((sy + camY) / tileSize).toInt()
        if (tx !in 0 until MAP_W || ty !in 0 until MAP_H) return
        val p = game.player
        if (aimMode) {
            if (game.useSkillAt(tx, ty)) Sfx.play("skill")
            else Sfx.play("empty")
            aimMode = false
            return
        }
        val t = game.level.tile(tx, ty)
        val enemy = game.enemyAt(tx, ty)
        if (enemy != null && t.visible) {
            walkTarget = null
            game.tapTile(tx, ty)
            return
        }
        if (tx == p.x && ty == p.y) {
            walkTarget = null
            game.tapTile(tx, ty)
            return
        }
        val adjacent = game.isAdjacent(p.x, p.y, tx, ty)
        if (adjacent) {
            walkTarget = null
            if (t.f == Feature.CHEST || t.f == Feature.LOCKED_CHEST ||
                t.f == Feature.SHOP || t.f == Feature.ALTAR || t.isStairs()
            ) {
                game.tapTile(tx, ty)
            } else if (!game.tryMove(tx - p.x, ty - p.y)) {
                game.tapTile(tx, ty)
            }
            return
        }
        if (!t.explored) {
            game.message("Orayı göremiyorsun. Karanlıkta yürümezsin.", Palette.TEXT_FAINT)
            Sfx.play("empty")
            return
        }
        if (!t.walkable()) {
            game.message("Oraya yürüyemezsin.", Palette.TEXT_FAINT)
            Sfx.play("empty")
            return
        }
        startWalk(tx, ty)
    }

    private fun onSkillPressed() {
        val cls = game.heroClass
        if (cls == HeroClass.BLADE) {
            if (game.useSkillAt(game.player.x, game.player.y)) Sfx.play("skill")
            else Sfx.play("empty")
            return
        }
        if (game.player.energyNow < cls.skillCost) {
            game.message("Yeterli enerjin yok.", Palette.TEXT_FAINT)
            Sfx.play("empty")
            return
        }
        aimMode = !aimMode
        walkTarget = null
        if (aimMode) {
            game.message(
                if (cls == HeroClass.EMBER) "Kor Yağmuru için bir kare seç." else "Sis Adımı için bir kare seç.",
                cls.accent
            )
        }
    }

    // ------------------------------------------------------------------
    // Geometry / hit tests
    // ------------------------------------------------------------------

    private fun topInset(): Float {
        val insets = if (Build.VERSION.SDK_INT >= 23) rootWindowInsets else null
        @Suppress("DEPRECATION")
        return ((insets?.systemWindowInsetTop ?: 0)).toFloat()
    }

    private val padR: Float get() = min(width, height) * 0.125f
    private val padCx: Float get() = dp(16f) + padR
    private val padCy: Float get() = height - dp(20f) - padR

    private fun skillR(): Float = padR * 0.62f
    private fun skillCx(): Float = width - dp(24f) - skillR()
    private fun skillCy(): Float = height - dp(26f) - skillR()

    private fun bagR(): Float = padR * 0.40f
    private fun bagCx(): Float = skillCx() - skillR() - bagR() - dp(18f)
    private fun bagCy(): Float = height - dp(30f) - bagR()

    private fun waitR(): Float = bagR()
    private fun waitCx(): Float = bagCx()
    private fun waitCy(): Float = bagCy() - bagR() * 2f - dp(16f)

    private fun mapR(): Float = bagR() * 0.90f
    private fun mapCx(): Float = bagCx() - bagR() - mapR() - dp(12f)
    private fun mapCy(): Float = bagCy()

    private fun menuCx(): Float = width - dp(30f)
    private fun menuCy(): Float = topInset() + dp(30f)
    private fun menuR(): Float = dp(19f)

    private fun miniSize(): Float = dp(88f)
    private fun miniX(): Float = width - dp(12f) - miniSize()
    private fun miniY(): Float = topInset() + dp(58f)

    private fun hitCircle(x: Float, y: Float, cx: Float, cy: Float, r: Float): Boolean {
        val dx = x - cx
        val dy = y - cy
        return dx * dx + dy * dy <= r * r * 1.25f
    }

    private fun hitMenu(x: Float, y: Float) = hitCircle(x, y, menuCx(), menuCy(), menuR())
    private fun hitBag(x: Float, y: Float) = hitCircle(x, y, bagCx(), bagCy(), bagR() * 1.15f)
    private fun hitSkill(x: Float, y: Float) = hitCircle(x, y, skillCx(), skillCy(), skillR() * 1.12f)
    private fun hitWait(x: Float, y: Float) = hitCircle(x, y, waitCx(), waitCy(), waitR() * 1.15f)
    private fun hitMap(x: Float, y: Float) = hitCircle(x, y, mapCx(), mapCy(), mapR() * 1.15f)

    private fun hitDpad(x: Float, y: Float): Int {
        val dx = x - padCx
        val dy = y - padCy
        if (dx * dx + dy * dy > (padR * 1.25f) * (padR * 1.25f)) return -1
        return if (abs(dx) > abs(dy)) {
            if (dx > 0) 1 else 3
        } else {
            if (dy > 0) 2 else 0
        }
    }

    // ------------------------------------------------------------------
    // Draw
    // ------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val now = System.currentTimeMillis()
        canvas.drawColor(Palette.BG)

        val p = game.player
        tileSize = min(width / 11.6f, height / 16.2f)
        val mapPxW = MAP_W * tileSize
        val mapPxH = MAP_H * tileSize
        var targetX = (p.x + 0.5f) * tileSize - width / 2f
        var targetY = (p.y + 0.5f) * tileSize - height / 2f
        targetX = if (mapPxW <= width) (mapPxW - width) / 2f else targetX.coerceIn(0f, mapPxW - width)
        targetY = if (mapPxH <= height) (mapPxH - height) / 2f else targetY.coerceIn(0f, mapPxH - height)
        if (!cameraInit) {
            camX = targetX
            camY = targetY
            cameraInit = true
        } else {
            camX += (targetX - camX) * 0.18f
            camY += (targetY - camY) * 0.18f
        }

        val save = canvas.save()
        if (shake > 0.2f) {
            val sx = ((Math.random() - 0.5) * shake * 2.4).toFloat()
            val sy = ((Math.random() - 0.5) * shake * 1.8).toFloat()
            canvas.translate(sx, sy)
        }

        drawWorld(canvas, now)
        drawGlow(canvas)

        drawEffects(canvas, now)
        drawActors(canvas, now)
        drawParticles(canvas, now)
        drawFloats(canvas, now)

        canvas.restoreToCount(save)

        drawHud(canvas, now)
        drawBanner(canvas, now)
        if (mapOpen) drawMapOverlay(canvas)
    }

    private fun litOf(t: Tile): Float = if (t.visible) 1f else 0.30f

    private fun drawWorld(canvas: Canvas, now: Long) {
        val viewL = camX
        val viewT = camY
        val viewR = camX + width
        val viewB = camY + height
        val x0 = max(0, (viewL / tileSize).toInt() - 1)
        val y0 = max(0, (viewT / tileSize).toInt() - 1)
        val x1 = min(MAP_W - 1, (viewR / tileSize).toInt() + 1)
        val y1 = min(MAP_H - 1, (viewB / tileSize).toInt() + 1)
        val p = game.player
        val radius = game.lightRadius().toFloat()

        for (y in y0..y1) {
            for (x in x0..x1) {
                val t = game.level.tile(x, y)
                if (!t.explored) continue
                val cx = (x + 0.5f) * tileSize - camX
                val cy = (y + 0.5f) * tileSize - camY
                if (cx < -tileSize || cy < -tileSize || cx > width + tileSize || cy > height + tileSize) continue
                val lit = litOf(t)
                when (t.t) {
                    TileType.WALL -> Art.drawWall(canvas, cx, cy, tileSize, game.depth, x, y, lit)
                    TileType.WALL_TORCH -> {
                        Art.drawWall(canvas, cx, cy, tileSize, game.depth, x, y, lit)
                    }
                    TileType.FLOOR -> Art.drawFloor(canvas, cx, cy, tileSize, game.depth, x, y, time, lit)
                    TileType.WATER -> Art.drawWater(canvas, cx, cy, tileSize, game.depth, x, y, time, lit)
                    TileType.LAVA -> Art.drawLava(canvas, cx, cy, tileSize, time, x, y, lit)
                    TileType.RUBBLE -> Art.drawRubble(canvas, cx, cy, tileSize, game.depth, x, y, lit)
                    TileType.DOOR -> {
                        Art.drawFloor(canvas, cx, cy, tileSize, game.depth, x, y, time, lit)
                        Art.drawDoor(canvas, cx, cy, tileSize, t.doorOpen, lit)
                    }
                    TileType.STAIRS -> {
                        Art.drawFloor(canvas, cx, cy, tileSize, game.depth, x, y, time, lit)
                        Art.drawStairs(canvas, cx, cy, tileSize, time, lit)
                    }
                    TileType.PORTAL -> Art.drawPortal(canvas, cx, cy, tileSize, time, lit)
                    TileType.PILLAR -> Art.drawPillar(canvas, cx, cy, tileSize, game.depth, x, y, lit)
                    TileType.VOID -> {
                        bg.color = 0xFF07040A.toInt()
                        canvas.drawRect(cx - tileSize / 2, cy - tileSize / 2, cx + tileSize / 2, cy + tileSize / 2, bg)
                    }
                }

                if (t.f != Feature.NONE && t.type != TileType.WALL.ordinal && t.type != TileType.WALL_TORCH.ordinal) {
                    if (t.isTrap() && t.featureData == 0) {
                        // hidden
                    } else {
                        Art.drawFeature(canvas, t.f, cx, cy, tileSize, time, lit, t.featureData)
                    }
                }

                // Lighting overlay.
                if (!t.visible) {
                    fill.color = 0xA8000000.toInt()
                    canvas.drawRect(cx - tileSize / 2, cy - tileSize / 2, cx + tileSize / 2, cy + tileSize / 2, fill)
                } else {
                    val d = max(abs(x - p.x), abs(y - p.y)).toFloat()
                    val a = (((d / max(1f, radius)) * 0.95f).coerceIn(0f, 1f) * 138f).toInt()
                    if (a > 3) {
                        fill.color = Color.argb(a, 0, 0, 0)
                        canvas.drawRect(cx - tileSize / 2, cy - tileSize / 2, cx + tileSize / 2, cy + tileSize / 2, fill)
                    }
                }

                if (t.type == TileType.WALL_TORCH.ordinal && t.visible) {
                    Art.drawTorchFlame(canvas, cx, cy - tileSize * 0.28f, tileSize, time, x * 31 + y)
                    drawTorchGlow(canvas, cx, cy, lit)
                }
            }
        }

        // Ground items on top of tiles.
        for (g in game.groundItems) {
            if (!game.level.inBounds(g.x, g.y)) continue
            val t = game.level.tile(g.x, g.y)
            if (!t.explored) continue
            val cx = (g.x + 0.5f) * tileSize - camX
            val cy = (g.y + 0.5f) * tileSize - camY
            if (cx < -tileSize || cy < -tileSize || cx > width + tileSize || cy > height + tileSize) continue
            Art.drawItem(canvas, g.item, cx, cy, tileSize, time, t.visible)
            if (!t.visible) {
                fill.color = 0x78000000
                canvas.drawCircle(cx, cy, tileSize * 0.45f, fill)
            }
        }
    }

    private fun drawTorchGlow(canvas: Canvas, cx: Float, cy: Float, lit: Float) {
        val r = tileSize * (1.6f + 0.10f * sin(time * 2.2f + cx))
        glowPaint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(Art.alpha(0xFFFFB45C.toInt(), (70 * lit).toInt()), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, glowPaint)
        glowPaint.shader = null
    }

    private fun drawGlow(canvas: Canvas) {
        val p = game.player
        val px = (p.x + 0.5f) * tileSize - camX
        val py = (p.y + 0.5f) * tileSize - camY
        val r = tileSize * (game.lightRadius() * 1.15f)
        glowPaint.shader = RadialGradient(
            px, py, r,
            intArrayOf(Art.alpha(0xFFFFC170.toInt(), 26), Art.alpha(0xFFFFC170.toInt(), 8), Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(px, py, r, glowPaint)
        glowPaint.shader = null
    }

    private fun drawActors(canvas: Canvas, now: Long) {
        val p = game.player
        val px = actorScreenX(p, now)
        val py = actorScreenY(p, now)
        val playerAlpha = if (p.invisibleTurns > 0) 0.55f else 1f
        Art.drawPlayer(
            canvas, game.heroClass, px, py, tileSize, time,
            p.facingX, p.facingY,
            now < p.attackUntil, now < p.hurtUntil, playerAlpha
        )

        for (e in game.enemies) {
            if (!e.alive) {
                val d = now - e.deathAt
                if (d > 1400) continue
                val deadTile = game.level.tileOrNull(e.x, e.y) ?: continue
                if (!deadTile.visible) continue
                val a = (1f - d / 1400f).coerceIn(0f, 1f)
                Art.drawEnemy(canvas, e, actorScreenX(e, now), actorScreenY(e, now), tileSize, time, false, a)
                continue
            }
            val t = game.level.tileOrNull(e.x, e.y) ?: continue
            if (!t.visible) continue
            val alpha = if (e.invisibleTurns > 0) 0.35f else 1f
            Art.drawEnemy(canvas, e, actorScreenX(e, now), actorScreenY(e, now), tileSize, time, now < e.hurtUntil, alpha)
            if (e.boss) {
                drawBossBar(canvas, e, actorScreenY(e, now))
            }
        }
    }

    private fun actorScreenX(a: Actor, now: Long): Float {
        val anim = if (a.animStart > 0L) ((now - a.animStart) / 170f).coerceIn(0f, 1f) else 1f
        val ease = 1f - (1f - anim) * (1f - anim)
        val world = a.animFromX + (a.x - a.animFromX) * ease
        return (world + 0.5f) * tileSize - camX
    }

    private fun actorScreenY(a: Actor, now: Long): Float {
        val anim = if (a.animStart > 0L) ((now - a.animStart) / 170f).coerceIn(0f, 1f) else 1f
        val ease = 1f - (1f - anim) * (1f - anim)
        val world = a.animFromY + (a.y - a.animFromY) * ease
        return (world + 0.5f) * tileSize - camY
    }

    private fun drawBossBar(canvas: Canvas, e: Actor, sy: Float) {
        val w = min(width * 0.62f, tileSize * 6f)
        val x = width / 2f - w / 2f
        val y = topInset() + dp(92f)
        fill.color = Art.alpha(Palette.BLACK, 170)
        rect.set(x - dp(6f), y - dp(6f), x + w + dp(6f), y + dp(20f))
        canvas.drawRoundRect(rect, dp(7f), dp(7f), fill)
        txtBold.textSize = dp(10f)
        txtBold.color = Palette.TEXT
        txtBold.textAlign = Paint.Align.CENTER
        canvas.drawText(e.name.uppercase(), width / 2f, y + dp(9f), txtBold)
        txtBold.textAlign = Paint.Align.LEFT
        Art.drawBar(canvas, x, y + dp(12f), w, dp(4f), e.hp.toFloat() / max(1, e.maxHp), Palette.RED, 0xFF2A1216.toInt(), dp(2f))
    }

    private fun drawEffects(canvas: Canvas, now: Long) {
        for (r in rings) {
            val f = ((now - r.born) / 620f).coerceIn(0f, 1f)
            val cx = r.x * tileSize - camX
            val cy = r.y * tileSize - camY
            line.color = Art.alpha(r.color, ((1f - f) * 200).toInt())
            line.strokeWidth = max(1.5f, tileSize * 0.05f)
            canvas.drawCircle(cx, cy, tileSize * (0.25f + f * 1.9f), line)
        }
        for (s in shots) {
            val f = ((now - s.born) / 480f).coerceIn(0f, 1f)
            val x0 = s.x0 * tileSize - camX
            val y0 = s.y0 * tileSize - camY
            val x1 = s.x1 * tileSize - camX
            val y1 = s.y1 * tileSize - camY
            val ease = f * f * (3 - 2 * f)
            val hx = x0 + (x1 - x0) * ease
            val hy = y0 + (y1 - y0) * ease
            line.color = Art.alpha(s.color, ((1f - f * 0.6f) * 230).toInt())
            line.strokeWidth = max(2f, tileSize * if (s.kind == 2) 0.10f else 0.05f)
            val tail = if (s.kind == 2) 0.5f else 0.30f
            val tx = hx + (x0 - x1) * tail * 0.2f
            val ty = hy + (y0 - y1) * tail * 0.2f
            canvas.drawLine(tx, ty, hx, hy, line)
            fill.color = Art.alpha(s.color, 220)
            canvas.drawCircle(hx, hy, tileSize * (if (s.kind == 1) 0.14f else 0.075f), fill)
            if (s.kind == 1) {
                fill.color = Art.alpha(0xFFFFE0A0.toInt(), 180)
                canvas.drawCircle(hx, hy, tileSize * 0.05f, fill)
            }
        }
    }

    private fun drawParticles(canvas: Canvas, now: Long) {
        for (p in particles) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            fill.color = Art.alpha(p.color, (a * 220).toInt())
            val px = p.x * tileSize - camX
            val py = p.y * tileSize - camY
            canvas.drawCircle(px, py, p.size * (0.5f + a * 0.7f), fill)
        }
    }

    private fun drawFloats(canvas: Canvas, now: Long) {
        for (f in floats) {
            val age = now - f.born
            val t = (age / 1100f).coerceIn(0f, 1f)
            val px = f.x * tileSize - camX
            val py = f.y * tileSize - camY - t * tileSize * 0.9f
            val a = ((1f - t) * 255).toInt().coerceIn(0, 255)
            txtBold.textSize = dp(12f)
            txtBold.color = Art.alpha(f.color, a)
            txtBold.textAlign = Paint.Align.CENTER
            txtBold.setShadowLayer(dp(3f), 0f, dp(1.5f), 0xCC000000.toInt())
            canvas.drawText(f.text, px, py, txtBold)
            txtBold.clearShadowLayer()
            txtBold.textAlign = Paint.Align.LEFT
        }
    }

    // ------------------------------------------------------------------
    // HUD
    // ------------------------------------------------------------------

    private fun drawHud(canvas: Canvas, now: Long) {
        val p = game.player
        val top = topInset() + dp(10f)
        val left = dp(10f)
        val panelW = min(width * 0.60f, width - miniSize() - dp(34f))
        val panelH = dp(84f)

        fill.color = Art.alpha(Palette.PANEL, 214)
        rect.set(left, top, left + panelW, top + panelH)
        canvas.drawRoundRect(rect, dp(12f), dp(12f), fill)
        line.color = Art.alpha(Palette.STROKE, 200)
        line.strokeWidth = max(1f, dp(1f))
        canvas.drawRoundRect(rect, dp(12f), dp(12f), line)

        txt.textSize = dp(10f)
        txt.color = Palette.TEXT_FAINT
        canvas.drawText("${Biome.name(game.depth).uppercase()} · ${game.depth}. KAT", left + dp(12f), top + dp(15f), txt)

        txtBold.textSize = dp(12f)
        txtBold.color = game.heroClass.accent
        txtBold.textAlign = Paint.Align.RIGHT
        canvas.drawText("Sv ${p.level}", left + panelW - dp(12f), top + dp(15f), txtBold)
        txtBold.textAlign = Paint.Align.LEFT

        // HP
        val barX = left + dp(12f)
        val barW = panelW - dp(24f)
        txt.textSize = dp(8.5f)
        txt.color = Palette.TEXT_DIM
        canvas.drawText("CAN", barX, top + dp(29f), txt)
        txt.color = Palette.TEXT_FAINT
        txt.textAlign = Paint.Align.RIGHT
        canvas.drawText("${p.hp}/${p.maxHp}", barX + barW, top + dp(29f), txt)
        txt.textAlign = Paint.Align.LEFT
        Art.drawBar(canvas, barX, top + dp(32f), barW, dp(9f), p.hp.toFloat() / max(1, p.maxHp), Palette.RED, 0xFF2A1418.toInt(), dp(4f))

        // Oil
        txt.textSize = dp(8.5f)
        txt.color = Palette.TEXT_DIM
        canvas.drawText("YAĞ", barX, top + dp(52f), txt)
        txt.color = Palette.TEXT_FAINT
        txt.textAlign = Paint.Align.RIGHT
        canvas.drawText("${p.oil}", barX + barW, top + dp(52f), txt)
        txt.textAlign = Paint.Align.LEFT
        Art.drawBar(canvas, barX, top + dp(55f), barW, dp(6f), p.oil / 100f, Palette.AMBER, 0xFF2A2415.toInt(), dp(3f))

        // Energy pips.
        val maxE = max(1, p.maxEnergyWithGear())
        txt.textSize = dp(8.5f)
        txt.color = Palette.TEXT_DIM
        canvas.drawText("ENERJİ", barX, top + dp(73f), txt)
        val pipStart = barX + dp(38f)
        val pipGap = min(dp(9f), (barW - dp(40f)) / maxE)
        val pipR = min(dp(3.1f), pipGap * 0.34f)
        for (i in 0 until maxE) {
            fill.color = if (i < p.energyNow) Palette.TEAL else 0xFF2A3540.toInt()
            canvas.drawCircle(pipStart + i * pipGap, top + dp(70f), pipR, fill)
        }

        // Gold.
        txtBold.textSize = dp(12f)
        txtBold.color = Palette.AMBER_HI
        canvas.drawText("◆ ${p.gold}", left + dp(3f), top + panelH + dp(19f), txtBold)

        // Buff chips.
        var bx = left + dp(3f)
        val by = top + panelH + dp(30f)
        txt.textSize = dp(8.5f)
        for (b in p.buffs) {
            val (label, col) = when (b.type) {
                BuffType.POISON -> "ZEHİR ${b.turns}" to Palette.GREEN
                BuffType.BURN -> "ATEŞ ${b.turns}" to 0xFFE08A55.toInt()
                BuffType.SLOW -> "YAVAŞ ${b.turns}" to 0xFF7FA8D8.toInt()
                BuffType.INVISIBLE -> "SİS ${b.turns}" to Palette.TEAL
                BuffType.FEAR -> "KORKU ${b.turns}" to Palette.VIOLET
                BuffType.SHIELD -> "KALKAN ${b.power}" to Palette.TEAL
                BuffType.HASTE -> "HIZ ${b.turns}" to Palette.AMBER
            }
            val tw = txt.measureText(label) + dp(12f)
            if (bx + tw > width - dp(10f)) break
            fill.color = Art.alpha(col, 34)
            rect.set(bx, by - dp(10f), bx + tw, by + dp(4f))
            canvas.drawRoundRect(rect, dp(6f), dp(6f), fill)
            txt.color = col
            canvas.drawText(label, bx + dp(6f), by, txt)
            bx += tw + dp(5f)
        }
        if (p.invisibleTurns > 0) {
            val label = "GÖRÜNMEZ ${p.invisibleTurns}"
            val tw = txt.measureText(label) + dp(12f)
            if (bx + tw <= width - dp(10f)) {
                fill.color = Art.alpha(Palette.TEAL, 34)
                rect.set(bx, by - dp(10f), bx + tw, by + dp(4f))
                canvas.drawRoundRect(rect, dp(6f), dp(6f), fill)
                txt.color = Palette.TEAL
                canvas.drawText(label, bx + dp(6f), by, txt)
            }
        }

        drawMinimap(canvas)
        drawButtons(canvas, now)
        drawMessages(canvas)
        drawHint(canvas, now)

        if (aimMode) {
            drawAimHint(canvas)
        }
    }

    private fun drawButtons(canvas: Canvas, now: Long) {
        Art.drawDpad(canvas, padCx, padCy, padR, time, heldDir)

        // Skill
        val sr = skillR()
        val scx = skillCx()
        val scy = skillCy()
        val energyEnough = game.player.energyNow >= game.heroClass.skillCost
        val pulse = if (now - flashSkill < 240) (now - flashSkill) / 240f else 0f
        fill.color = Art.alpha(Palette.BLACK, 120)
        canvas.drawCircle(scx, scy, sr * 1.12f, fill)
        fill.color = Art.alpha(game.heroClass.accent, if (energyEnough) 40 else 18)
        canvas.drawCircle(scx, scy, sr * (1f + 0.06f * pulse), fill)
        line.color = Art.alpha(game.heroClass.accent, if (aimMode) 255 else if (energyEnough) 170 else 70)
        line.strokeWidth = max(1.5f, sr * (if (aimMode) 0.075f else 0.045f))
        canvas.drawCircle(scx, scy, sr * (1f + 0.05f * sin(time * 3f) * if (aimMode) 1f else 0.3f), line)
        Art.drawEmblem(canvas, game.heroClass, scx, scy, sr * 0.58f, if (energyEnough) game.heroClass.accent else Palette.TEXT_FAINT)
        txtBold.textSize = dp(9f)
        txtBold.color = if (energyEnough) Palette.TEXT else Palette.TEXT_FAINT
        txtBold.textAlign = Paint.Align.CENTER
        canvas.drawText(game.heroClass.skillName, scx, scy + sr * 1.30f, txtBold)
        txtBold.textAlign = Paint.Align.LEFT

        // Bag
        Art.drawBagIcon(canvas, bagCx(), bagCy(), bagR(), Palette.AMBER_HI)
        // Wait
        Art.drawHourglassIcon(canvas, waitCx(), waitCy(), waitR(), Palette.TEXT_DIM)
        // Map
        Art.drawMapIcon(canvas, mapCx(), mapCy(), mapR(), if (mapOpen) Palette.TEAL else Palette.TEXT_DIM)

        // Menu button.
        fill.color = Art.alpha(Palette.PANEL, 210)
        canvas.drawCircle(menuCx(), menuCy(), menuR(), fill)
        line.color = Art.alpha(Palette.STROKE, 220)
        line.strokeWidth = max(1f, dp(1f))
        canvas.drawCircle(menuCx(), menuCy(), menuR(), line)
        line.color = Palette.TEXT_DIM
        line.strokeWidth = max(1.5f, dp(1.8f))
        for (i in -1..1) {
            canvas.drawLine(
                menuCx() - menuR() * 0.42f, menuCy() + i * menuR() * 0.30f,
                menuCx() + menuR() * 0.42f, menuCy() + i * menuR() * 0.30f, line
            )
        }
    }

    private fun drawMinimap(canvas: Canvas) {
        val size = miniSize()
        val mx = miniX()
        val my = miniY()
        fill.color = Art.alpha(Palette.PANEL, 205)
        rect.set(mx, my, mx + size, my + size)
        canvas.drawRoundRect(rect, dp(9f), dp(9f), fill)
        line.color = Art.alpha(Palette.STROKE, 180)
        line.strokeWidth = max(1f, dp(1f))
        canvas.drawRoundRect(rect, dp(9f), dp(9f), line)

        val inset = dp(6f)
        val cell = (size - inset * 2) / MAP_W
        val ox = mx + inset
        val oy = my + inset
        for (y in 0 until MAP_H) {
            for (x in 0 until MAP_W) {
                val t = game.level.tile(x, y)
                if (!t.explored) continue
                if (t.type == TileType.WALL.ordinal || t.type == TileType.WALL_TORCH.ordinal) continue
                val col = when (t.t) {
                    TileType.WATER -> 0xFF2F5866.toInt()
                    TileType.LAVA -> 0xFF8A3B25.toInt()
                    TileType.STAIRS -> Palette.AMBER
                    TileType.PILLAR, TileType.RUBBLE -> 0xFF242A31.toInt()
                    else -> if (t.visible) 0xFF58636E.toInt() else 0xFF373E46.toInt()
                }
                fill.color = col
                canvas.drawRect(ox + x * cell, oy + y * cell, ox + (x + 1) * cell, oy + (y + 1) * cell, fill)
            }
        }
        for (e in game.enemies) {
            if (!e.alive) continue
            val t = game.level.tileOrNull(e.x, e.y) ?: continue
            if (!t.visible) continue
            fill.color = if (e.boss) Palette.RED else 0xFFC56A6A.toInt()
            canvas.drawCircle(ox + (e.x + 0.5f) * cell, oy + (e.y + 0.5f) * cell, max(dp(1.4f), cell * 0.45f), fill)
        }
        val t = game.level.tile(game.player.x, game.player.y)
        if (t.f == Feature.SHOP) {
            fill.color = Palette.AMBER_HI
            canvas.drawCircle(ox + (game.player.x + 0.5f) * cell, oy + (game.player.y + 0.5f) * cell, cell * 0.5f, fill)
        }
        fill.color = Palette.TEAL
        canvas.drawCircle(
            ox + (game.player.x + 0.5f) * cell,
            oy + (game.player.y + 0.5f) * cell,
            max(dp(2f), cell * (0.55f + 0.12f * sin(time * 4f))),
            fill
        )
    }

    private fun drawMessages(canvas: Canvas) {
        val msgs = game.messages.takeLast(2)
        if (msgs.isEmpty()) return
        val cx = width / 2f
        val y = padCy - padR * 1.35f - dp(8f)
        txt.textSize = dp(10.5f)
        txt.textAlign = Paint.Align.CENTER
        var yy = y - (msgs.size - 1) * dp(14f)
        for (m in msgs) {
            val tw = min(txt.measureText(m.text), width * 0.86f)
            fill.color = Art.alpha(Palette.BLACK, 140)
            rect.set(cx - tw / 2f - dp(8f), yy - dp(11f), cx + tw / 2f + dp(8f), yy + dp(4f))
            canvas.drawRoundRect(rect, dp(7f), dp(7f), fill)
            txt.color = m.color
            txt.setShadowLayer(dp(2f), 0f, dp(1f), 0xEE000000.toInt())
            canvas.drawText(m.text, cx, yy, txt)
            txt.clearShadowLayer()
            yy += dp(15f)
        }
        txt.textAlign = Paint.Align.LEFT
    }

    private fun drawHint(canvas: Canvas, now: Long) {
        if (game.depth != 1 || game.hintText.isEmpty()) return
        val remaining = game.hintUntil - now
        if (remaining <= 0L) return
        val a = (min(1f, remaining / 600f) * 255f).toInt().coerceIn(0, 255)
        val y = padCy - padR * 1.35f - dp(52f)
        txtBold.textSize = dp(11f)
        val maxW = width * 0.88f
        val tw = min(txtBold.measureText(game.hintText), maxW - dp(28f))
        val half = tw / 2f + dp(14f)
        fill.color = Art.alpha(Palette.BLACK, (a * 0.72f).toInt())
        rect.set(width / 2f - half, y - dp(13f), width / 2f + half, y + dp(7f))
        canvas.drawRoundRect(rect, dp(9f), dp(9f), fill)
        line.color = Art.alpha(Palette.TEAL, (a * 0.7f).toInt())
        line.strokeWidth = max(1f, dp(1.2f))
        canvas.drawRoundRect(rect, dp(9f), dp(9f), line)
        txtBold.color = Art.alpha(Palette.TEAL, a)
        txtBold.textAlign = Paint.Align.CENTER
        canvas.drawText(game.hintText, width / 2f, y, txtBold)
        txtBold.textAlign = Paint.Align.LEFT
    }

    private fun drawAimHint(canvas: Canvas) {
        val p = game.player
        val r = when (game.heroClass) {
            HeroClass.EMBER -> 6
            HeroClass.SHADE -> 4
            else -> 1
        }
        val cx = (p.x + 0.5f) * tileSize - camX
        val cy = (p.y + 0.5f) * tileSize - camY
        line.color = Art.alpha(game.heroClass.accent, 120)
        line.strokeWidth = max(1.5f, dp(1.5f))
        canvas.drawCircle(cx, cy, tileSize * r, line)
        val text = if (game.heroClass == HeroClass.EMBER) "Bir kareye dokun: Kor Yağmuru" else "Bir kareye dokun: Sis Adımı"
        txtBold.textSize = dp(11f)
        txtBold.textAlign = Paint.Align.CENTER
        txtBold.color = game.heroClass.accent
        fill.color = Art.alpha(Palette.BLACK, 160)
        rect.set(width / 2f - dp(150f), padCy - padR * 1.9f, width / 2f + dp(150f), padCy - padR * 1.9f + dp(26f))
        canvas.drawRoundRect(rect, dp(8f), dp(8f), fill)
        canvas.drawText(text, width / 2f, padCy - padR * 1.9f + dp(17f), txtBold)
        txtBold.textAlign = Paint.Align.LEFT
    }

    private fun drawBanner(canvas: Canvas, now: Long) {
        if (bannerTitle.isEmpty()) return
        val age = now - bannerBorn
        if (age > 3000) {
            bannerTitle = ""
            return
        }
        val fadeIn = (age / 240f).coerceIn(0f, 1f)
        val fadeOut = if (age > 2400) 1f - (age - 2400) / 600f else 1f
        val a = (min(fadeIn, fadeOut) * 255).toInt().coerceIn(0, 255)
        val cy = height * 0.28f
        fill.color = Art.alpha(Palette.BLACK, (a * 0.55f).toInt())
        rect.set(width * 0.08f, cy - dp(38f), width * 0.92f, cy + dp(34f))
        canvas.drawRoundRect(rect, dp(12f), dp(12f), fill)
        line.color = Art.alpha(bannerColor, (a * 0.5f).toInt())
        line.strokeWidth = max(1f, dp(1.2f))
        canvas.drawRoundRect(rect, dp(12f), dp(12f), line)
        txtSerif.textSize = dp(24f)
        txtSerif.color = Art.alpha(bannerColor, a)
        txtSerif.textAlign = Paint.Align.CENTER
        txtSerif.setShadowLayer(dp(8f), 0f, dp(2f), Art.alpha(bannerColor, a / 2))
        canvas.drawText(bannerTitle, width / 2f, cy - dp(2f), txtSerif)
        txtSerif.clearShadowLayer()
        txt.textSize = dp(10.5f)
        txt.color = Art.alpha(Palette.TEXT_DIM, a)
        txt.textAlign = Paint.Align.CENTER
        canvas.drawText(bannerSub, width / 2f, cy + dp(20f), txt)
        txt.textAlign = Paint.Align.LEFT
        txtSerif.textAlign = Paint.Align.LEFT
    }

    private fun drawMapOverlay(canvas: Canvas) {
        fill.color = Art.alpha(Palette.BG, 245)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)

        val top = topInset() + dp(18f)
        txtSerif.textSize = dp(22f)
        txtSerif.color = Palette.AMBER_HI
        txtSerif.textAlign = Paint.Align.CENTER
        canvas.drawText("HARİTA", width / 2f, top + dp(18f), txtSerif)
        txtSerif.textAlign = Paint.Align.LEFT
        txt.textSize = dp(10f)
        txt.color = Palette.TEXT_DIM
        txt.textAlign = Paint.Align.CENTER
        canvas.drawText("${Biome.name(game.depth)} · ${game.depth}. Kat", width / 2f, top + dp(36f), txt)
        txt.textAlign = Paint.Align.LEFT

        val availableW = width - dp(36f)
        val availableH = height - top - dp(140f)
        val cell = min(availableW / MAP_W, availableH / MAP_H)
        val ox = (width - cell * MAP_W) / 2f
        val oy = top + dp(54f) + (availableH - cell * MAP_H) / 2f

        fill.color = Art.alpha(Palette.PANEL, 200)
        canvas.drawRect(ox - dp(6f), oy - dp(6f), ox + cell * MAP_W + dp(6f), oy + cell * MAP_H + dp(6f), fill)

        for (y in 0 until MAP_H) for (x in 0 until MAP_W) {
            val t = game.level.tile(x, y)
            if (!t.explored) continue
            val col = when (t.t) {
                TileType.WALL, TileType.WALL_TORCH -> 0xFF10141A.toInt()
                TileType.WATER -> 0xFF244A5A.toInt()
                TileType.LAVA -> 0xFF7A3420.toInt()
                TileType.STAIRS -> Palette.AMBER
                TileType.DOOR -> 0xFF6A5433.toInt()
                TileType.PILLAR, TileType.RUBBLE -> 0xFF2A3038.toInt()
                TileType.VOID -> 0xFF160A18.toInt()
                else -> 0xFF3A424B.toInt()
            }
            fill.color = col
            canvas.drawRect(ox + x * cell, oy + y * cell, ox + (x + 1) * cell, oy + (y + 1) * cell, fill)
            if (t.f == Feature.SHOP) {
                fill.color = Palette.AMBER_HI
                canvas.drawCircle(ox + (x + 0.5f) * cell, oy + (y + 0.5f) * cell, cell * 0.42f, fill)
            } else if (t.f == Feature.ALTAR && t.visible) {
                fill.color = Palette.VIOLET
                canvas.drawCircle(ox + (x + 0.5f) * cell, oy + (y + 0.5f) * cell, cell * 0.34f, fill)
            }
            if (t.isTrap() && t.featureData == 1) {
                line.color = Palette.RED
                line.strokeWidth = max(1f, cell * 0.12f)
                val tx = ox + (x + 0.5f) * cell
                val ty = oy + (y + 0.5f) * cell
                canvas.drawLine(tx - cell * 0.2f, ty - cell * 0.2f, tx + cell * 0.2f, ty + cell * 0.2f, line)
                canvas.drawLine(tx + cell * 0.2f, ty - cell * 0.2f, tx - cell * 0.2f, ty + cell * 0.2f, line)
            }
        }
        for (e in game.enemies) {
            if (!e.alive) continue
            val t = game.level.tileOrNull(e.x, e.y) ?: continue
            if (!t.visible) continue
            fill.color = if (e.boss) Palette.RED else 0xFFC56A6A.toInt()
            canvas.drawCircle(ox + (e.x + 0.5f) * cell, oy + (e.y + 0.5f) * cell, max(dp(2f), cell * 0.52f), fill)
        }
        val px = ox + (game.player.x + 0.5f) * cell
        val py = oy + (game.player.y + 0.5f) * cell
        fill.color = Art.alpha(Palette.TEAL, 60)
        canvas.drawCircle(px, py, cell * 1.7f, fill)
        fill.color = Palette.TEAL
        canvas.drawCircle(px, py, max(dp(2.5f), cell * 0.62f), fill)

        txt.textSize = dp(10.5f)
        txt.color = Palette.TEXT_FAINT
        txt.textAlign = Paint.Align.CENTER
        canvas.drawText("Kapatmak için dokun", width / 2f, height - dp(46f), txt)
        txt.textAlign = Paint.Align.LEFT
    }

    companion object {
        private val HAPTIC_SOUNDS = setOf(
            "hit", "crit", "hurt", "death", "boss", "levelup", "skill", "slam", "trap", "alarm", "boss_death", "seal"
        )
    }
}
