package com.wendigo.zifiri

import kotlin.math.max
import kotlin.math.min

const val MAP_W = 44
const val MAP_H = 44
const val MAX_DEPTH = 8

fun argb(a: Int, r: Int, g: Int, b: Int): Int =
    ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

object Palette {
    const val BG = 0xFF07090C.toInt()
    const val PANEL = 0xFF12161C.toInt()
    const val PANEL_HI = 0xFF1B222B.toInt()
    const val STROKE = 0xFF2C3540.toInt()
    const val AMBER = 0xFFF2B35C.toInt()
    const val AMBER_HI = 0xFFFFD08A.toInt()
    const val AMBER_DIM = 0xFF8A6533.toInt()
    const val TEAL = 0xFF4FD1C5.toInt()
    const val TEAL_DIM = 0xFF235E59.toInt()
    const val VIOLET = 0xFFA78BFA.toInt()
    const val RED = 0xFFE05A5A.toInt()
    const val GREEN = 0xFF7BC47F.toInt()
    const val TEXT = 0xFFEDE7DA.toInt()
    const val TEXT_DIM = 0xFFA29B8C.toInt()
    const val TEXT_FAINT = 0xFF6E685D.toInt()
    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
}

object Biome {
    const val SARNIC = 0
    const val KRISTAL = 1
    const val KUL = 2
    const val MUHUR = 3
    const val BOSLUK = 4

    fun forDepth(depth: Int): Int = when {
        depth <= 2 -> SARNIC
        depth <= 4 -> KRISTAL
        depth <= 6 -> KUL
        depth == 7 -> MUHUR
        else -> BOSLUK
    }

    fun name(depth: Int): String = when (forDepth(depth)) {
        SARNIC -> "Sarnıç"
        KRISTAL -> "Kristal Kuyu"
        KUL -> "Kül Ocakları"
        MUHUR -> "Mühür Salonu"
        else -> "Boşluk Kalbi"
    }

    fun subtitle(depth: Int): String = when (forDepth(depth)) {
        SARNIC -> "Su damlıyor, taş nefes alıyor."
        KRISTAL -> "Kristaller seni izliyor."
        KUL -> "Küller eski bir ateşi hatırlıyor."
        MUHUR -> "Yedinci mühür uyanmak üzere."
        else -> "Işığın bittiği yer."
    }

    fun floorColor(depth: Int, variant: Int): Int {
        val v = (variant and 7) - 3
        return when (forDepth(depth)) {
            SARNIC -> argb(255, 34 + v, 46 + v, 44 + v)
            KRISTAL -> argb(255, 37 + v, 35 + v, 52 + v)
            KUL -> argb(255, 48 + v, 38 + v, 32 + v)
            MUHUR -> argb(255, 43 + v, 42 + v, 37 + v)
            else -> argb(255, 32 + v, 24 + v, 30 + v)
        }
    }

    fun wallColor(depth: Int, variant: Int): Int {
        val v = (variant and 7) - 3
        return when (forDepth(depth)) {
            SARNIC -> argb(255, 27 + v, 42 + v, 39 + v)
            KRISTAL -> argb(255, 29 + v, 27 + v, 44 + v)
            KUL -> argb(255, 40 + v, 31 + v, 27 + v)
            MUHUR -> argb(255, 36 + v, 35 + v, 31 + v)
            else -> argb(255, 27 + v, 20 + v, 25 + v)
        }
    }

    fun accent(depth: Int): Int = when (forDepth(depth)) {
        SARNIC -> 0xFF6BBFAE.toInt()
        KRISTAL -> 0xFF9C8CFF.toInt()
        KUL -> 0xFFE39A5C.toInt()
        MUHUR -> 0xFFF2C86B.toInt()
        else -> 0xFFDF6B7E.toInt()
    }
}

// ---------------------------------------------------------------------------
// Map
// ---------------------------------------------------------------------------

enum class TileType { WALL, FLOOR, WATER, LAVA, RUBBLE, DOOR, STAIRS, PORTAL, PILLAR, WALL_TORCH, VOID }

enum class Feature { NONE, CHEST, LOCKED_CHEST, ALTAR, CRYSTAL, MUSHROOM, BONES, WEED, SHOP, TRAP_SPIKE, TRAP_POISON, TRAP_ALARM, TRAP_TELEPORT }

class Tile(
    var type: Int = TileType.WALL.ordinal,
    var variant: Int = 0,
    var feature: Int = Feature.NONE.ordinal,
    var featureData: Int = 0,
    var explored: Boolean = false,
    var visible: Boolean = false,
    var doorOpen: Boolean = false
) {
    val t: TileType get() = TileType.values()[type]
    val f: Feature get() = Feature.values()[feature]

    fun isWall(): Boolean = t == TileType.WALL
    fun isDoor(): Boolean = t == TileType.DOOR
    fun isStairs(): Boolean = t == TileType.STAIRS
    fun isPortal(): Boolean = t == TileType.PORTAL

    fun blocksSight(): Boolean = when (t) {
        TileType.WALL, TileType.PILLAR, TileType.RUBBLE -> true
        TileType.DOOR -> !doorOpen
        else -> false
    }

    fun blocksMove(): Boolean = when (t) {
        TileType.WALL, TileType.LAVA, TileType.PILLAR, TileType.RUBBLE, TileType.VOID -> true
        TileType.DOOR -> !doorOpen
        else -> f == Feature.CHEST || f == Feature.LOCKED_CHEST || f == Feature.SHOP
    }

    fun walkable(): Boolean = !blocksMove()

    fun isTrap(): Boolean = f == Feature.TRAP_SPIKE || f == Feature.TRAP_POISON || f == Feature.TRAP_ALARM || f == Feature.TRAP_TELEPORT
}

class Level(
    val depth: Int,
    val tiles: Array<Tile> = Array(MAP_W * MAP_H) { Tile() },
    val rng: Rng
) {
    fun idx(x: Int, y: Int): Int = y * MAP_W + x
    fun inBounds(x: Int, y: Int): Boolean = x in 0 until MAP_W && y in 0 until MAP_H
    fun tile(x: Int, y: Int): Tile = tiles[idx(x, y)]
    fun tileOrNull(x: Int, y: Int): Tile? = if (inBounds(x, y)) tiles[idx(x, y)] else null
    fun isWalkable(x: Int, y: Int): Boolean = inBounds(x, y) && tile(x, y).walkable()
    fun blocksSight(x: Int, y: Int): Boolean = !inBounds(x, y) || tile(x, y).blocksSight()

    var startX = 1
    var startY = 1
    var stairsX = 1
    var stairsY = 1
    var bossX = 0
    var bossY = 0
}

// ---------------------------------------------------------------------------
// Heroes
// ---------------------------------------------------------------------------

enum class HeroClass(
    val key: String,
    val title: String,
    val subtitle: String,
    val desc: String,
    val hp: Int,
    val atkMin: Int,
    val atkMax: Int,
    val def: Int,
    val acc: Double,
    val eva: Double,
    val energy: Int,
    val skillName: String,
    val skillCost: Int,
    val skillDesc: String,
    val accent: Int
) {
    BLADE(
        "blade", "Vardiya", "Kılıç Ustası",
        "Ön safta savaşır. Hilal Darbesi ile çevresindeki düşmanları savurur.",
        38, 4, 6, 2, 0.02, 0.02, 10,
        "Hilal Darbesi", 3, "Yanındaki tüm düşmanlara ağır vurur ve onları geri iter.",
        0xFFE3A153.toInt()
    ),
    EMBER(
        "ember", "Kor Ustası", "Ateş Büyücüsü",
        "Uzaktan kor yağdırır. Kırılgandır ama kalabalığı küle çevirir.",
        28, 3, 5, 1, 0.03, 0.01, 12,
        "Kor Yağmuru", 4, "Görüş alanındaki bir noktaya kor yağdırır; alanı yakar.",
        0xFFE0654F.toInt()
    ),
    SHADE(
        "shade", "Sis Avcısı", "Gölge Nişancı",
        "Sisler içinde kaybolur, uzaktan ok atar. Sis Adımı ile yer değiştirir.",
        31, 3, 5, 2, 0.08, 0.06, 10,
        "Sis Adımı", 3, "Görüş alanındaki bir noktaya sıçrar ve kısa süre görünmez olur.",
        0xFF6FC7BE.toInt()
    );

    companion object {
        fun byKey(key: String): HeroClass = values().firstOrNull { it.key == key } ?: BLADE
    }
}

// ---------------------------------------------------------------------------
// Items
// ---------------------------------------------------------------------------

enum class ItemKind { WEAPON, ARMOR, RELIC, POTION, SCROLL, OIL, KEY, FOOD, QUEST }

data class ItemDef(
    val id: String,
    val name: String,
    val kind: ItemKind,
    val tier: Int = 0,
    val atkMin: Int = 0,
    val atkMax: Int = 0,
    val def: Int = 0,
    val eva: Double = 0.0,
    val acc: Double = 0.0,
    val crit: Double = 0.0,
    val fire: Int = 0,
    val lifesteal: Double = 0.0,
    val energyBonus: Int = 0,
    val heal: Int = 0,
    val oil: Int = 0,
    val price: Int = 20,
    val stackable: Boolean = false,
    val flavor: String = "",
    val desc: String = "",
    val color: Int = Palette.TEXT
)

object Items {
    const val RUSTY_BLADE = "rusty_blade"
    const val HUNTER_KNIFE = "hunter_knife"
    const val EMBER_STAFF = "ember_staff"
    const val SHORT_BOW = "short_bow"
    const val SEAL_SWORD = "seal_sword"
    const val CRYSTAL_SPEAR = "crystal_spear"
    const val ASH_AXE = "ash_axe"
    const val VOID_DAGGER = "void_dagger"
    const val LANTERN_HAMMER = "lantern_hammer"

    const val LEATHER_VEST = "leather_vest"
    const val TORN_ROBE = "torn_robe"
    const val SHADE_CLOAK = "shade_cloak"
    const val CHAIN_SHIRT = "chain_shirt"
    const val CRYSTAL_MAIL = "crystal_mail"
    const val ASH_PLATE = "ash_plate"
    const val SEAL_AEGIS = "seal_aegis"

    const val EMBER_HEART = "ember_heart"
    const val MIST_BREATH = "mist_breath"
    const val SEAL_SHARD = "seal_shard"
    const val OPEN_EYE = "open_eye"
    const val BLOODSTONE = "bloodstone"

    const val P_HEAL = "p_heal"
    const val P_ENERGY = "p_energy"
    const val P_STRENGTH = "p_strength"
    const val P_INVIS = "p_invis"
    const val P_OIL = "p_oil"
    const val P_FLAME = "p_flame"

    const val S_MAP = "s_map"
    const val S_UPGRADE = "s_upgrade"
    const val S_TELEPORT = "s_teleport"
    const val S_IDENTIFY = "s_identify"
    const val S_FEAR = "s_fear"

    const val OIL_FLASK = "oil_flask"
    const val BRASS_KEY = "brass_key"
    const val BREAD = "bread"
    const val SEVENTH_SEAL = "seventh_seal"
    const val GOLD = "gold"

    val DEFS: Map<String, ItemDef> = listOf(
        ItemDef(RUSTY_BLADE, "Paslı Kılıç", ItemKind.WEAPON, tier = 1, atkMin = 1, atkMax = 3, price = 18,
            desc = "Kuşakta taşınan, kenarı yenmiş eski bir kılıç. Yine de bir karanlıkta işe yarar.", color = 0xFFB7AA95.toInt()),
        ItemDef(HUNTER_KNIFE, "Avcı Bıçağı", ItemKind.WEAPON, tier = 1, atkMin = 1, atkMax = 4, crit = 0.08, price = 26,
            desc = "Kısa, hafif ve ölümcül. İyi bir darbe her şeyi bitirir.", color = 0xFFB9C3C8.toInt()),
        ItemDef(EMBER_STAFF, "Kor Asası", ItemKind.WEAPON, tier = 1, atkMin = 1, atkMax = 3, fire = 1, price = 28,
            desc = "Ucundaki kızıl taş, sönmüş gibi görünse de içten içe yanar.", color = 0xFFE07A4F.toInt()),
        ItemDef(SHORT_BOW, "Kısa Yay", ItemKind.WEAPON, tier = 1, atkMin = 1, atkMax = 3, acc = 0.04, price = 26,
            desc = "Sis Avcısı'nın yayı. Sessizdir, ama yankısı uzun sürer.", color = 0xFFC4A46A.toInt()),
        ItemDef(SEAL_SWORD, "Mühür Kılıcı", ItemKind.WEAPON, tier = 2, atkMin = 2, atkMax = 5, price = 55,
            desc = "Kabzasındaki mühür, doğru kullanıldığında ağırlaşır.", color = 0xFFD8CDB2.toInt()),
        ItemDef(CRYSTAL_SPEAR, "Kristal Mızrak", ItemKind.WEAPON, tier = 2, atkMin = 2, atkMax = 6, acc = 0.03, price = 62,
            desc = "Kristal uç, karanlıkta soluk mavi bir iz bırakır.", color = 0xFF9FC7E8.toInt()),
        ItemDef(ASH_AXE, "Kül Baltası", ItemKind.WEAPON, tier = 3, atkMin = 4, atkMax = 9, acc = -0.06, price = 95,
            desc = "Ağır ve acımasız. Savururken küller havalanır.", color = 0xFFD07B4A.toInt()),
        ItemDef(VOID_DAGGER, "Boşluk Hançeri", ItemKind.WEAPON, tier = 4, atkMin = 5, atkMax = 10, crit = 0.14, lifesteal = 0.18, price = 170,
            desc = "Karanlığın kendisinden dövülmüş gibi. Kestiği yerden ışık sızar.", color = 0xFFB08BE0.toInt()),
        ItemDef(LANTERN_HAMMER, "Fener Tokmağı", ItemKind.WEAPON, tier = 3, atkMin = 3, atkMax = 7, fire = 1, price = 110,
            desc = "İçi kor dolu bir fenerin demir gövdesi. Vurduğu yeri ısıtır.", color = 0xFFE3B15C.toInt()),

        ItemDef(LEATHER_VEST, "Deri Yelek", ItemKind.ARMOR, tier = 1, def = 1, price = 20,
            desc = "Basit ama dürüst bir koruma.", color = 0xFFA67B52.toInt()),
        ItemDef(TORN_ROBE, "Yırtık Cübbe", ItemKind.ARMOR, tier = 1, def = 0, energyBonus = 2, price = 22,
            desc = "Yırtıklarından soğuk sızar, ama zihni serbest bırakır.", color = 0xFF6C7FA8.toInt()),
        ItemDef(SHADE_CLOAK, "Gölge Pelerini", ItemKind.ARMOR, tier = 1, def = 1, eva = 0.08, price = 30,
            desc = "Kenarları sisle dikilmiş gibi dalgalanır.", color = 0xFF5E8C88.toInt()),
        ItemDef(CHAIN_SHIRT, "Zincir Gömlek", ItemKind.ARMOR, tier = 2, def = 2, price = 55,
            desc = "Her halkası başka bir hayatın bedeliyle dövülmüş.", color = 0xFF9AA5AE.toInt()),
        ItemDef(CRYSTAL_MAIL, "Kristal Zırh", ItemKind.ARMOR, tier = 3, def = 3, price = 95,
            desc = "Kristal plakalar darbeyi emer, sonra sessizce çatlar.", color = 0xFF8CB8D8.toInt()),
        ItemDef(ASH_PLATE, "Kül Plakası", ItemKind.ARMOR, tier = 4, def = 4, eva = -0.05, price = 120,
            desc = "Küllerden dövülmüş ağır zırh. Yavaşlatır ama yıkmak zordur.", color = 0xFFB07858.toInt()),
        ItemDef(SEAL_AEGIS, "Mühür Zırhı", ItemKind.ARMOR, tier = 5, def = 5, eva = 0.03, price = 190,
            desc = "Yedi mühürden birinin parçasıyla güçlenmiş efsanevi zırh.", color = 0xFFE4C477.toInt()),

        ItemDef(EMBER_HEART, "Kor Kalbi", ItemKind.RELIC, tier = 2, fire = 2, price = 90,
            desc = "Ateş, seni ısıtmak yerine besliyor.", color = 0xFFE06A4E.toInt()),
        ItemDef(MIST_BREATH, "Sis Nefesi", ItemKind.RELIC, tier = 2, eva = 0.10, price = 95,
            desc = "Darbeler sisin içinde kaybolur.", color = 0xFF74C5BC.toInt()),
        ItemDef(SEAL_SHARD, "Mühür Parçası", ItemKind.RELIC, tier = 2, energyBonus = 2, price = 100,
            desc = "Kırık bir mühürden kalan parça. İçindeki ışık hâlâ atıyor.", color = 0xFFE8C66F.toInt()),
        ItemDef(OPEN_EYE, "Açık Göz", ItemKind.RELIC, tier = 2, acc = 0.10, price = 85,
            desc = "Kapalı bir göz, karanlıkta açık kalır. Tuzakları fısıldar.", color = 0xFFB9A7D9.toInt()),
        ItemDef(BLOODSTONE, "Kan Taşı", ItemKind.RELIC, tier = 3, lifesteal = 0.12, price = 120,
            desc = "Yaraladığın her şeyden biraz hayat sızar.", color = 0xFFC45461.toInt()),

        ItemDef(P_HEAL, "Kor İksiri", ItemKind.POTION, tier = 1, heal = 45, price = 28, stackable = true,
            flavor = "Kızıl", desc = "Yaraları sıcak bir ışıkla kapatır.", color = 0xFFD8525B.toInt()),
        ItemDef(P_ENERGY, "Kor Özü", ItemKind.POTION, tier = 1, price = 24, stackable = true,
            flavor = "Turuncu", desc = "İçindeki kor, yeteneklerini yeniden tutuşturur.", color = 0xFFE58A45.toInt()),
        ItemDef(P_STRENGTH, "Demir Suyu", ItemKind.POTION, tier = 2, price = 45, stackable = true,
            flavor = "Gümüş", desc = "Damarlarında ağırlaşan bir güç bırakır.", color = 0xFF93A8B5.toInt()),
        ItemDef(P_INVIS, "Sis İksiri", ItemKind.POTION, tier = 2, price = 35, stackable = true,
            flavor = "Duman", desc = "Kısa süreliğine karanlığın bir parçası olursun.", color = 0xFF84A8A3.toInt()),
        ItemDef(P_OIL, "Işık Yağı", ItemKind.POTION, tier = 1, oil = 60, price = 22, stackable = true,
            flavor = "Sarı", desc = "Fenerin için saf, parlak bir yakıt.", color = 0xFFE3C05A.toInt()),
        ItemDef(P_FLAME, "Ateş Şişesi", ItemKind.POTION, tier = 2, price = 32, stackable = true,
            flavor = "Kor", desc = "Görüş alanındaki en yakın düşmana kor yağdırır.", color = 0xFFE06A3F.toInt()),

        ItemDef(S_MAP, "Harita Parşömeni", ItemKind.SCROLL, tier = 1, price = 30, stackable = true,
            flavor = "Kadim", desc = "Bu katın hatırasını zihnine kazır.", color = 0xFFD8C79A.toInt()),
        ItemDef(S_UPGRADE, "Yükseltme Parşömeni", ItemKind.SCROLL, tier = 2, price = 60, stackable = true,
            flavor = "Mühürlü", desc = "Kuşandığın silahı veya zırhı güçlendirir.", color = 0xFFE5C978.toInt()),
        ItemDef(S_TELEPORT, "Dönüş Parşömeni", ItemKind.SCROLL, tier = 2, price = 35, stackable = true,
            flavor = "Kıvrık", desc = "Seni bu katta keşfettiğin bir yere taşır.", color = 0xFFB9A6D8.toInt()),
        ItemDef(S_IDENTIFY, "Tanıma Parşömeni", ItemKind.SCROLL, tier = 1, price = 30, stackable = true,
            flavor = "Açık", desc = "Bu yolculuktaki tüm iksir ve parşömenleri tanımanı sağlar.", color = 0xFFC8D7D4.toInt()),
        ItemDef(S_FEAR, "Korku Parşömeni", ItemKind.SCROLL, tier = 2, price = 38, stackable = true,
            flavor = "Kara", desc = "Yakındaki düşmanları karanlığa kaçırır.", color = 0xFF9983B9.toInt()),

        ItemDef(OIL_FLASK, "Yağ Şişesi", ItemKind.OIL, tier = 1, oil = 60, price = 20, stackable = true,
            desc = "Fenerini tazeler. Karanlıkta yağ, ekmekten değerlidir.", color = 0xFFE3C05A.toInt()),
        ItemDef(BRASS_KEY, "Pirinç Anahtar", ItemKind.KEY, tier = 1, price = 15, stackable = true,
            desc = "Kilitli sandıkları ve kapıları açar.", color = 0xFFD2A85C.toInt()),
        ItemDef(BREAD, "Kara Ekmek", ItemKind.FOOD, tier = 1, heal = 16, price = 10, stackable = true,
            desc = "Sert, kuru ve biraz isli. Yine de sıcak tutar.", color = 0xFFB98B54.toInt()),
        ItemDef(SEVENTH_SEAL, "Yedinci Mühür", ItemKind.QUEST, tier = 5, price = 0,
            desc = "Yedi mühür kırıldı. Boşluk sustu.", color = 0xFFFFD98A.toInt()),
        ItemDef(GOLD, "Kor Kristali", ItemKind.QUEST, tier = 0, price = 1, stackable = true,
            desc = "Karanlıkta bile parlayan, sıcak bir para birimi.", color = 0xFFE8C35C.toInt())
    ).associateBy { it.id }

    fun def(id: String): ItemDef = DEFS[id] ?: DEFS[RUSTY_BLADE]!!

    fun weaponsForDepth(depth: Int): List<String> = when {
        depth <= 1 -> listOf(RUSTY_BLADE, HUNTER_KNIFE, EMBER_STAFF, SHORT_BOW)
        depth <= 3 -> listOf(RUSTY_BLADE, HUNTER_KNIFE, EMBER_STAFF, SHORT_BOW, SEAL_SWORD, CRYSTAL_SPEAR)
        depth <= 5 -> listOf(SEAL_SWORD, CRYSTAL_SPEAR, ASH_AXE, LANTERN_HAMMER)
        else -> listOf(CRYSTAL_SPEAR, ASH_AXE, LANTERN_HAMMER, VOID_DAGGER)
    }

    fun armorForDepth(depth: Int): List<String> = when {
        depth <= 1 -> listOf(LEATHER_VEST, TORN_ROBE, SHADE_CLOAK)
        depth <= 3 -> listOf(LEATHER_VEST, TORN_ROBE, SHADE_CLOAK, CHAIN_SHIRT)
        depth <= 5 -> listOf(CHAIN_SHIRT, CRYSTAL_MAIL, ASH_PLATE)
        else -> listOf(CRYSTAL_MAIL, ASH_PLATE, SEAL_AEGIS)
    }

    fun randomRelic(rng: Rng): String = rng.pick(listOf(EMBER_HEART, MIST_BREATH, SEAL_SHARD, OPEN_EYE, BLOODSTONE))

    fun potionIds(): List<String> = listOf(P_HEAL, P_ENERGY, P_STRENGTH, P_INVIS, P_OIL, P_FLAME)
    fun scrollIds(): List<String> = listOf(S_MAP, S_UPGRADE, S_TELEPORT, S_IDENTIFY, S_FEAR)

    fun isIdentifiable(id: String): Boolean {
        val k = def(id).kind
        return k == ItemKind.POTION || k == ItemKind.SCROLL
    }
}

class Item(var id: String, var count: Int = 1, var upgrade: Int = 0) {
    val def: ItemDef get() = Items.def(id)
    fun copyItem(): Item = Item(id, count, upgrade)
}

// ---------------------------------------------------------------------------
// Actors
// ---------------------------------------------------------------------------

enum class AiState { SLEEP, WANDER, HUNT, FLEE }
enum class Behavior { MELEE, RANGED, CHARGER, TURRET, SPLITTER, TELEPORTER, BOSS, HEALER }
enum class BuffType { POISON, BURN, SLOW, INVISIBLE, FEAR, SHIELD, HASTE }

class Buff(var type: BuffType, var turns: Int, var power: Int = 0)

data class EnemyDef(
    val id: String,
    val name: String,
    val hp: Int,
    val atkMin: Int,
    val atkMax: Int,
    val def: Int,
    val acc: Double,
    val eva: Double,
    val speed: Int,
    val xp: Int,
    val behavior: Behavior,
    val color: Int,
    val minDepth: Int,
    val maxDepth: Int,
    val scale: Float = 1f,
    val rangedRange: Int = 0,
    val desc: String = ""
)

object Mobs {
    const val SLIME = "slime"
    const val RAT = "rat"
    const val SPIDER = "spider"
    const val HOUND = "hound"
    const val THORN = "thorn"
    const val BAT = "bat"
    const val SKELETON = "skeleton"
    const val FLAME = "flame"
    const val SHADE = "shade"
    const val BOSS_CRYSTAL = "boss_crystal"
    const val BOSS_ASH = "boss_ash"
    const val BOSS_VOID = "boss_void"

    val DEFS: Map<String, EnemyDef> = listOf(
        EnemyDef(SLIME, "Sızıntı", 9, 1, 3, 0, 0.0, 0.0, 85, 5, Behavior.SPLITTER, 0xFF6FAE61.toInt(), 1, 4, 1f,
            desc = "Sarnıcın duvarlarından sızan yeşil bir tortu. Öldürünce ikiye bölünür."),
        EnemyDef(RAT, "Kemirgen", 7, 2, 4, 0, 0.05, 0.08, 130, 4, Behavior.MELEE, 0xFF9B7A58.toInt(), 1, 5, 0.85f,
            desc = "Karanlıkta hızlı hareket eden, aç ve cesur bir yaratık."),
        EnemyDef(SPIDER, "Kristal Örümcek", 15, 2, 5, 1, 0.05, 0.05, 100, 9, Behavior.RANGED, 0xFF9A7BD0.toInt(), 2, 6, 1f,
            rangedRange = 5, desc = "Kristal damarlarında yuvalanan, ağını uzaktan atan bir avcı."),
        EnemyDef(HOUND, "Kül Tazısı", 20, 3, 6, 1, 0.08, 0.10, 120, 12, Behavior.CHARGER, 0xFFD07A4C.toInt(), 3, 7, 1f,
            desc = "Küllerin arasından fırlar; hızını darbeye çevirir."),
        EnemyDef(THORN, "Dikenli Yosun", 24, 2, 5, 2, 0.04, 0.0, 70, 11, Behavior.TURRET, 0xFF6E9A5C.toInt(), 3, 7, 1f,
            rangedRange = 6, desc = "Toprağa kök salmış, dikenlerini uzaktan fırlatan bir tuzak."),
        EnemyDef(BAT, "Boşluk Yarasası", 14, 2, 6, 0, 0.06, 0.18, 150, 10, Behavior.TELEPORTER, 0xFF8A6FB5.toInt(), 4, 8, 0.85f,
            desc = "Işığın kenarında dolaşır; kanla beslenir."),
        EnemyDef(SKELETON, "Kemik Muhafız", 32, 4, 8, 3, 0.04, 0.0, 90, 18, Behavior.MELEE, 0xFFC9C2AC.toInt(), 5, 8, 1.05f,
            desc = "Eski bir yemine hâlâ sadık kalmış, paslı zırhlı bir iskelet."),
        EnemyDef(FLAME, "Ateş Ruhu", 22, 4, 9, 1, 0.06, 0.08, 105, 17, Behavior.RANGED, 0xFFE07045.toInt(), 5, 8, 1f,
            rangedRange = 5, desc = "Küllerin içinden doğan öfkeli bir alev."),
        EnemyDef(SHADE, "Gölge İkizi", 28, 5, 9, 2, 0.08, 0.20, 115, 22, Behavior.TELEPORTER, 0xFF7F6FA8.toInt(), 6, 8, 1f,
            desc = "Senin gibi hareket eder; ama gölgesi yoktur."),
        EnemyDef(BOSS_CRYSTAL, "Kristal Ana", 88, 5, 10, 4, 0.06, 0.05, 100, 60, Behavior.BOSS, 0xFFAC8BFF.toInt(), 4, 4, 1.55f,
            desc = "Kristal Kuyu'nun kalbinde büyüyen, yumurtalarından örümcekler doğan ana."),
        EnemyDef(BOSS_ASH, "Kül Devi", 155, 8, 14, 6, 0.06, 0.0, 85, 90, Behavior.BOSS, 0xFFD0804E.toInt(), 6, 6, 1.7f,
            desc = "Küllerden dövülmüş dev. Her adımında zemin sarsılır."),
        EnemyDef(BOSS_VOID, "Boşluk Bekçisi", 260, 10, 18, 8, 0.08, 0.10, 105, 200, Behavior.BOSS, 0xFFD55C7A.toInt(), 8, 8, 1.9f,
            desc = "Yedinci mühürün ardındaki son bekçi. Işığın bittiği yerde nefes alır.")
    ).associateBy { it.id }

    fun def(id: String): EnemyDef = DEFS[id] ?: DEFS[RAT]!!
    fun forDepth(depth: Int): List<String> = DEFS.values.filter { depth in it.minDepth..it.maxDepth && it.behavior != Behavior.BOSS }.map { it.id }
    fun bossForDepth(depth: Int): String? = when (depth) {
        4 -> BOSS_CRYSTAL
        6 -> BOSS_ASH
        8 -> BOSS_VOID
        else -> null
    }
}

open class Actor(
    var x: Int,
    var y: Int,
    var name: String,
    var hp: Int,
    var maxHp: Int,
    var atkMin: Int,
    var atkMax: Int,
    var def: Int,
    var acc: Double = 0.0,
    var eva: Double = 0.0
) {
    var alive: Boolean = true
    var energy: Int = 0
    var enemyId: String? = null
    var elite: Boolean = false
    var boss: Boolean = false
    var aiState: AiState = AiState.SLEEP
    var homeX: Int = x
    var homeY: Int = y
    var lastSeenX: Int = -1
    var lastSeenY: Int = -1
    var alertTurns: Int = 0
    var slowSkip: Boolean = false
    var invisibleTurns: Int = 0
    var shield: Int = 0
    var buffs: MutableList<Buff> = mutableListOf()
    var animFromX: Float = x.toFloat()
    var animFromY: Float = y.toFloat()
    var animStart: Long = 0L
    var hurtUntil: Long = 0L
    var attackUntil: Long = 0L
    var deathAt: Long = 0L
    var facingX: Float = 0f
    var facingY: Float = 1f
    var specialCooldown: Int = 0
    var summonCooldown: Int = 0
    var generation: Int = 0

    val currentDef: Int get() = def + buffs.sumOf { if (it.type == BuffType.SHIELD) it.power else 0 }

    fun hasBuff(t: BuffType): Boolean = buffs.any { it.type == t && it.turns > 0 }

    fun addBuff(b: Buff) {
        val existing = buffs.firstOrNull { it.type == b.type }
        if (existing != null) {
            existing.turns = max(existing.turns, b.turns)
            existing.power = max(existing.power, b.power)
        } else buffs.add(b)
    }

    fun removeBuff(t: BuffType) {
        buffs.removeAll { it.type == t }
    }

    fun heal(amount: Int) {
        hp = min(maxHp, hp + amount)
    }

    fun hurt(amount: Int): Int {
        val real = max(0, amount)
        hp -= real
        if (hp <= 0) {
            hp = 0
            alive = false
        }
        return real
    }

    fun distanceTo(other: Actor): Int = max(kotlin.math.abs(x - other.x), kotlin.math.abs(y - other.y))
    fun distanceTo(px: Int, py: Int): Int = max(kotlin.math.abs(x - px), kotlin.math.abs(y - py))
}

class Player(
    var cls: HeroClass,
    x: Int,
    y: Int
) : Actor(
    x, y, cls.title,
    cls.hp, cls.hp,
    cls.atkMin, cls.atkMax,
    cls.def, cls.acc, cls.eva
) {
    var level: Int = 1
    var xp: Int = 0
    var xpNext: Int = 12
    var gold: Int = 0
    var oil: Int = 100
    var maxEnergy: Int = cls.energy
    var energyNow: Int = cls.energy
    var kills: Int = 0
    var turns: Int = 0
    var weapon: Item? = null
    var armor: Item? = null
    var relic: Item? = null
    var inventory: MutableList<Item> = mutableListOf()
    var identified: MutableSet<String> = mutableSetOf()
    var strengthUses: Int = 0
    var steps: Int = 0

    fun weaponMin(): Int = atkMin + (weapon?.let { it.def.atkMin + it.upgrade } ?: 0)
    fun weaponMax(): Int = atkMax + (weapon?.let { it.def.atkMax + it.upgrade } ?: 0)
    fun armorDef(): Int = def + (armor?.let { it.def.def + it.upgrade } ?: 0)
    fun totalAcc(): Double = acc + (weapon?.def?.acc ?: 0.0) + (relic?.def?.acc ?: 0.0)
    fun totalEva(): Double = eva + (armor?.def?.eva ?: 0.0) + (relic?.def?.eva ?: 0.0)
    fun totalCrit(): Double = 0.08 + (weapon?.def?.crit ?: 0.0)
    fun totalFire(): Int = (weapon?.def?.fire ?: 0) + (relic?.def?.fire ?: 0)
    fun totalLifesteal(): Double = (weapon?.def?.lifesteal ?: 0.0) + (relic?.def?.lifesteal ?: 0.0)
    fun maxEnergyWithGear(): Int = maxEnergy + (armor?.def?.energyBonus ?: 0) + (relic?.def?.energyBonus ?: 0)

    fun addXp(amount: Int): Boolean {
        xp += amount
        var leveled = false
        while (xp >= xpNext) {
            xp -= xpNext
            level++
            xpNext = 10 + level * 9
            maxHp += 4
            hp = min(maxHp, hp + maxHp / 3)
            atkMin += 1
            if (level % 2 == 0) atkMax += 1
            if (level % 3 == 0) def += 1
            if (level % 2 == 0) maxEnergy++
            leveled = true
        }
        return leveled
    }

    fun addItem(item: Item): Boolean {
        if (item.def.stackable) {
            val existing = inventory.firstOrNull { it.id == item.id }
            if (existing != null) {
                existing.count += item.count
                return true
            }
        }
        if (inventory.size >= 24) return false
        inventory.add(item)
        return true
    }

    fun removeItem(item: Item, count: Int = 1) {
        item.count -= count
        if (item.count <= 0) inventory.remove(item)
    }
}

class GroundItem(var x: Int, var y: Int, var item: Item) {
    var spawnedAt: Long = System.currentTimeMillis()
}

// ---------------------------------------------------------------------------
// Game messages and effects
// ---------------------------------------------------------------------------

class GameMessage(val text: String, val color: Int = Palette.TEXT, val at: Long = System.currentTimeMillis())
