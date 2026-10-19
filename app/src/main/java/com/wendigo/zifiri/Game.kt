package com.wendigo.zifiri

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

sealed class VisualEvent {
    data class FloatText(val x: Int, val y: Int, val text: String, val color: Int) : VisualEvent()
    data class Burst(val x: Int, val y: Int, val color: Int, val count: Int, val power: Float = 1f) : VisualEvent()
    data class Projectile(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val color: Int, val kind: Int = 0) : VisualEvent()
    data class Ring(val x: Int, val y: Int, val color: Int) : VisualEvent()
    data class Shake(val amount: Float) : VisualEvent()
    data class Sound(val id: String) : VisualEvent()
    data class Banner(val title: String, val sub: String, val color: Int) : VisualEvent()
}

interface GameListener {
    fun onGameOver()
    fun onVictory()
    fun onShopRequested()
}

class Game(
    val heroClass: HeroClass,
    var rng: Rng,
    var player: Player,
    var level: Level,
    var depth: Int
) {
    val enemies: MutableList<Actor> = mutableListOf()
    val groundItems: MutableList<GroundItem> = mutableListOf()
    val messages: MutableList<GameMessage> = mutableListOf()
    val events: MutableList<VisualEvent> = mutableListOf()
    var shopStock: MutableList<Pair<Item, Int>> = mutableListOf()
    var gameOver: Boolean = false
    var victory: Boolean = false
    var listener: GameListener? = null
    var turnCount: Long = 0L
    var startedAt: Long = System.currentTimeMillis()
    var totalGoldFound: Int = 0
    var totalDamage: Int = 0
    var deepest: Int = 1

    companion object {
        fun newGame(cls: HeroClass, seed: Long = System.nanoTime()): Game {
            val rng = Rng(seed)
            val level = DungeonGenerator(rng).generate(1)
            val player = Player(cls, level.startX, level.startY)
            val game = Game(cls, rng, player, level, 1)
            game.giveStartingGear()
            game.spawnFloor()
            game.recomputeFov()
            game.message("Karanlık seni yuttu. Fenerin hâlâ yanıyor.", Palette.AMBER_HI)
            game.message("Yedinci mühür aşağılarda bir yerde. İn.", Palette.TEXT_DIM)
            game.events.add(VisualEvent.Banner("SARNIÇ", "I. Kat · Su damlıyor, taş nefes alıyor.", Biome.accent(1)))
            game.events.add(VisualEvent.Sound("stairs"))
            return game
        }
    }

    private fun giveStartingGear() {
        when (heroClass) {
            HeroClass.BLADE -> {
                player.weapon = Item(Items.RUSTY_BLADE)
                player.armor = Item(Items.LEATHER_VEST)
                player.addItem(Item(Items.P_HEAL, 2))
                player.addItem(Item(Items.OIL_FLASK, 1))
                player.addItem(Item(Items.BREAD, 1))
            }
            HeroClass.EMBER -> {
                player.weapon = Item(Items.EMBER_STAFF)
                player.armor = Item(Items.TORN_ROBE)
                player.addItem(Item(Items.P_HEAL, 2))
                player.addItem(Item(Items.P_ENERGY, 1))
                player.addItem(Item(Items.OIL_FLASK, 1))
            }
            HeroClass.SHADE -> {
                player.weapon = Item(Items.SHORT_BOW)
                player.armor = Item(Items.SHADE_CLOAK)
                player.addItem(Item(Items.P_HEAL, 2))
                player.addItem(Item(Items.P_INVIS, 1))
                player.addItem(Item(Items.OIL_FLASK, 1))
            }
        }
        player.identified.add(Items.P_HEAL)
        player.maxEnergy = player.maxEnergyWithGear()
        player.energyNow = player.maxEnergy
        player.oil = 100
    }

    fun message(text: String, color: Int = Palette.TEXT) {
        messages.add(GameMessage(text, color))
        while (messages.size > 80) messages.removeAt(0)
    }

    fun sound(id: String) { events.add(VisualEvent.Sound(id)) }
    fun shake(amount: Float) { events.add(VisualEvent.Shake(amount)) }
    fun floatText(x: Int, y: Int, text: String, color: Int) { events.add(VisualEvent.FloatText(x, y, text, color)) }
    fun burst(x: Int, y: Int, color: Int, count: Int, power: Float = 1f) { events.add(VisualEvent.Burst(x, y, color, count, power)) }

    // ------------------------------------------------------------------
    // Floor population
    // ------------------------------------------------------------------

    private fun spawnFloor() {
        enemies.clear()
        groundItems.clear()
        shopStock.clear()
        deepest = max(deepest, depth)

        val bossId = Mobs.bossForDepth(depth)
        if (bossId != null) {
            val e = spawnEnemy(bossId, level.bossX, level.bossY)
            if (e != null) {
                message("${e.name} uyanıyor. Hava ağırlaşıyor.", Palette.RED)
                events.add(VisualEvent.Banner(e.name.uppercase(), "Mühür bekçisi", Palette.RED))
                sound("boss")
                shake(12f)
            }
            repeat(2 + depth / 3) {
                val spot = randomSpot(7) ?: return@repeat
                spawnEnemy(rng.pick(Mobs.forDepth(max(1, depth - 1))), spot.first, spot.second)
            }
        } else {
            val count = min(20, 6 + depth * 2)
            val pool = Mobs.forDepth(depth)
            repeat(count) {
                val spot = randomSpot(7) ?: return@repeat
                val id = rng.pick(pool)
                val elite = rng.chance(0.09 + depth * 0.008)
                spawnEnemy(id, spot.first, spot.second, elite)
            }
        }

        // Guaranteed light and a few supplies.
        val itemCount = 3 + depth / 2
        repeat(itemCount) {
            val spot = randomSpot(2) ?: return@repeat
            val item = randomLoot()
            groundItems.add(GroundItem(spot.first, spot.second, item))
        }
        repeat(2 + depth / 2) {
            val spot = randomSpot(2) ?: return@repeat
            val amount = rng.nextInt(4, 8 + depth * 4)
            groundItems.add(GroundItem(spot.first, spot.second, Item(Items.GOLD, amount)))
        }
        if (rng.chance(0.45)) {
            val spot = randomSpot(2)
            if (spot != null) groundItems.add(GroundItem(spot.first, spot.second, Item(Items.OIL_FLASK)))
        }
        if (rng.chance(0.30)) {
            val spot = randomSpot(2)
            if (spot != null) groundItems.add(GroundItem(spot.first, spot.second, Item(Items.BRASS_KEY)))
        }
        // Locked chests deserve a key.
        if (level.tiles.any { it.f == Feature.LOCKED_CHEST }) {
            val spot = randomSpot(2)
            if (spot != null) groundItems.add(GroundItem(spot.first, spot.second, Item(Items.BRASS_KEY)))
        }
        // Shop stock.
        if (level.tiles.any { it.f == Feature.SHOP }) {
            val stock = mutableListOf<Pair<Item, Int>>()
            stock.add(Item(Items.P_HEAL) to 26)
            stock.add(Item(rng.pick(Items.potionIds())) to rng.nextInt(22, 46))
            stock.add(Item(rng.pick(Items.scrollIds())) to rng.nextInt(26, 56))
            stock.add(Item(rng.pick(Items.weaponsForDepth(depth))) to rng.nextInt(38, 80))
            shopStock = stock
        }
    }

    private fun randomSpot(minDistFromStart: Int): Pair<Int, Int>? {
        repeat(600) {
            val x = rng.nextInt(1, MAP_W - 2)
            val y = rng.nextInt(1, MAP_H - 2)
            val t = level.tile(x, y)
            if (t.type != TileType.FLOOR.ordinal) return@repeat
            if (t.feature == Feature.SHOP.ordinal || t.feature == Feature.CHEST.ordinal || t.feature == Feature.LOCKED_CHEST.ordinal) return@repeat
            if (x == player.x && y == player.y) return@repeat
            if (enemyAt(x, y) != null) return@repeat
            if (groundItems.any { it.x == x && it.y == y }) return@repeat
            val d = max(abs(x - level.startX), abs(y - level.startY))
            if (d < minDistFromStart) return@repeat
            if (t.isTrap()) return@repeat
            return x to y
        }
        return null
    }

    private fun randomLoot(): Item {
        val roll = rng.nextDouble()
        val id = when {
            roll < 0.38 -> rng.pick(Items.potionIds())
            roll < 0.56 -> rng.pick(Items.scrollIds())
            roll < 0.70 -> rng.pick(Items.weaponsForDepth(depth))
            roll < 0.84 -> rng.pick(Items.armorForDepth(depth))
            roll < 0.91 -> Items.OIL_FLASK
            roll < 0.96 -> Items.BREAD
            else -> Items.randomRelic(rng)
        }
        val item = Item(id)
        if (item.def.kind == ItemKind.WEAPON || item.def.kind == ItemKind.ARMOR) {
            if (rng.chance(0.20)) item.upgrade = 1
            if (rng.chance(0.07)) item.upgrade = 2
        }
        return item
    }

    private fun spawnEnemy(id: String, x: Int, y: Int, elite: Boolean = false, generation: Int = 0): Actor? {
        val d = Mobs.def(id)
        if (!level.isWalkable(x, y)) return null
        if (enemyAt(x, y) != null) return null
        if (player.x == x && player.y == y) return null
        val depthBonus = max(0, depth - d.minDepth)
        val hpScale = if (d.behavior == Behavior.BOSS) 1.0 else 1.0 + depthBonus * 0.10
        val eliteScale = if (elite) 1.55 else 1.0
        val hp = (d.hp * hpScale * eliteScale).toInt().coerceAtLeast(1)
        val atkBonus = if (d.behavior == Behavior.BOSS) 0 else depthBonus / 3
        val e = Actor(
            x, y, if (elite) "Seçkin ${d.name}" else d.name,
            hp, hp,
            d.atkMin + atkBonus + (if (elite) 2 else 0),
            d.atkMax + atkBonus + (if (elite) 3 else 0),
            d.def + (if (elite) 1 else 0) + (if (d.behavior == Behavior.BOSS) 0 else depthBonus / 5),
            d.acc + (if (elite) 0.03 else 0.0),
            d.eva
        )
        e.enemyId = id
        e.elite = elite
        e.boss = d.behavior == Behavior.BOSS
        e.homeX = x
        e.homeY = y
        e.aiState = if (e.boss) AiState.HUNT else AiState.SLEEP
        e.generation = generation
        e.animFromX = x.toFloat()
        e.animFromY = y.toFloat()
        enemies.add(e)
        return e
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    fun enemyAt(x: Int, y: Int, includeDead: Boolean = false): Actor? {
        return enemies.firstOrNull { it.x == x && it.y == y && (includeDead || it.alive) && !it.boss }
            ?: enemies.firstOrNull { it.x == x && it.y == y && (includeDead || it.alive) && it.boss }
    }

    fun bossAlive(): Boolean = enemies.any { it.boss && it.alive }

    fun lightRadius(): Int {
        val base = when {
            player.oil > 40 -> 7
            player.oil > 15 -> 6
            player.oil > 0 -> 5
            else -> 3
        }
        return base + if (player.relic?.id == Items.OPEN_EYE) 1 else 0
    }

    fun recomputeFov() {
        Fov.compute(level, player.x, player.y, lightRadius())
        if (player.relic?.id == Items.OPEN_EYE) {
            revealTraps(player.x, player.y, 2)
        }
    }

    private fun revealTraps(cx: Int, cy: Int, radius: Int) {
        for (y in cy - radius..cy + radius) for (x in cx - radius..cx + radius) {
            if (!level.inBounds(x, y)) continue
            val t = level.tile(x, y)
            if (t.isTrap() && t.explored) t.featureData = 1
        }
    }

    fun itemName(item: Item): String {
        val def = item.def
        return if (Items.isIdentifiable(item.id) && item.id !in player.identified) {
            if (def.kind == ItemKind.POTION) "Bilinmeyen İksir · ${def.flavor}"
            else "Bilinmeyen Parşömen · ${def.flavor}"
        } else {
            def.name + if (item.upgrade > 0) " +${item.upgrade}" else ""
        }
    }

    fun itemDescription(item: Item): String {
        val def = item.def
        return if (Items.isIdentifiable(item.id) && item.id !in player.identified) {
            "Kullanana kadar ne olduğunu bilmiyorsun. Rengi/izi: ${def.flavor}."
        } else def.desc
    }

    private fun itemNameById(id: String): String = itemName(Item(id))

    fun visibleTiles(): Int {
        var c = 0
        for (t in level.tiles) if (t.visible) c++
        return c
    }

    // ------------------------------------------------------------------
    // Player actions
    // ------------------------------------------------------------------

    private fun canAct(): Boolean = !gameOver && !victory

    fun tapTile(tx: Int, ty: Int): Boolean {
        if (!canAct()) return false
        val t = level.tileOrNull(tx, ty) ?: return false
        if (tx == player.x && ty == player.y) {
            if (t.isStairs()) return tryDescend()
            if (t.f == Feature.ALTAR) return interact(tx, ty)
            if (t.isPortal()) { victory(); return true }
            return false
        }
        val enemy = enemyAt(tx, ty)
        if (enemy != null && t.visible) {
            if (heroClass == HeroClass.SHADE && player.distanceTo(enemy) <= 6 &&
                Pathfinder.canSee(level, player.x, player.y, tx, ty)
            ) {
                return playerShoot(enemy)
            }
            if (player.distanceTo(enemy) == 1) return playerAttack(enemy)
            return false
        }
        if (isAdjacent(player.x, player.y, tx, ty)) {
            if (t.f == Feature.CHEST || t.f == Feature.LOCKED_CHEST || t.f == Feature.SHOP || t.f == Feature.ALTAR) {
                return interact(tx, ty)
            }
            if (t.isTrap() && t.featureData == 1) {
                message("Bu bir tuzak. Dikkatli geç.", Palette.TEXT_DIM)
                return false
            }
        }
        return false
    }

    fun tryMove(dx: Int, dy: Int): Boolean {
        if (!canAct()) return false
        if (dx == 0 && dy == 0) return false
        val nx = player.x + dx
        val ny = player.y + dy
        if (!level.inBounds(nx, ny)) return false
        val enemy = enemyAt(nx, ny)
        if (enemy != null) return playerAttack(enemy)
        val t = level.tile(nx, ny)
        if (t.isDoor() && !t.doorOpen) {
            t.doorOpen = true
            message("Kapı gıcırdayarak açıldı.", Palette.TEXT_DIM)
            sound("open")
            endTurn()
            return true
        }
        if (!t.walkable()) {
            if (t.f == Feature.CHEST || t.f == Feature.LOCKED_CHEST || t.f == Feature.SHOP || t.f == Feature.ALTAR) {
                return interact(nx, ny)
            }
            if (t.isStairs()) {
                return tryDescend()
            }
            burst(nx, ny, Palette.STROKE, 4, 0.4f)
            return false
        }
        if (dx != 0 && dy != 0) {
            val a = level.tile(player.x + dx, player.y)
            val b = level.tile(player.x, player.y + dy)
            if (!a.walkable() || !b.walkable()) return false
            if (enemyAt(player.x + dx, player.y) != null || enemyAt(player.x, player.y + dy) != null) return false
        }
        player.facingX = dx.toFloat()
        player.facingY = dy.toFloat()
        player.animFromX = player.x.toFloat()
        player.animFromY = player.y.toFloat()
        player.animStart = System.currentTimeMillis()
        player.x = nx
        player.y = ny
        player.steps++
        sound(if (t.type == TileType.WATER.ordinal) "splash" else "step")
        if (t.type == TileType.WATER.ordinal) burst(nx, ny, 0xFF6FAEB8.toInt(), 5, 0.5f)
        pickupAt(nx, ny)
        if (t.isTrap() && t.featureData == 0) triggerTrap(t, nx, ny)
        if (t.isStairs()) message("Merdivenler burada. İnmek için dokun.", Palette.AMBER_HI)
        alertNearby(3)
        endTurn()
        return true
    }

    fun waitTurn() {
        if (!canAct()) return
        revealTraps(player.x, player.y, 2)
        message("Nefesini tutup dinledin.", Palette.TEXT_DIM)
        sound("wait")
        endTurn()
    }

    private fun playerAttack(enemy: Actor): Boolean {
        if (!canAct()) return false
        player.facingX = (enemy.x - player.x).toFloat()
        player.facingY = (enemy.y - player.y).toFloat()
        player.attackUntil = System.currentTimeMillis() + 220
        val acc = (0.86 + player.totalAcc() - enemy.eva).coerceIn(0.35, 0.97)
        if (!rng.chance(acc)) {
            message("Vuruşun boşa gitti.", Palette.TEXT_FAINT)
            floatText(enemy.x, enemy.y, "ıska", Palette.TEXT_FAINT)
            sound("miss")
            alertEnemy(enemy)
            endTurn()
            return true
        }
        var dmg = rng.nextInt(player.weaponMin(), player.weaponMax())
        val crit = rng.chance(player.totalCrit())
        if (crit) dmg = (dmg * 1.9).toInt() + 1
        dmg = max(1, dmg - enemy.def / 2)
        val fire = player.totalFire()
        if (fire > 0) {
            dmg += fire
            if (rng.chance(0.35)) {
                enemy.addBuff(Buff(BuffType.BURN, 2, 2 + fire))
                burst(enemy.x, enemy.y, 0xFFE06645.toInt(), 6, 0.8f)
            }
        }
        val dealt = damageEnemy(enemy, dmg, crit)
        val life = (dealt * player.totalLifesteal()).toInt()
        if (life > 0) {
            player.heal(life)
            floatText(player.x, player.y, "+$life", Palette.GREEN)
        }
        message("${player.name}, ${enemy.name}'e ${if (crit) "ağır " else ""}vurdu ($dealt).", if (crit) Palette.AMBER_HI else Palette.TEXT)
        sound(if (crit) "crit" else "hit")
        alertEnemy(enemy)
        alertNearby(8)
        endTurn()
        return true
    }

    private fun playerShoot(enemy: Actor): Boolean {
        if (!canAct()) return false
        if (player.distanceTo(enemy) > 6 || !Pathfinder.canSee(level, player.x, player.y, enemy.x, enemy.y)) {
            message("Ok için görüş hattı yok.", Palette.TEXT_FAINT)
            return false
        }
        player.facingX = (enemy.x - player.x).toFloat()
        player.facingY = (enemy.y - player.y).toFloat()
        player.attackUntil = System.currentTimeMillis() + 220
        events.add(VisualEvent.Projectile(player.x, player.y, enemy.x, enemy.y, 0xFFD8C99A.toInt(), 0))
        val acc = (0.84 + player.totalAcc() - enemy.eva).coerceIn(0.35, 0.97)
        if (!rng.chance(acc)) {
            message("Ok havada kayboldu.", Palette.TEXT_FAINT)
            floatText(enemy.x, enemy.y, "ıska", Palette.TEXT_FAINT)
            sound("miss")
            alertEnemy(enemy)
            endTurn()
            return true
        }
        var dmg = rng.nextInt(player.weaponMin(), player.weaponMax())
        val crit = rng.chance(player.totalCrit() + 0.05)
        if (crit) dmg = (dmg * 1.8).toInt() + 1
        dmg = max(1, dmg - enemy.def / 2)
        val dealt = damageEnemy(enemy, dmg, crit)
        message("Ok ${enemy.name}'e saplandı ($dealt).", if (crit) Palette.AMBER_HI else Palette.TEXT)
        sound("arrow")
        alertEnemy(enemy)
        alertNearby(8)
        endTurn()
        return true
    }

    fun useSkillAt(tx: Int, ty: Int): Boolean {
        if (!canAct()) return false
        if (player.energyNow < heroClass.skillCost) {
            message("Yeterli enerjin yok.", Palette.TEXT_FAINT)
            sound("empty")
            return false
        }
        when (heroClass) {
            HeroClass.BLADE -> {
                val targets = enemies.filter { it.alive && player.distanceTo(it) == 1 }
                if (targets.isEmpty()) {
                    message("Hilal Darbesi için yanında düşman yok.", Palette.TEXT_FAINT)
                    return false
                }
                player.energyNow -= heroClass.skillCost
                player.attackUntil = System.currentTimeMillis() + 320
                events.add(VisualEvent.Ring(player.x, player.y, Palette.AMBER_HI))
                sound("skill")
                shake(5f)
                for (e in targets) {
                    var dmg = rng.nextInt(player.weaponMin(), player.weaponMax()) + player.weaponMax() / 2
                    dmg = max(1, dmg - e.def / 2)
                    damageEnemy(e, dmg, true)
                    knockback(e, e.x - player.x, e.y - player.y)
                }
                message("Hilal Darbesi! Çevrendeki düşmanlar savruldu.", Palette.AMBER_HI)
                alertNearby(8)
                endTurn()
                return true
            }
            HeroClass.EMBER -> {
                if (tx !in 0 until MAP_W || ty !in 0 until MAP_H) return false
                if (player.distanceTo(tx, ty) > 6) {
                    message("Kor çok uzağa gidemez.", Palette.TEXT_FAINT)
                    return false
                }
                if (!Pathfinder.canSee(level, player.x, player.y, tx, ty)) {
                    message("Oraya bakamıyorsun.", Palette.TEXT_FAINT)
                    return false
                }
                player.energyNow -= heroClass.skillCost
                player.attackUntil = System.currentTimeMillis() + 300
                events.add(VisualEvent.Projectile(player.x, player.y, tx, ty, 0xFFE06A3F.toInt(), 1))
                burst(tx, ty, 0xFFE06A3F.toInt(), 20, 1.3f)
                events.add(VisualEvent.Ring(tx, ty, 0xFFE06A3F.toInt()))
                sound("fire")
                shake(4f)
                for (e in enemies.toList()) {
                    if (!e.alive) continue
                    if (abs(e.x - tx) <= 1 && abs(e.y - ty) <= 1) {
                        var dmg = rng.nextInt(4, 7) + player.level + player.totalFire()
                        dmg = max(1, dmg - e.def / 3)
                        damageEnemy(e, dmg, false)
                        e.addBuff(Buff(BuffType.BURN, 3, 3 + player.totalFire()))
                    }
                }
                message("Kor Yağmuru! Alevler etrafı sardı.", Palette.AMBER_HI)
                alertNearby(10)
                endTurn()
                return true
            }
            HeroClass.SHADE -> {
                if (tx !in 0 until MAP_W || ty !in 0 until MAP_H) return false
                if (player.distanceTo(tx, ty) > 4) {
                    message("Sis sadece kısa bir sıçrayışa izin veriyor.", Palette.TEXT_FAINT)
                    return false
                }
                val t = level.tile(tx, ty)
                if (!t.walkable() || !t.visible || enemyAt(tx, ty) != null) {
                    message("Oraya sıçrayamazsın.", Palette.TEXT_FAINT)
                    return false
                }
                player.energyNow -= heroClass.skillCost
                burst(player.x, player.y, Palette.TEAL, 14, 1f)
                player.animFromX = player.x.toFloat()
                player.animFromY = player.y.toFloat()
                player.animStart = System.currentTimeMillis()
                player.x = tx
                player.y = ty
                player.invisibleTurns = max(player.invisibleTurns, 4)
                burst(tx, ty, Palette.TEAL, 16, 1f)
                sound("blink")
                message("Sis Adımı. Kısa süre görünmezsin.", Palette.TEAL)
                pickupAt(tx, ty)
                if (t.isTrap() && t.featureData == 0) triggerTrap(t, tx, ty)
                endTurn()
                return true
            }
        }
        return false
    }

    private fun knockback(e: Actor, dx: Int, dy: Int): Boolean {
        var ddx = dx
        var ddy = dy
        if (ddx == 0 && ddy == 0) return false
        if (abs(ddx) > 1) ddx = ddx.coerceIn(-1, 1)
        if (abs(ddy) > 1) ddy = ddy.coerceIn(-1, 1)
        val nx = e.x + ddx
        val ny = e.y + ddy
        if (!level.isWalkable(nx, ny)) return false
        if (enemyAt(nx, ny) != null) return false
        if (player.x == nx && player.y == ny) return false
        e.animFromX = e.x.toFloat()
        e.animFromY = e.y.toFloat()
        e.animStart = System.currentTimeMillis()
        e.x = nx
        e.y = ny
        return true
    }

    fun interact(tx: Int, ty: Int): Boolean {
        if (!canAct()) return false
        if (!isAdjacent(player.x, player.y, tx, ty) && !(player.x == tx && player.y == ty)) return false
        val t = level.tileOrNull(tx, ty) ?: return false
        when (t.f) {
            Feature.CHEST -> {
                openChest(tx, ty, false)
                endTurn()
                return true
            }
            Feature.LOCKED_CHEST -> {
                val key = player.inventory.firstOrNull { it.id == Items.BRASS_KEY }
                if (key == null) {
                    message("Kilitli. Pirinç bir anahtar gerekiyor.", Palette.TEXT_FAINT)
                    return false
                }
                player.removeItem(key)
                openChest(tx, ty, true)
                endTurn()
                return true
            }
            Feature.ALTAR -> {
                useAltar(tx, ty)
                endTurn()
                return true
            }
            Feature.SHOP -> {
                if (shopStock.isEmpty()) {
                    message("Sandık boş. Gezgin çoktan kaçmış.", Palette.TEXT_FAINT)
                    return false
                }
                sound("shop")
                listener?.onShopRequested()
                return true
            }
            else -> {
                if (t.isStairs()) return tryDescend()
                if (t.isTrap() && t.featureData == 1) {
                    message("Tuzak. Etrafından dolaş.", Palette.TEXT_FAINT)
                    return false
                }
            }
        }
        return false
    }

    private fun openChest(tx: Int, ty: Int, locked: Boolean) {
        val t = level.tile(tx, ty)
        t.feature = Feature.NONE.ordinal
        sound("chest")
        burst(tx, ty, Palette.AMBER_HI, 18, 1.1f)
        message(if (locked) "Kilit kırıldı. Sandık açıldı." else "Sandık açıldı.", Palette.AMBER_HI)
        val gold = rng.nextInt(10, 26 + depth * 6)
        player.gold += gold
        totalGoldFound += gold
        floatText(player.x, player.y, "+$gold altın", Palette.AMBER_HI)
        val count = rng.nextInt(2, 4)
        repeat(count) {
            val item = if (rng.chance(0.22)) Item(Items.randomRelic(rng)) else randomLoot()
            if (!player.addItem(item)) {
                groundItems.add(GroundItem(nearestFreeTile(tx, ty)?.first ?: tx, nearestFreeTile(tx, ty)?.second ?: ty, item))
                message("Çanta doldu; ${itemName(item)} yere düştü.", Palette.TEXT_DIM)
            } else {
                message("Sandıktan çıktı: ${itemName(item)}", Palette.TEXT)
            }
        }
    }

    private fun useAltar(tx: Int, ty: Int) {
        val t = level.tile(tx, ty)
        t.feature = Feature.NONE.ordinal
        burst(tx, ty, Palette.AMBER_HI, 24, 1.2f)
        sound("altar")
        when (rng.nextInt(4)) {
            0 -> {
                player.heal(player.maxHp * 2 / 5)
                message("Sunağın ışığı yaralarını sarıyor.", Palette.GREEN)
            }
            1 -> {
                player.oil = min(100, player.oil + 50)
                message("Fenerin yeniden parlıyor.", Palette.AMBER_HI)
            }
            2 -> {
                player.maxHp += 3
                player.hp = min(player.maxHp, player.hp + 3)
                message("Sunağın taşı bir parçasını sana bırakıyor. +3 can.", Palette.AMBER_HI)
            }
            else -> {
                val item = Item(Items.randomRelic(rng))
                if (player.addItem(item)) message("Sunağın armağanı: ${itemName(item)}", Palette.VIOLET)
                else message("Sunağın armağanı ellerinde parçalanıyor.", Palette.TEXT_FAINT)
            }
        }
    }

    private fun triggerTrap(t: Tile, x: Int, y: Int) {
        t.featureData = 1
        sound("trap")
        when (t.f) {
            Feature.TRAP_SPIKE -> {
                val dmg = rng.nextInt(3, 6) + depth
                damagePlayer(dmg, "diken tuzağı")
                burst(x, y, 0xFF9E3F3F.toInt(), 16, 1.1f)
                shake(7f)
                message("Diken tuzağı! $dmg hasar.", Palette.RED)
            }
            Feature.TRAP_POISON -> {
                player.addBuff(Buff(BuffType.POISON, 4, 2 + depth / 2))
                burst(x, y, 0xFF6FAE61.toInt(), 18, 1.0f)
                message("Zehirli gaz! Nefesin yanıyor.", Palette.GREEN)
            }
            Feature.TRAP_ALARM -> {
                alertNearby(30)
                burst(x, y, 0xFFE0B45C.toInt(), 18, 1.2f)
                message("Alarm tuzağı! Tüm kat uyandı.", Palette.RED)
                sound("alarm")
            }
            Feature.TRAP_TELEPORT -> {
                burst(x, y, Palette.VIOLET, 24, 1.1f)
                teleportRandom()
                message("Teleport tuzağı seni başka bir yere savurdu.", Palette.VIOLET)
            }
            else -> {}
        }
    }

    private fun nearestFreeTile(x: Int, y: Int): Pair<Int, Int>? {
        for (r in 1..4) for (dy in -r..r) for (dx in -r..r) {
            val nx = x + dx
            val ny = y + dy
            if (!level.inBounds(nx, ny)) continue
            if (level.isWalkable(nx, ny) && enemyAt(nx, ny) == null && !(nx == player.x && ny == player.y)) return nx to ny
        }
        return null
    }

    fun tryDescend(): Boolean {
        if (!canAct()) return false
        if (depth >= MAX_DEPTH) {
            message("Daha aşağısı yok. Mührü bul.", Palette.TEXT_FAINT)
            return false
        }
        if (bossAlive()) {
            message("Merdivenin mührü kapalı. Bekçi hâlâ yaşıyor.", Palette.RED)
            sound("empty")
            return false
        }
        if (player.x != level.stairsX || player.y != level.stairsY) {
            message("İnmek için merdivenlerin üstünde durmalısın.", Palette.TEXT_FAINT)
            return false
        }
        depth++
        rng = Rng(rng.random.nextLong())
        level = DungeonGenerator(rng).generate(depth)
        player.x = level.startX
        player.y = level.startY
        player.animFromX = player.x.toFloat()
        player.animFromY = player.y.toFloat()
        player.heal(player.maxHp / 6)
        player.oil = min(100, player.oil + 8)
        spawnFloor()
        recomputeFov()
        message("Aşağı indin. ${Biome.name(depth)} seni karşıladı.", Biome.accent(depth))
        events.add(VisualEvent.Banner("${Biome.name(depth).uppercase()} · ${depth}. KAT", Biome.subtitle(depth), Biome.accent(depth)))
        sound("stairs")
        return true
    }

    // ------------------------------------------------------------------
    // Turn loop
    // ------------------------------------------------------------------

    private fun endTurn() {
        if (gameOver || victory) return
        turnCount++
        player.turns++
        if (player.turns % 8 == 0 && player.oil > 0) {
            player.oil--
            if (player.oil == 20) message("Fenerin zayıflıyor. Yağ bulmalısın.", Palette.AMBER)
            if (player.oil == 0) message("Fener söndü. Karanlık yaklaşıyor.", Palette.RED)
        }
        if (player.turns % 3 == 0) {
            player.energyNow = min(player.maxEnergyWithGear(), player.energyNow + 1)
        }
        if (player.oil <= 0 && player.turns % 12 == 0) {
            damagePlayer(1, "karanlık")
            message("Karanlık tenini kemiriyor.", Palette.RED)
        }
        tickPlayerBuffs()
        for (e in enemies.toList()) {
            if (!e.alive) continue
            tickEnemyBuffs(e)
            val speed = if (e.hasBuff(BuffType.SLOW)) e.energy else 100
            val spd = Mobs.def(e.enemyId ?: Mobs.RAT).speed
            e.energy += if (e.hasBuff(BuffType.SLOW)) spd / 2 else spd
            var acts = 0
            while (e.energy >= 100 && acts < 3 && e.alive) {
                e.energy -= 100
                enemyAct(e)
                acts++
                if (gameOver || victory) break
            }
        }
        enemies.removeAll { !it.alive && System.currentTimeMillis() - it.deathAt > 1600 }
        recomputeFov()
        if (player.hp <= 0 && !gameOver) die()
        if (player.oil <= 0) player.oil = 0
    }

    private fun tickPlayerBuffs() {
        val it = player.buffs.iterator()
        while (it.hasNext()) {
            val b = it.next()
            when (b.type) {
                BuffType.POISON -> {
                    if (b.turns > 0) {
                        damagePlayer(max(1, b.power), "zehir")
                        burst(player.x, player.y, 0xFF6FAE61.toInt(), 3, 0.4f)
                    }
                }
                BuffType.BURN -> {
                    if (b.turns > 0) {
                        damagePlayer(max(1, b.power), "ateş")
                        burst(player.x, player.y, 0xFFE06A3F.toInt(), 4, 0.5f)
                    }
                }
                else -> {}
            }
            b.turns--
            if (b.turns <= 0) it.remove()
        }
        if (player.invisibleTurns > 0) player.invisibleTurns--
    }

    private fun tickEnemyBuffs(e: Actor) {
        val it = e.buffs.iterator()
        while (it.hasNext()) {
            val b = it.next()
            if (b.type == BuffType.POISON && b.turns > 0) {
                val dealt = e.hurt(max(1, b.power))
                floatText(e.x, e.y, "-$dealt", 0xFF9BE07A.toInt())
                if (!e.alive) onEnemyDeath(e)
            }
            if (b.type == BuffType.BURN && b.turns > 0) {
                val dealt = e.hurt(max(1, b.power))
                floatText(e.x, e.y, "-$dealt", 0xFFE08A55.toInt())
                burst(e.x, e.y, 0xFFE06A3F.toInt(), 2, 0.3f)
                if (!e.alive) onEnemyDeath(e)
            }
            b.turns--
            if (b.turns <= 0) it.remove()
        }
    }

    // ------------------------------------------------------------------
    // Enemies
    // ------------------------------------------------------------------

    private fun enemyAct(e: Actor) {
        if (!e.alive || gameOver || victory) return
        if (e.hasBuff(BuffType.FEAR)) {
            flee(e)
            return
        }
        if (e.boss) {
            bossAct(e)
            return
        }
        val dist = e.distanceTo(player)
        val sees = dist <= 8 && player.invisibleTurns <= 0 && Pathfinder.canSee(level, e.x, e.y, player.x, player.y)
        if (sees) {
            e.lastSeenX = player.x
            e.lastSeenY = player.y
            e.alertTurns = 8
            if (e.aiState == AiState.SLEEP) {
                e.aiState = AiState.HUNT
                floatText(e.x, e.y, "!", Palette.AMBER_HI)
            }
        }
        if (e.aiState == AiState.SLEEP) {
            if (sees && dist <= 7) {
                e.aiState = AiState.HUNT
            } else {
                if (rng.chance(0.18)) randomStep(e)
                return
            }
        }
        if (e.alertTurns > 0) e.alertTurns--
        if (e.alertTurns <= 0 && !sees) e.aiState = AiState.WANDER
        if (e.aiState == AiState.WANDER) {
            if (rng.chance(0.35)) randomStep(e)
            if (sees) e.aiState = AiState.HUNT
            return
        }

        val behavior = Mobs.def(e.enemyId ?: Mobs.RAT).behavior
        val range = Mobs.def(e.enemyId ?: Mobs.RAT).rangedRange
        if (behavior == Behavior.TURRET) {
            if (sees && dist <= range) enemyRangedAttack(e)
            return
        }
        if (behavior == Behavior.RANGED && sees && dist in 2..range && e.specialCooldown <= 0) {
            enemyRangedAttack(e)
            e.specialCooldown = 2
            return
        }
        if (e.specialCooldown > 0) e.specialCooldown--
        if (dist == 1) {
            enemyMelee(e)
            return
        }
        if (behavior == Behavior.CHARGER && sees && dist in 2..4 &&
            (e.x == player.x || e.y == player.y)
        ) {
            val dx = Integer.signum(player.x - e.x)
            val dy = Integer.signum(player.y - e.y)
            var moved = false
            for (step in 1..2) {
                val nx = e.x + dx
                val ny = e.y + dy
                if (level.isWalkable(nx, ny) && enemyAt(nx, ny) == null && !(nx == player.x && ny == player.y)) {
                    actorStep(e, dx, dy)
                    moved = true
                } else break
            }
            if (moved) {
                burst(e.x, e.y, 0xFFD07A4C.toInt(), 8, 0.8f)
                if (e.distanceTo(player) == 1) enemyMelee(e)
                return
            }
        }
        if (behavior == Behavior.TELEPORTER && rng.chance(0.12)) {
            teleportEnemyNear(e)
            return
        }
        // Chase.
        val tx = if (e.lastSeenX >= 0) e.lastSeenX else player.x
        val ty = if (e.lastSeenY >= 0) e.lastSeenY else player.y
        val path = Pathfinder.findPath(level, e.x, e.y, tx, ty, { x, y ->
            !level.isWalkable(x, y) || (enemyAt(x, y) != null) || (player.x == x && player.y == y)
        })
        if (path.isNotEmpty()) {
            val next = path.first()
            if (next.first == player.x && next.second == player.y) {
                enemyMelee(e)
            } else {
                actorStep(e, next.first - e.x, next.second - e.y)
            }
        } else {
            randomStep(e)
        }
    }

    private fun bossAct(e: Actor) {
        val def = Mobs.def(e.enemyId ?: Mobs.BOSS_VOID)
        val frac = e.hp.toFloat() / e.maxHp
        val dist = e.distanceTo(player)
        if (e.summonCooldown > 0) e.summonCooldown--
        if (e.specialCooldown > 0) e.specialCooldown--

        if (e.enemyId == Mobs.BOSS_VOID && e.summonCooldown <= 0 && frac < 0.72f) {
            e.summonCooldown = if (frac < 0.35f) 4 else 6
            repeat(2) {
                val spot = nearBossFreeSpot(e)
                if (spot != null) {
                    val minion = spawnEnemy(if (frac < 0.35f) Mobs.SHADE else Mobs.BAT, spot.first, spot.second)
                    if (minion != null) {
                        minion.aiState = AiState.HUNT
                        minion.alertTurns = 30
                        burst(spot.first, spot.second, Palette.VIOLET, 10, 0.9f)
                    }
                }
            }
            message("${e.name} gölgeleri çağırıyor!", Palette.VIOLET)
            sound("boss")
        }

        if (e.enemyId == Mobs.BOSS_CRYSTAL && e.summonCooldown <= 0) {
            e.summonCooldown = 5
            repeat(2) {
                val spot = nearBossFreeSpot(e)
                if (spot != null) {
                    val minion = spawnEnemy(Mobs.SPIDER, spot.first, spot.second)
                    if (minion != null) {
                        minion.aiState = AiState.HUNT
                        minion.alertTurns = 30
                        burst(spot.first, spot.second, Palette.VIOLET, 10, 0.9f)
                    }
                }
            }
            message("Kristal Ana yumurtalarını çatlatıyor.", Palette.VIOLET)
            sound("boss")
        }

        val phase = when {
            frac > 0.66f -> 1
            frac > 0.33f -> 2
            else -> 3
        }

        if (e.enemyId == Mobs.BOSS_ASH && dist <= 2 && e.specialCooldown <= 0) {
            e.specialCooldown = 3
            events.add(VisualEvent.Ring(e.x, e.y, 0xFFE08A55.toInt()))
            shake(13f)
            burst(e.x, e.y, 0xFFE08A55.toInt(), 30, 1.3f)
            damagePlayer(rng.nextInt(6, 10) + depth, "Kül Devi'nin darbesi")
            message("Kül Devi yere vurdu! Zemin sarsılıyor.", Palette.RED)
            sound("slam")
            if (dist == 1) enemyMelee(e)
            return
        }

        if (e.enemyId == Mobs.BOSS_VOID && e.specialCooldown <= 0 && dist <= 8 &&
            Pathfinder.canSee(level, e.x, e.y, player.x, player.y) && rng.chance(0.5)
        ) {
            e.specialCooldown = if (phase >= 3) 2 else 4
            events.add(VisualEvent.Projectile(e.x, e.y, player.x, player.y, 0xFFD55C7A.toInt(), 2))
            damagePlayer(rng.nextInt(6, 11) + depth, "boşluk ışını")
            burst(player.x, player.y, 0xFFD55C7A.toInt(), 18, 1.2f)
            message("Boşluk Bekçisi bir ışın savurdu.", Palette.RED)
            sound("void")
            return
        }

        if (e.enemyId == Mobs.BOSS_CRYSTAL && dist in 2..6 && e.specialCooldown <= 0 &&
            Pathfinder.canSee(level, e.x, e.y, player.x, player.y)
        ) {
            e.specialCooldown = 3
            events.add(VisualEvent.Projectile(e.x, e.y, player.x, player.y, 0xFFAC8BFF.toInt(), 0))
            damagePlayer(rng.nextInt(4, 8) + depth / 2, "kristal parçası")
            message("Kristal Ana bir kristal fırlattı.", Palette.VIOLET)
            sound("arrow")
            return
        }

        if (dist == 1) {
            enemyMelee(e)
            return
        }
        if (e.enemyId == Mobs.BOSS_VOID && e.specialCooldown <= 0 && phase >= 3 && rng.chance(0.35)) {
            e.specialCooldown = 5
            teleportEnemyNear(e)
            message("Boşluk Bekçisi karanlığa karıştı.", Palette.VIOLET)
            burst(e.x, e.y, Palette.VIOLET, 20, 1.2f)
            return
        }
        // Bosses move slowly toward the player.
        val path = Pathfinder.findPath(level, e.x, e.y, player.x, player.y, { x, y ->
            !level.isWalkable(x, y) || (enemyAt(x, y) != null) || (player.x == x && player.y == y)
        })
        if (path.isNotEmpty()) {
            val next = path.first()
            if (next.first == player.x && next.second == player.y) enemyMelee(e)
            else actorStep(e, next.first - e.x, next.second - e.y)
        } else if (rng.chance(0.2)) {
            randomStep(e)
        }
    }

    private fun nearBossFreeSpot(boss: Actor): Pair<Int, Int>? {
        for (r in 1..4) for (dy in -r..r) for (dx in -r..r) {
            val x = boss.x + dx
            val y = boss.y + dy
            if (!level.inBounds(x, y)) continue
            if (level.isWalkable(x, y) && enemyAt(x, y) == null && !(x == player.x && y == player.y)) return x to y
        }
        return null
    }

    private fun enemyMelee(e: Actor) {
        if (e.distanceTo(player) != 1) return
        e.attackUntil = System.currentTimeMillis() + 220
        val acc = (0.82 + e.acc - player.totalEva()).coerceIn(0.30, 0.95)
        if (!rng.chance(acc)) {
            floatText(player.x, player.y, "ıska", Palette.TEXT_FAINT)
            sound("miss")
            return
        }
        var dmg = rng.nextInt(e.atkMin, e.atkMax)
        if (e.elite) dmg += 1
        dmg = max(1, dmg - player.armorDef() / 2)
        damagePlayer(dmg, e.name)
        if (e.enemyId == Mobs.BAT && e.alive) {
            e.heal(max(1, dmg / 3))
            floatText(e.x, e.y, "+${max(1, dmg / 3)}", Palette.GREEN)
        }
        if (rng.chance(0.18) && e.enemyId == Mobs.SPIDER) {
            player.addBuff(Buff(BuffType.SLOW, 3))
            message("Kristal ağı hareketlerini yavaşlatıyor.", Palette.VIOLET)
        }
        sound("hurt")
        shake(5f)
    }

    private fun enemyRangedAttack(e: Actor) {
        if (!Pathfinder.canSee(level, e.x, e.y, player.x, player.y)) return
        e.attackUntil = System.currentTimeMillis() + 220
        val color = when (e.enemyId) {
            Mobs.SPIDER -> 0xFFAC8BFF.toInt()
            Mobs.FLAME -> 0xFFE06A3F.toInt()
            Mobs.THORN -> 0xFF7BAE61.toInt()
            else -> Palette.TEXT_DIM
        }
        events.add(VisualEvent.Projectile(e.x, e.y, player.x, player.y, color, 0))
        val acc = (0.80 + e.acc - player.totalEva()).coerceIn(0.28, 0.94)
        if (!rng.chance(acc)) {
            floatText(player.x, player.y, "ıska", Palette.TEXT_FAINT)
            sound("miss")
            return
        }
        var dmg = rng.nextInt(e.atkMin, e.atkMax)
        dmg = max(1, dmg - player.armorDef() / 3)
        damagePlayer(dmg, e.name)
        when (e.enemyId) {
            Mobs.SPIDER -> {
                if (rng.chance(0.4)) {
                    player.addBuff(Buff(BuffType.SLOW, 3))
                    message("Kristal ağı seni yavaşlattı.", Palette.VIOLET)
                }
            }
            Mobs.FLAME -> {
                if (rng.chance(0.35)) {
                    player.addBuff(Buff(BuffType.BURN, 2, 2 + depth / 3))
                    message("Alevler üstüne sıçradı.", Palette.AMBER)
                }
            }
            Mobs.THORN -> {
                if (rng.chance(0.3)) {
                    player.addBuff(Buff(BuffType.POISON, 3, 1 + depth / 3))
                    message("Dikenler zehir bıraktı.", Palette.GREEN)
                }
            }
        }
        sound("hit")
        shake(4f)
    }

    private fun flee(e: Actor) {
        if (e.distanceTo(player) > 9) {
            e.removeBuff(BuffType.FEAR)
            return
        }
        val options = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
            .filter { (dx, dy) ->
                val nx = e.x + dx
                val ny = e.y + dy
                level.isWalkable(nx, ny) && enemyAt(nx, ny) == null && !(nx == player.x && ny == player.y)
            }
            .sortedByDescending { e.distanceTo(player.x + it.first, player.y + it.second) }
        val pick = options.firstOrNull()
        if (pick != null) actorStep(e, pick.first, pick.second)
    }

    private fun randomStep(e: Actor) {
        val dirs = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        val options = dirs.filter { (dx, dy) ->
            val nx = e.x + dx
            val ny = e.y + dy
            level.isWalkable(nx, ny) && enemyAt(nx, ny) == null && !(nx == player.x && ny == player.y)
        }
        val pick = options.randomOrNull(Random(e.x * 31L + e.y * 17L + turnCount)) ?: return
        actorStep(e, pick.first, pick.second)
    }

    private fun actorStep(e: Actor, dx: Int, dy: Int) {
        if (dx == 0 && dy == 0) return
        val nx = e.x + dx
        val ny = e.y + dy
        if (!level.isWalkable(nx, ny)) return
        if (enemyAt(nx, ny) != null) return
        if (player.x == nx && player.y == ny) return
        if (dx != 0 && dy != 0) {
            if (!level.isWalkable(e.x + dx, e.y) || !level.isWalkable(e.x, e.y + dy)) return
        }
        e.animFromX = e.x.toFloat()
        e.animFromY = e.y.toFloat()
        e.animStart = System.currentTimeMillis()
        e.x = nx
        e.y = ny
        e.facingX = dx.toFloat()
        e.facingY = dy.toFloat()
        val t = level.tile(nx, ny)
        if (t.isDoor() && !t.doorOpen) t.doorOpen = true
    }

    private fun teleportEnemyNear(e: Actor) {
        val spot = nearBossFreeSpot(player) ?: return
        burst(e.x, e.y, Palette.VIOLET, 16, 1f)
        e.animFromX = e.x.toFloat()
        e.animFromY = e.y.toFloat()
        e.animStart = System.currentTimeMillis()
        e.x = spot.first
        e.y = spot.second
        burst(e.x, e.y, Palette.VIOLET, 16, 1f)
    }

    private fun alertEnemy(e: Actor) {
        if (!e.alive) return
        e.aiState = AiState.HUNT
        e.alertTurns = 12
        e.lastSeenX = player.x
        e.lastSeenY = player.y
    }

    private fun alertNearby(radius: Int) {
        for (e in enemies) {
            if (!e.alive) continue
            if (e.boss) continue
            if (e.distanceTo(player) <= radius) {
                e.aiState = AiState.HUNT
                e.alertTurns = max(e.alertTurns, 10)
                e.lastSeenX = player.x
                e.lastSeenY = player.y
            }
        }
    }

    // ------------------------------------------------------------------
    // Damage and death
    // ------------------------------------------------------------------

    fun damageEnemy(e: Actor, raw: Int, crit: Boolean): Int {
        var dmg = raw
        if (e.hasBuff(BuffType.SHIELD)) {
            val absorbed = min(dmg, e.buffs.first { it.type == BuffType.SHIELD }.power)
            val b = e.buffs.first { it.type == BuffType.SHIELD }
            b.power -= absorbed
            dmg -= absorbed
            if (b.power <= 0) e.removeBuff(BuffType.SHIELD)
        }
        val dealt = e.hurt(dmg)
        totalDamage += dealt
        e.hurtUntil = System.currentTimeMillis() + 180
        e.aiState = AiState.HUNT
        e.alertTurns = 12
        e.lastSeenX = player.x
        e.lastSeenY = player.y
        floatText(e.x, e.y, "-$dealt", if (crit) Palette.AMBER_HI else Palette.RED)
        burst(e.x, e.y, if (crit) 0xFFE0B45C.toInt() else 0xFFB04444.toInt(), if (crit) 12 else 7, 0.8f)
        if (dealt > 0) {
            if (player.weapon?.id == Items.HUNTER_KNIFE && rng.chance(0.15)) {
                e.addBuff(Buff(BuffType.SLOW, 2))
            }
            if (player.weapon?.id == Items.LANTERN_HAMMER && rng.chance(0.20)) {
                e.addBuff(Buff(BuffType.BURN, 2, 2))
            }
            if (player.weapon?.id == Items.VOID_DAGGER && rng.chance(0.20)) {
                e.addBuff(Buff(BuffType.FEAR, 2))
            }
        }
        if (!e.alive) onEnemyDeath(e)
        return dealt
    }

    fun damagePlayer(raw: Int, source: String) {
        if (gameOver || victory) return
        var dmg = max(1, raw)
        if (player.shield > 0) {
            val absorbed = min(dmg, player.shield)
            player.shield -= absorbed
            dmg -= absorbed
        }
        if (dmg <= 0) {
            floatText(player.x, player.y, "kalkan", Palette.TEAL)
            return
        }
        player.hurt(dmg)
        player.hurtUntil = System.currentTimeMillis() + 180
        floatText(player.x, player.y, "-$dmg", Palette.RED)
        burst(player.x, player.y, 0xFFB04444.toInt(), 8, 0.7f)
        if (player.hp <= 0) {
            die()
        } else if (source.isNotEmpty()) {
            message("$source sana $dmg hasar verdi.", Palette.RED)
        }
    }

    private fun onEnemyDeath(e: Actor) {
        if (!e.alive && e.deathAt > 0) return
        e.alive = false
        e.deathAt = System.currentTimeMillis()
        player.kills++
        burst(e.x, e.y, 0xFFB04444.toInt(), 18, 1.1f)
        burst(e.x, e.y, 0xFFE0B45C.toInt(), 10, 0.8f)
        sound(if (e.boss) "boss_death" else "death")
        val xp = Mobs.def(e.enemyId ?: Mobs.RAT).xp + depth * 2 + if (e.elite) 8 else 0
        val leveled = player.addXp(xp)
        floatText(e.x, e.y, "+$xp tecrübe", Palette.TEAL)
        message("${e.name} yok oldu. +$xp tecrübe.", Palette.TEXT_DIM)
        val gold = rng.nextInt(3, 8 + depth * 3) + if (e.elite) 10 else 0
        groundItems.add(GroundItem(e.x, e.y, Item(Items.GOLD, gold)))
        if (e.elite || (e.boss)) {
            val item = if (e.boss && rng.chance(0.65)) Item(Items.randomRelic(rng)) else randomLoot()
            item.upgrade = max(item.upgrade, if (e.boss) 2 else 1)
            groundItems.add(GroundItem(e.x, e.y, item))
        } else if (rng.chance(0.16)) {
            groundItems.add(GroundItem(e.x, e.y, randomLoot()))
        }
        if (e.enemyId == Mobs.SLIME && e.generation < 2) {
            repeat(2) {
                val spot = nearestFreeTile(e.x, e.y)
                if (spot != null) {
                    val mini = spawnEnemy(Mobs.SLIME, spot.first, spot.second, false, e.generation + 1)
                    if (mini != null) {
                        mini.maxHp = max(3, e.maxHp / 2)
                        mini.hp = mini.maxHp
                        mini.atkMin = max(1, e.atkMin - 1)
                        mini.atkMax = max(1, e.atkMax - 1)
                        mini.aiState = AiState.HUNT
                        mini.alertTurns = 20
                        mini.name = "Küçük Sızıntı"
                        burst(spot.first, spot.second, 0xFF9BE07A.toInt(), 8, 0.7f)
                    }
                }
            }
            message("Sızıntı ikiye bölündü!", Palette.GREEN)
        }
        if (leveled) {
            message("Seviye atladın! Artık ${player.level}. seviyedesin.", Palette.AMBER_HI)
            floatText(player.x, player.y, "SEVİYE ${player.level}", Palette.AMBER_HI)
            burst(player.x, player.y, Palette.AMBER_HI, 26, 1.2f)
            sound("levelup")
        }
        if (e.boss) {
            message("${e.name} düştü. Mührün yolu açıldı.", Palette.AMBER_HI)
            shake(18f)
            burst(e.x, e.y, Palette.AMBER_HI, 40, 1.8f)
            if (depth == 8) {
                groundItems.add(GroundItem(e.x, e.y, Item(Items.SEVENTH_SEAL)))
                message("Yedinci Mühür yerde parlıyor. Onu al.", Palette.AMBER_HI)
            } else {
                val t = level.tile(level.stairsX, level.stairsY)
                t.type = TileType.STAIRS.ordinal
                burst(level.stairsX, level.stairsY, Palette.AMBER_HI, 20, 1.2f)
                message("Merdivenin mührü çözüldü.", Palette.AMBER_HI)
            }
        }
    }

    private fun die() {
        if (gameOver) return
        player.hp = 0
        player.alive = false
        gameOver = true
        message("Işığın söndü.", Palette.RED)
        sound("gameover")
        burst(player.x, player.y, Palette.RED, 30, 1.4f)
        listener?.onGameOver()
    }

    private fun victory() {
        if (victory) return
        victory = true
        message("Yedinci Mühür kırıldı. Boşluk sustu.", Palette.AMBER_HI)
        sound("victory")
        events.add(VisualEvent.Banner("YEDİ MÜHÜR KIRILDI", "Boşluk sustu. Işık yeniden nefes alıyor.", Palette.AMBER_HI))
        listener?.onVictory()
    }

    // ------------------------------------------------------------------
    // Pickup / inventory / shop
    // ------------------------------------------------------------------

    private fun pickupAt(x: Int, y: Int) {
        val here = groundItems.filter { it.x == x && it.y == y }
        if (here.isEmpty()) return
        for (g in here.toList()) {
            if (g.item.id == Items.GOLD) {
                player.gold += g.item.count
                totalGoldFound += g.item.count
                floatText(x, y, "+${g.item.count} altın", Palette.AMBER_HI)
                message("${g.item.count} kor kristali topladın.", Palette.AMBER_HI)
                groundItems.remove(g)
                sound("gold")
                continue
            }
            if (g.item.id == Items.SEVENTH_SEAL) {
                groundItems.remove(g)
                player.addItem(g.item)
                sound("seal")
                burst(x, y, Palette.AMBER_HI, 40, 1.8f)
                victory()
                return
            }
            if (player.addItem(g.item)) {
                groundItems.remove(g)
                message("Aldın: ${itemName(g.item)}", Palette.TEXT)
                sound("pickup")
                floatText(x, y, itemName(g.item), Palette.TEAL)
            } else {
                message("Çanta dolu. ${itemName(g.item)} yerde kalıyor.", Palette.TEXT_FAINT)
            }
        }
    }

    fun pickupHere(): Boolean {
        if (groundItems.none { it.x == player.x && it.y == player.y }) return false
        pickupAt(player.x, player.y)
        return true
    }

    fun useItem(item: Item): Boolean {
        if (!canAct()) return false
        val def = item.def
        when (def.kind) {
            ItemKind.WEAPON, ItemKind.ARMOR, ItemKind.RELIC -> return equip(item)
            ItemKind.POTION, ItemKind.SCROLL -> {
                player.identified.add(item.id)
                val ok = applyConsumable(item)
                if (!ok) return false
                player.removeItem(item)
                endTurn()
                return true
            }
            ItemKind.OIL -> {
                if (player.oil >= 100) {
                    message("Fenerin zaten dolu.", Palette.TEXT_FAINT)
                    return false
                }
                player.oil = min(100, player.oil + def.oil)
                player.removeItem(item)
                message("Fenerini tazeledin. Yağ: ${player.oil}", Palette.AMBER_HI)
                sound("oil")
                burst(player.x, player.y, Palette.AMBER_HI, 10, 0.7f)
                endTurn()
                return true
            }
            ItemKind.FOOD -> {
                player.heal(def.heal)
                player.removeItem(item)
                message("Kara ekmeği yedin. +${def.heal} can.", Palette.GREEN)
                sound("eat")
                endTurn()
                return true
            }
            ItemKind.KEY -> {
                message("Anahtar, kilitli bir sandık ya da kapı ister.", Palette.TEXT_FAINT)
                return false
            }
            ItemKind.QUEST -> {
                if (item.id == Items.SEVENTH_SEAL) {
                    victory()
                    return true
                }
                return false
            }
        }
        return false
    }

    private fun applyConsumable(item: Item): Boolean {
        val def = item.def
        when (item.id) {
            Items.P_HEAL -> {
                val amount = max(1, player.maxHp * def.heal / 100)
                player.heal(amount)
                message("Kor İksiri yaralarını sardı (+$amount).", Palette.GREEN)
                burst(player.x, player.y, Palette.GREEN, 16, 0.9f)
                sound("potion")
            }
            Items.P_ENERGY -> {
                player.energyNow = player.maxEnergyWithGear()
                message("Kor Özü enerjini tazeledi.", Palette.AMBER_HI)
                burst(player.x, player.y, Palette.AMBER_HI, 16, 0.9f)
                sound("potion")
            }
            Items.P_STRENGTH -> {
                if (player.strengthUses >= 3) {
                    player.heal(8)
                    message("Demir Suyu artık damarlarını doyurmuyor; yalnızca ısıtıyor.", Palette.TEXT_DIM)
                } else {
                    player.strengthUses++
                    player.atkMin += 1
                    player.atkMax += 1
                    message("Demir Suyu damarlarına işledi. Gücün arttı.", Palette.AMBER_HI)
                }
                sound("potion")
            }
            Items.P_INVIS -> {
                player.invisibleTurns = max(player.invisibleTurns, 10)
                message("Sis seni sardı. 10 tur görünmezsin.", Palette.TEAL)
                burst(player.x, player.y, Palette.TEAL, 20, 1.0f)
                sound("potion")
            }
            Items.P_OIL -> {
                player.oil = min(100, player.oil + def.oil)
                message("Işık Yağı fenerini doldurdu. Yağ: ${player.oil}", Palette.AMBER_HI)
                sound("oil")
            }
            Items.P_FLAME -> {
                val target = enemies.filter { it.alive && it.distanceTo(player) <= 5 &&
                    Pathfinder.canSee(level, player.x, player.y, it.x, it.y) }
                    .minByOrNull { it.distanceTo(player) }
                if (target == null) {
                    message("Yakında görünen bir hedef yok. Şişe elinde kalıyor.", Palette.TEXT_FAINT)
                    return false
                }
                events.add(VisualEvent.Projectile(player.x, player.y, target.x, target.y, 0xFFE06A3F.toInt(), 1))
                burst(target.x, target.y, 0xFFE06A3F.toInt(), 24, 1.3f)
                events.add(VisualEvent.Ring(target.x, target.y, 0xFFE06A3F.toInt()))
                var dmg = rng.nextInt(7, 12) + depth
                dmg = max(1, dmg - target.def / 3)
                damageEnemy(target, dmg, false)
                target.addBuff(Buff(BuffType.BURN, 3, 3))
                message("Ateş Şişesi ${target.name}'e çarptı ($dmg).", Palette.AMBER_HI)
                sound("fire")
                shake(4f)
            }
            Items.S_MAP -> {
                for (t in level.tiles) t.explored = true
                message("Harita parşömeni bu katın hatırasını zihnine kazıdı.", Palette.TEAL)
                sound("scroll")
            }
            Items.S_UPGRADE -> {
                val target = player.weapon ?: player.armor
                if (target == null) {
                    message("Yükseltilecek bir eşya kuşanmış değilsin.", Palette.TEXT_FAINT)
                    return false
                }
                if (target.upgrade >= 5) {
                    message("${itemName(target)} zaten en yüksek seviyede.", Palette.TEXT_FAINT)
                    return false
                }
                target.upgrade++
                message("${itemName(target)} +${target.upgrade} oldu.", Palette.AMBER_HI)
                burst(player.x, player.y, Palette.AMBER_HI, 18, 1.0f)
                sound("upgrade")
            }
            Items.S_TELEPORT -> {
                burst(player.x, player.y, Palette.VIOLET, 20, 1.0f)
                teleportRandom()
                message("Dönüş parşömeni seni başka bir yere taşıdı.", Palette.VIOLET)
                sound("scroll")
            }
            Items.S_IDENTIFY -> {
                player.identified.addAll(Items.potionIds())
                player.identified.addAll(Items.scrollIds())
                message("Tüm iksir ve parşömenleri tanıdın.", Palette.TEAL)
                sound("scroll")
            }
            Items.S_FEAR -> {
                var count = 0
                for (e in enemies) {
                    if (e.alive && e.distanceTo(player) <= 8 && !e.boss) {
                        e.addBuff(Buff(BuffType.FEAR, 6))
                        e.aiState = AiState.FLEE
                        count++
                    }
                }
                message(if (count > 0) "$count düşman karanlığa kaçtı." else "Yakında kaçacak düşman yok.", Palette.VIOLET)
                sound(if (count > 0) "scroll" else "empty")
                if (count == 0) return false
            }
            else -> {
                message("Bu eşya burada kullanılamaz.", Palette.TEXT_FAINT)
                return false
            }
        }
        return true
    }

    fun equip(item: Item): Boolean {
        if (!canAct()) return false
        when (item.def.kind) {
            ItemKind.WEAPON -> {
                val old = player.weapon
                player.weapon = item
                player.inventory.remove(item)
                if (old != null) player.inventory.add(old)
                message("Kuşandın: ${itemName(item)}", Palette.AMBER_HI)
            }
            ItemKind.ARMOR -> {
                val old = player.armor
                player.armor = item
                player.inventory.remove(item)
                if (old != null) player.inventory.add(old)
                player.maxEnergy = player.maxEnergyWithGear()
                player.energyNow = min(player.energyNow, player.maxEnergy)
                message("Kuşandın: ${itemName(item)}", Palette.AMBER_HI)
            }
            ItemKind.RELIC -> {
                val old = player.relic
                player.relic = item
                player.inventory.remove(item)
                if (old != null) player.inventory.add(old)
                message("Yadigâr kuşandın: ${itemName(item)}", Palette.VIOLET)
                player.maxEnergy = player.maxEnergyWithGear()
            }
            else -> return false
        }
        sound("equip")
        endTurn()
        return true
    }

    fun dropItem(item: Item) {
        player.inventory.remove(item)
        groundItems.add(GroundItem(player.x, player.y, item))
        message("Bıraktın: ${itemName(item)}", Palette.TEXT_DIM)
    }

    fun buyFromShop(index: Int): Boolean {
        val entry = shopStock.getOrNull(index) ?: return false
        val price = entry.second
        if (player.gold < price) {
            message("Yeterli altının yok.", Palette.RED)
            sound("empty")
            return false
        }
        player.gold -= price
        val item = entry.first
        if (!player.addItem(item)) {
            message("Çanta dolu. Sandıkta yer yok.", Palette.TEXT_FAINT)
            return false
        }
        shopStock.removeAt(index)
        message("Satın aldın: ${itemName(item)}", Palette.AMBER_HI)
        sound("shop")
        return true
    }

    fun shopPriceText(index: Int): String {
        val e = shopStock.getOrNull(index) ?: return ""
        return "${e.second} altın"
    }

    private fun teleportRandom() {
        val candidates = ArrayList<Pair<Int, Int>>()
        for (y in 1 until MAP_H - 1) for (x in 1 until MAP_W - 1) {
            val t = level.tile(x, y)
            if (!t.walkable() || enemyAt(x, y) != null) continue
            if (!t.explored) continue
            if (x == player.x && y == player.y) continue
            candidates.add(x to y)
        }
        if (candidates.isEmpty()) return
        val spot = rng.pick(candidates)
        player.animFromX = player.x.toFloat()
        player.animFromY = player.y.toFloat()
        player.animStart = System.currentTimeMillis()
        player.x = spot.first
        player.y = spot.second
        burst(player.x, player.y, Palette.VIOLET, 20, 1.1f)
        pickupAt(player.x, player.y)
        recomputeFov()
    }

    fun healAfterBattle() {
        // reserved for future shrines
    }

    fun isAdjacent(x0: Int, y0: Int, x1: Int, y1: Int): Boolean =
        max(abs(x0 - x1), abs(y0 - y1)) == 1
}
