package com.wendigo.zifiri

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ScoreEntry(
    val name: String,
    val cls: String,
    val depth: Int,
    val level: Int,
    val gold: Int,
    val kills: Int,
    val victory: Boolean,
    val score: Int,
    val date: Long
)

object Store {
    private const val PREFS = "zifiri"
    private const val KEY_SAVE = "save_json"
    private const val KEY_SCORES = "scores"
    private const val KEY_SOUND = "sound"
    private const val KEY_HAPTIC = "haptic"
    private const val KEY_SHAKE = "shake"
    private const val KEY_HINT = "hint_seen"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var sound: Boolean
        get() = prefs(appContext).getBoolean(KEY_SOUND, true)
        set(value) = prefs(appContext).edit().putBoolean(KEY_SOUND, value).apply()

    var haptic: Boolean
        get() = prefs(appContext).getBoolean(KEY_HAPTIC, true)
        set(value) = prefs(appContext).edit().putBoolean(KEY_HAPTIC, value).apply()

    var shakeScreen: Boolean
        get() = prefs(appContext).getBoolean(KEY_SHAKE, true)
        set(value) = prefs(appContext).edit().putBoolean(KEY_SHAKE, value).apply()

    var hintSeen: Boolean
        get() = prefs(appContext).getBoolean(KEY_HINT, false)
        set(value) = prefs(appContext).edit().putBoolean(KEY_HINT, value).apply()

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        Sfx.enabled = sound
    }

    fun hasSave(context: Context): Boolean = prefs(context).getString(KEY_SAVE, null) != null

    fun saveSummary(context: Context): String? {
        val raw = prefs(context).getString(KEY_SAVE, null) ?: return null
        return try {
            val o = JSONObject(raw)
            val depth = o.optInt("depth", 1)
            val cls = HeroClass.byKey(o.optString("cls", "blade"))
            "$depth. Kat · ${cls.title}"
        } catch (_: Exception) {
            null
        }
    }

    fun clearSave(context: Context) {
        prefs(context).edit().remove(KEY_SAVE).apply()
    }

    fun save(context: Context, game: Game): Boolean {
        if (game.gameOver || game.victory) return false
        return try {
            val root = saveGame(game)
            prefs(context).edit().putString(KEY_SAVE, root.toString()).apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun load(context: Context): Game? {
        val raw = prefs(context).getString(KEY_SAVE, null) ?: return null
        return try {
            loadGame(JSONObject(raw))
        } catch (_: Exception) {
            null
        }
    }

    fun addScore(context: Context, game: Game): Int {
        val score = scoreOf(game)
        val entry = ScoreEntry(
            game.player.name,
            game.heroClass.title,
            game.depth,
            game.player.level,
            game.player.gold,
            game.player.kills,
            game.victory,
            score,
            System.currentTimeMillis()
        )
        val list = scores(context).toMutableList()
        list.add(entry)
        list.sortByDescending { it.score }
        val trimmed = list.take(8)
        val arr = JSONArray()
        for (s in trimmed) {
            arr.put(JSONObject().apply {
                put("name", s.name)
                put("cls", s.cls)
                put("depth", s.depth)
                put("level", s.level)
                put("gold", s.gold)
                put("kills", s.kills)
                put("victory", s.victory)
                put("score", s.score)
                put("date", s.date)
            })
        }
        prefs(context).edit().putString(KEY_SCORES, arr.toString()).apply()
        return score
    }

    fun scores(context: Context): List<ScoreEntry> {
        val raw = prefs(context).getString(KEY_SCORES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ScoreEntry(
                    o.optString("name", "Yolcu"),
                    o.optString("cls", "Vardiya"),
                    o.optInt("depth", 1),
                    o.optInt("level", 1),
                    o.optInt("gold", 0),
                    o.optInt("kills", 0),
                    o.optBoolean("victory", false),
                    o.optInt("score", 0),
                    o.optLong("date", 0L)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearScores(context: Context) {
        prefs(context).edit().remove(KEY_SCORES).apply()
    }

    fun scoreOf(game: Game): Int {
        val base = game.depth * 160 + game.player.level * 26 + game.player.kills * 7 +
            game.player.gold / 8 + game.totalDamage / 12 + (game.deepest - game.depth) * 30
        return base + if (game.victory) 6000 else 0
    }

    // ------------------------------------------------------------------
    // Save format
    // ------------------------------------------------------------------

    private fun itemJson(item: Item): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("n", item.count)
        put("u", item.upgrade)
    }

    private fun itemFrom(o: JSONObject): Item =
        Item(o.optString("id", Items.RUSTY_BLADE), o.optInt("n", 1), o.optInt("u", 0))

    private fun buffsJson(a: Actor): JSONArray {
        val arr = JSONArray()
        for (b in a.buffs) {
            arr.put(JSONObject().apply {
                put("t", b.type.name)
                put("n", b.turns)
                put("p", b.power)
            })
        }
        return arr
    }

    private fun readBuffs(a: Actor, arr: JSONArray?) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            try {
                a.buffs.add(Buff(BuffType.valueOf(o.optString("t", "POISON")), o.optInt("n", 1), o.optInt("p", 0)))
            } catch (_: Exception) {
            }
        }
    }

    private fun saveGame(game: Game): JSONObject {
        val root = JSONObject()
        root.put("v", 1)
        root.put("cls", game.heroClass.key)
        root.put("seed", game.rng.seed)
        root.put("depth", game.depth)
        root.put("turn", game.turnCount)
        root.put("started", game.startedAt)
        root.put("goldFound", game.totalGoldFound)
        root.put("damage", game.totalDamage)
        root.put("deepest", game.deepest)

        val p = game.player
        root.put("player", JSONObject().apply {
            put("x", p.x); put("y", p.y)
            put("hp", p.hp); put("maxHp", p.maxHp)
            put("atkMin", p.atkMin); put("atkMax", p.atkMax)
            put("def", p.def); put("acc", p.acc); put("eva", p.eva)
            put("level", p.level); put("xp", p.xp); put("xpNext", p.xpNext)
            put("gold", p.gold); put("oil", p.oil)
            put("maxEnergy", p.maxEnergy); put("energy", p.energyNow)
            put("kills", p.kills); put("turns", p.turns)
            put("strengthUses", p.strengthUses); put("steps", p.steps)
            put("invis", p.invisibleTurns); put("shield", p.shield)
            put("fx", p.facingX); put("fy", p.facingY)
            put("buffs", buffsJson(p))
            p.weapon?.let { put("weapon", itemJson(it)) }
            p.armor?.let { put("armor", itemJson(it)) }
            p.relic?.let { put("relic", itemJson(it)) }
            val inv = JSONArray()
            for (it in p.inventory) inv.put(itemJson(it))
            put("inv", inv)
            val ident = JSONArray()
            for (s in p.identified) ident.put(s)
            put("ident", ident)
        })

        val lvl = JSONObject()
        val l = game.level
        lvl.put("startX", l.startX); lvl.put("startY", l.startY)
        lvl.put("stairsX", l.stairsX); lvl.put("stairsY", l.stairsY)
        lvl.put("bossX", l.bossX); lvl.put("bossY", l.bossY)
        val tiles = JSONArray()
        for (t in l.tiles) {
            tiles.put(
                "${t.type},${t.variant},${t.feature},${t.featureData}," +
                    "${if (t.explored) 1 else 0},${if (t.visible) 1 else 0},${if (t.doorOpen) 1 else 0}"
            )
        }
        lvl.put("tiles", tiles)
        root.put("level", lvl)

        val enemies = JSONArray()
        for (e in game.enemies) {
            if (!e.alive) continue
            enemies.put(JSONObject().apply {
                put("id", e.enemyId ?: Mobs.RAT)
                put("x", e.x); put("y", e.y)
                put("name", e.name)
                put("hp", e.hp); put("maxHp", e.maxHp)
                put("atkMin", e.atkMin); put("atkMax", e.atkMax)
                put("def", e.def); put("acc", e.acc); put("eva", e.eva)
                put("energy", e.energy)
                put("elite", e.elite); put("boss", e.boss)
                put("ai", e.aiState.name)
                put("homeX", e.homeX); put("homeY", e.homeY)
                put("seenX", e.lastSeenX); put("seenY", e.lastSeenY)
                put("alert", e.alertTurns)
                put("gen", e.generation)
                put("fx", e.facingX); put("fy", e.facingY)
                put("buffs", buffsJson(e))
            })
        }
        root.put("enemies", enemies)

        val ground = JSONArray()
        for (g in game.groundItems) {
            ground.put(JSONObject().apply {
                put("x", g.x); put("y", g.y); put("item", itemJson(g.item))
            })
        }
        root.put("ground", ground)

        val shop = JSONArray()
        for ((item, price) in game.shopStock) {
            shop.put(JSONObject().apply {
                put("item", itemJson(item)); put("price", price)
            })
        }
        root.put("shop", shop)

        val msgs = JSONArray()
        for (m in game.messages.takeLast(14)) {
            msgs.put(JSONObject().apply {
                put("t", m.text); put("c", m.color)
            })
        }
        root.put("messages", msgs)
        return root
    }

    private fun loadGame(root: JSONObject): Game {
        val cls = HeroClass.byKey(root.optString("cls", "blade"))
        val depth = root.optInt("depth", 1)
        val rng = Rng(root.optLong("seed", System.nanoTime()))

        val po = root.getJSONObject("player")
        val player = Player(cls, po.optInt("x", 1), po.optInt("y", 1))
        player.hp = po.optInt("hp", player.maxHp)
        player.maxHp = po.optInt("maxHp", player.maxHp)
        player.atkMin = po.optInt("atkMin", player.atkMin)
        player.atkMax = po.optInt("atkMax", player.atkMax)
        player.def = po.optInt("def", player.def)
        player.acc = po.optDouble("acc", player.acc)
        player.eva = po.optDouble("eva", player.eva)
        player.level = po.optInt("level", 1)
        player.xp = po.optInt("xp", 0)
        player.xpNext = po.optInt("xpNext", 12)
        player.gold = po.optInt("gold", 0)
        player.oil = po.optInt("oil", 100)
        player.maxEnergy = po.optInt("maxEnergy", cls.energy)
        player.energyNow = po.optInt("energy", player.maxEnergy)
        player.kills = po.optInt("kills", 0)
        player.turns = po.optInt("turns", 0)
        player.strengthUses = po.optInt("strengthUses", 0)
        player.steps = po.optInt("steps", 0)
        player.invisibleTurns = po.optInt("invis", 0)
        player.shield = po.optInt("shield", 0)
        player.facingX = po.optDouble("fx", 0.0).toFloat()
        player.facingY = po.optDouble("fy", 1.0).toFloat()
        readBuffs(player, po.optJSONArray("buffs"))
        po.optJSONObject("weapon")?.let { player.weapon = itemFrom(it) }
        po.optJSONObject("armor")?.let { player.armor = itemFrom(it) }
        po.optJSONObject("relic")?.let { player.relic = itemFrom(it) }
        po.optJSONArray("inv")?.let { arr ->
            for (i in 0 until arr.length()) player.inventory.add(itemFrom(arr.getJSONObject(i)))
        }
        po.optJSONArray("ident")?.let { arr ->
            for (i in 0 until arr.length()) player.identified.add(arr.optString(i))
        }
        player.identified.add(Items.P_HEAL)

        val lo = root.getJSONObject("level")
        val tiles = arrayOfNulls<Tile>(MAP_W * MAP_H)
        val ta = lo.getJSONArray("tiles")
        for (i in 0 until MAP_W * MAP_H) {
            if (i < ta.length()) {
                val parts = ta.optString(i, "0,0,0,0,0,0,0").split(",")
                val t = Tile()
                if (parts.size >= 7) {
                    t.type = parts[0].toIntOrNull() ?: 0
                    t.variant = parts[1].toIntOrNull() ?: 0
                    t.feature = parts[2].toIntOrNull() ?: 0
                    t.featureData = parts[3].toIntOrNull() ?: 0
                    t.explored = parts[4] == "1"
                    t.visible = parts[5] == "1"
                    t.doorOpen = parts[6] == "1"
                }
                tiles[i] = t
            } else tiles[i] = Tile()
        }
        @Suppress("UNCHECKED_CAST")
        val level = Level(depth, tiles as Array<Tile>, rng)
        level.startX = lo.optInt("startX", 1)
        level.startY = lo.optInt("startY", 1)
        level.stairsX = lo.optInt("stairsX", 1)
        level.stairsY = lo.optInt("stairsY", 1)
        level.bossX = lo.optInt("bossX", 0)
        level.bossY = lo.optInt("bossY", 0)

        val game = Game(cls, rng, player, level, depth)
        game.turnCount = root.optLong("turn", 0L)
        game.startedAt = root.optLong("started", System.currentTimeMillis())
        game.totalGoldFound = root.optInt("goldFound", 0)
        game.totalDamage = root.optInt("damage", 0)
        game.deepest = root.optInt("deepest", depth)

        root.optJSONArray("enemies")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val d = Mobs.def(o.optString("id", Mobs.RAT))
                val e = Actor(
                    o.optInt("x", 1), o.optInt("y", 1),
                    o.optString("name", d.name),
                    o.optInt("hp", d.hp), o.optInt("maxHp", d.hp),
                    o.optInt("atkMin", d.atkMin), o.optInt("atkMax", d.atkMax),
                    o.optInt("def", d.def), o.optDouble("acc", d.acc), o.optDouble("eva", d.eva)
                )
                e.enemyId = o.optString("id", Mobs.RAT)
                e.energy = o.optInt("energy", 0)
                e.elite = o.optBoolean("elite", false)
                e.boss = o.optBoolean("boss", false)
                try {
                    e.aiState = AiState.valueOf(o.optString("ai", "SLEEP"))
                } catch (_: Exception) {
                    e.aiState = AiState.SLEEP
                }
                e.homeX = o.optInt("homeX", e.x)
                e.homeY = o.optInt("homeY", e.y)
                e.lastSeenX = o.optInt("seenX", -1)
                e.lastSeenY = o.optInt("seenY", -1)
                e.alertTurns = o.optInt("alert", 0)
                e.generation = o.optInt("gen", 0)
                e.facingX = o.optDouble("fx", 0.0).toFloat()
                e.facingY = o.optDouble("fy", 1.0).toFloat()
                e.animFromX = e.x.toFloat()
                e.animFromY = e.y.toFloat()
                readBuffs(e, o.optJSONArray("buffs"))
                game.enemies.add(e)
            }
        }

        root.optJSONArray("ground")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                game.groundItems.add(GroundItem(o.optInt("x", 1), o.optInt("y", 1), itemFrom(o.getJSONObject("item"))))
            }
        }

        root.optJSONArray("shop")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                game.shopStock.add(itemFrom(o.getJSONObject("item")) to o.optInt("price", 20))
            }
        }

        root.optJSONArray("messages")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                game.messages.add(GameMessage(o.optString("t", ""), o.optInt("c", Palette.TEXT)))
            }
        }

        game.recomputeFov()
        game.message("Kaldığın yerden devam ediyorsun.", Palette.AMBER_HI)
        return game
    }
}
