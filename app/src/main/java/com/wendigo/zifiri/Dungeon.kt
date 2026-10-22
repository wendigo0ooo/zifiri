package com.wendigo.zifiri

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class Room(val x: Int, val y: Int, val w: Int, val h: Int) {
    val cx: Int get() = x + w / 2
    val cy: Int get() = y + h / 2
    fun contains(px: Int, py: Int): Boolean = px in x until x + w && py in y until y + h
    fun intersects(o: Room): Boolean = !(x + w + 1 <= o.x || o.x + o.w + 1 <= x || y + h + 1 <= o.y || o.y + o.h + 1 <= y)
}

class DungeonGenerator(private val rng: Rng) {

    fun generate(depth: Int): Level {
        val level = Level(depth, rng = rng)
        if (depth == 4 || depth == 6 || depth == 8) generateBoss(level) else generateRegular(level)
        level.rng.seed = rng.seed
        return level
    }

    private fun clear(level: Level) {
        for (t in level.tiles) {
            t.type = TileType.WALL.ordinal
            t.feature = Feature.NONE.ordinal
            t.featureData = 0
            t.explored = false
            t.visible = false
            t.doorOpen = false
            t.variant = rng.nextInt(8)
        }
    }

    private fun carve(level: Level, x: Int, y: Int, type: TileType = TileType.FLOOR) {
        if (x in 1 until MAP_W - 1 && y in 1 until MAP_H - 1) {
            level.tile(x, y).type = type.ordinal
            level.tile(x, y).variant = rng.nextInt(8)
        }
    }

    private fun generateRegular(level: Level) {
        clear(level)
        val rooms = ArrayList<Room>()
        val attempts = 70
        for (i in 0 until attempts) {
            if (rooms.size >= 16) break
            val w = rng.nextInt(4, 8)
            val h = rng.nextInt(4, 8)
            val x = rng.nextInt(2, MAP_W - w - 3)
            val y = rng.nextInt(2, MAP_H - h - 3)
            val room = Room(x, y, w, h)
            if (rooms.any { it.intersects(room) }) continue
            rooms.add(room)
            for (yy in y until y + h) for (xx in x until x + w) carve(level, xx, yy)
        }
        if (rooms.size < 3) {
            // Deterministic fallback: a simple grid of rooms.
            rooms.clear()
            for (gy in 0..2) for (gx in 0..2) {
                val room = Room(3 + gx * 13, 3 + gy * 13, 8, 8)
                rooms.add(room)
                for (yy in room.y until room.y + room.h) for (xx in room.x until room.x + room.w) carve(level, xx, yy)
            }
        }

        for (i in 1 until rooms.size) connect(level, rooms[i - 1], rooms[i])
        repeat(3) {
            val a = rng.pick(rooms)
            val b = rng.pick(rooms)
            if (a !== b) connect(level, a, b)
        }

        val startRoom = rooms[0]
        level.startX = startRoom.cx
        level.startY = startRoom.cy
        carve(level, level.startX, level.startY)

        var exitRoom = rooms[1]
        var best = -1.0
        for (r in rooms.drop(1)) {
            val d = distance(level.startX, level.startY, r.cx, r.cy)
            if (d > best) {
                best = d
                exitRoom = r
            }
        }
        level.stairsX = exitRoom.cx
        level.stairsY = exitRoom.cy
        level.tile(level.stairsX, level.stairsY).type = TileType.STAIRS.ordinal
        level.tile(level.stairsX, level.stairsY).feature = Feature.NONE.ordinal

        // Water and danger patches.
        for (r in rooms) {
            if (rng.chance(0.25)) {
                val type = when (Biome.forDepth(level.depth)) {
                    Biome.SARNIC -> TileType.WATER
                    Biome.KRISTAL -> if (rng.chance(0.4)) TileType.WATER else TileType.LAVA
                    Biome.KUL -> TileType.LAVA
                    Biome.MUHUR -> if (rng.chance(0.5)) TileType.WATER else TileType.LAVA
                    else -> TileType.LAVA
                }
                val px = rng.nextInt(max(r.x, 1), r.x + r.w - 2)
                val py = rng.nextInt(max(r.y, 1), r.y + r.h - 2)
                for (dy in -1..1) for (dx in -1..1) {
                    if (rng.chance(0.65)) {
                        val tx = px + dx
                        val ty = py + dy
                        if (inRoom(tx, ty, r) && !(tx == level.startX && ty == level.startY) && !(tx == level.stairsX && ty == level.stairsY)) {
                            level.tile(tx, ty).type = type.ordinal
                        }
                    }
                }
            }
        }

        // Architectural details: pillars and rubble.
        for (r in rooms) {
            if (r.w >= 6 && r.h >= 6 && rng.chance(0.4)) {
                val px = r.x + r.w / 2
                val py = r.y + r.h / 2
                for (d in listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val tx = px + d.first
                    val ty = py + d.second
                    if (tx in r.x until r.x + r.w && ty in r.y until r.y + r.h && !isSpecial(level, tx, ty)) {
                        level.tile(tx, ty).type = TileType.PILLAR.ordinal
                    }
                }
            }
            repeat(rng.nextInt(0, 3)) {
                val tx = rng.nextInt(r.x, r.x + r.w - 1)
                val ty = rng.nextInt(r.y, r.y + r.h - 1)
                if (!isSpecial(level, tx, ty) && level.tile(tx, ty).type == TileType.FLOOR.ordinal) {
                    level.tile(tx, ty).type = TileType.RUBBLE.ordinal
                }
            }
        }

        // Doors in narrow corridor spots.
        for (y in 2 until MAP_H - 2) for (x in 2 until MAP_W - 2) {
            val t = level.tile(x, y)
            if (t.type != TileType.FLOOR.ordinal) continue
            if (t.feature != Feature.NONE.ordinal) continue
            val horiz = level.tile(x - 1, y).isWall() && level.tile(x + 1, y).isWall() && !level.tile(x, y - 1).isWall() && !level.tile(x, y + 1).isWall()
            val vert = level.tile(x, y - 1).isWall() && level.tile(x, y + 1).isWall() && !level.tile(x - 1, y).isWall() && !level.tile(x + 1, y).isWall()
            if ((horiz || vert) && rng.chance(0.32)) {
                t.type = TileType.DOOR.ordinal
                t.doorOpen = rng.chance(0.35)
            }
        }

        // Wall torches.
        for (r in rooms) {
            val spots = ArrayList<Pair<Int, Int>>()
            for (x in r.x - 1..r.x + r.w) {
                spots.add(x to r.y - 1)
                spots.add(x to r.y + r.h)
            }
            for (y in r.y - 1..r.y + r.h) {
                spots.add(r.x - 1 to y)
                spots.add(r.x + r.w to y)
            }
            rng.shuffle(spots)
            repeat(min(2, spots.size)) {
                val s = spots[it]
                val ti = level.tileOrNull(s.first, s.second) ?: return@repeat
                if (ti.isWall()) {
                    ti.type = TileType.WALL_TORCH.ordinal
                    ti.variant = if (rng.chance(0.5)) 0 else 1
                }
            }
        }

        // Decorative features.
        for (r in rooms) {
            repeat(rng.nextInt(1, 4)) {
                val tx = rng.nextInt(r.x, r.x + r.w - 1)
                val ty = rng.nextInt(r.y, r.y + r.h - 1)
                if (isSpecial(level, tx, ty)) return@repeat
                val t = level.tile(tx, ty)
                if (t.type != TileType.FLOOR.ordinal || t.feature != Feature.NONE.ordinal) return@repeat
                t.feature = when (Biome.forDepth(level.depth)) {
                    Biome.SARNIC -> rng.pick(listOf(Feature.MUSHROOM, Feature.WEED, Feature.BONES, Feature.NONE))
                    Biome.KRISTAL -> rng.pick(listOf(Feature.CRYSTAL, Feature.CRYSTAL, Feature.MUSHROOM, Feature.NONE))
                    Biome.KUL -> rng.pick(listOf(Feature.BONES, Feature.WEED, Feature.NONE))
                    Biome.MUHUR -> rng.pick(listOf(Feature.CRYSTAL, Feature.BONES, Feature.NONE))
                    else -> rng.pick(listOf(Feature.BONES, Feature.NONE))
                }.ordinal
            }
        }

        // Chests, altars and traps.
        val safeRooms = rooms.drop(1)
        repeat(Balance.chestCount(level.depth, rooms.size)) {
            val room = rng.pick(safeRooms)
            val spot = findFreeSpot(level, room)
            if (spot != null) {
                val t = level.tile(spot.first, spot.second)
                t.feature = if (rng.chance(0.35)) Feature.LOCKED_CHEST.ordinal else Feature.CHEST.ordinal
            }
        }
        if (rng.chance(Balance.altarChance(level.depth))) {
            val room = rng.pick(safeRooms)
            val spot = findFreeSpot(level, room)
            if (spot != null) level.tile(spot.first, spot.second).feature = Feature.ALTAR.ordinal
        }
        if ((level.depth == 3 || level.depth == 6) && rng.chance(0.9)) {
            val room = rng.pick(safeRooms)
            val spot = findFreeSpot(level, room)
            if (spot != null) level.tile(spot.first, spot.second).feature = Feature.SHOP.ordinal
        }
        val trapCount = Balance.trapCount(level.depth)
        repeat(trapCount) {
            val room = rng.pick(safeRooms)
            val tx = rng.nextInt(room.x, room.x + room.w - 1)
            val ty = rng.nextInt(room.y, room.y + room.h - 1)
            if (isSpecial(level, tx, ty)) return@repeat
            val t = level.tile(tx, ty)
            if (t.type == TileType.FLOOR.ordinal && t.feature == Feature.NONE.ordinal) {
                t.feature = rng.pick(listOf(
                    Feature.TRAP_SPIKE, Feature.TRAP_POISON, Feature.TRAP_ALARM, Feature.TRAP_TELEPORT
                )).ordinal
                t.featureData = 0
            }
        }

        // Decoration can seal a room mouth; guarantee a path after everything is placed.
        ensureConnected(level)
        ensureOneChest(level, safeRooms)
    }

    private fun generateBoss(level: Level) {
        clear(level)
        val cx = MAP_W / 2
        val cy = MAP_H / 2
        val rx = if (level.depth == 8) 15 else 12
        val ry = if (level.depth == 8) 13 else 10
        for (y in 0 until MAP_H) for (x in 0 until MAP_W) {
            val dx = (x - cx).toDouble() / rx
            val dy = (y - cy).toDouble() / ry
            val d = dx * dx + dy * dy
            if (d <= 1.0) {
                level.tile(x, y).type = TileType.FLOOR.ordinal
                level.tile(x, y).variant = rng.nextInt(8)
            }
        }
        val voidDepth = level.depth == 8
        // Outer ring decoration.
        for (y in 1 until MAP_H - 1) for (x in 1 until MAP_W - 1) {
            if (level.tile(x, y).type == TileType.WALL.ordinal) continue
            val dx = (x - cx).toDouble() / rx
            val dy = (y - cy).toDouble() / ry
            if (dx * dx + dy * dy > 0.82) {
                if (voidDepth) level.tile(x, y).type = TileType.LAVA.ordinal
                else if (rng.chance(0.25)) level.tile(x, y).type = TileType.WATER.ordinal
            }
        }
        val pillarCount = if (level.depth == 8) 10 else 8
        for (i in 0 until pillarCount) {
            val a = Math.PI * 2.0 * i / pillarCount
            val px = (cx + Math.cos(a) * (rx - 5)).toInt()
            val py = (cy + Math.sin(a) * (ry - 4)).toInt()
            if (px in 2 until MAP_W - 2 && py in 2 until MAP_H - 2) {
                level.tile(px, py).type = TileType.PILLAR.ordinal
                level.tile(px + 1, py).type = TileType.PILLAR.ordinal
            }
        }
        level.startX = cx
        level.startY = cy + ry - 2
        level.bossX = cx
        level.bossY = cy - 1
        level.tile(level.startX, level.startY).type = TileType.FLOOR.ordinal
        level.tile(level.bossX, level.bossY).type = TileType.FLOOR.ordinal
        level.tile(level.bossX, level.bossY).feature = Feature.NONE.ordinal
        level.stairsX = cx
        level.stairsY = cy - (ry - 2)
        level.tile(level.stairsX, level.stairsY).type = TileType.STAIRS.ordinal
        level.tile(level.stairsX, level.stairsY).feature = Feature.NONE.ordinal
        if (voidDepth) {
            // The final seal stands at the very heart.
            level.tile(level.bossX, level.bossY).feature = Feature.CRYSTAL.ordinal
        }
        // Torches around the arena.
        for (y in 1 until MAP_H - 1) for (x in 1 until MAP_W - 1) {
            val t = level.tile(x, y)
            if (!t.isWall()) continue
            val nearFloor = level.tileOrNull(x - 1, y)?.let { !it.isWall() && it.type != TileType.LAVA.ordinal } == true ||
                level.tileOrNull(x + 1, y)?.let { !it.isWall() && it.type != TileType.LAVA.ordinal } == true ||
                level.tileOrNull(x, y - 1)?.let { !it.isWall() && it.type != TileType.LAVA.ordinal } == true ||
                level.tileOrNull(x, y + 1)?.let { !it.isWall() && it.type != TileType.LAVA.ordinal } == true
            if (nearFloor && rng.chance(0.10)) t.type = TileType.WALL_TORCH.ordinal
        }
        ensureConnected(level)
    }

    private fun isSpecial(level: Level, x: Int, y: Int): Boolean {
        if (x == level.startX && y == level.startY) return true
        if (x == level.stairsX && y == level.stairsY) return true
        val t = level.tile(x, y)
        return t.type != TileType.FLOOR.ordinal || t.feature != Feature.NONE.ordinal
    }

    private fun findFreeSpot(level: Level, room: Room): Pair<Int, Int>? {
        repeat(30) {
            val tx = rng.nextInt(room.x, room.x + room.w - 1)
            val ty = rng.nextInt(room.y, room.y + room.h - 1)
            if (!isSpecial(level, tx, ty)) return tx to ty
        }
        return null
    }

    private fun inRoom(x: Int, y: Int, r: Room): Boolean = x in r.x until r.x + r.w && y in r.y until r.y + r.h

    private fun connect(level: Level, a: Room, b: Room) {
        var x = a.cx
        var y = a.cy
        val horizFirst = rng.chance(0.5)
        if (horizFirst) {
            while (x != b.cx) {
                x += if (x < b.cx) 1 else -1
                carve(level, x, y)
            }
            while (y != b.cy) {
                y += if (y < b.cy) 1 else -1
                carve(level, x, y)
            }
        } else {
            while (y != b.cy) {
                y += if (y < b.cy) 1 else -1
                carve(level, x, y)
            }
            while (x != b.cx) {
                x += if (x < b.cx) 1 else -1
                carve(level, x, y)
            }
        }
    }

    private fun blocksPath(t: Tile): Boolean = when (t.type) {
        TileType.WALL.ordinal, TileType.WALL_TORCH.ordinal, TileType.PILLAR.ordinal,
        TileType.RUBBLE.ordinal, TileType.LAVA.ordinal, TileType.VOID.ordinal -> true
        else -> t.feature == Feature.CHEST.ordinal || t.feature == Feature.LOCKED_CHEST.ordinal ||
            t.feature == Feature.SHOP.ordinal
    }

    private fun clearForPath(t: Tile) {
        t.type = TileType.FLOOR.ordinal
        t.feature = Feature.NONE.ordinal
        t.featureData = 0
    }

    private fun pathExists(level: Level): Boolean {
        val seen = BooleanArray(MAP_W * MAP_H)
        val queue = ArrayDeque<Int>()
        queue.add(level.idx(level.startX, level.startY))
        seen[level.idx(level.startX, level.startY)] = true
        val dirs = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val x = i % MAP_W
            val y = i / MAP_W
            for (d in dirs) {
                val nx = x + d.first
                val ny = y + d.second
                if (nx !in 1 until MAP_W - 1 || ny !in 1 until MAP_H - 1) continue
                if (seen[level.idx(nx, ny)]) continue
                if (blocksPath(level.tile(nx, ny))) continue
                seen[level.idx(nx, ny)] = true
                queue.add(level.idx(nx, ny))
            }
        }
        return seen[level.idx(level.stairsX, level.stairsY)]
    }

    private fun ensureConnected(level: Level) {
        if (pathExists(level)) return
        // Decoration sealed the stairs; carve a guaranteed path.
        var x = level.startX
        var y = level.startY
        while (x != level.stairsX) {
            x += if (x < level.stairsX) 1 else -1
            val t = level.tile(x, y)
            if (blocksPath(t)) clearForPath(t)
        }
        while (y != level.stairsY) {
            y += if (y < level.stairsY) 1 else -1
            val t = level.tile(x, y)
            if (blocksPath(t)) clearForPath(t)
        }
        level.tile(level.stairsX, level.stairsY).type = TileType.STAIRS.ordinal
        level.tile(level.stairsX, level.stairsY).feature = Feature.NONE.ordinal
    }

    private fun ensureOneChest(level: Level, rooms: List<Room>) {
        val hasChest = level.tiles.any {
            it.feature == Feature.CHEST.ordinal || it.feature == Feature.LOCKED_CHEST.ordinal
        }
        if (hasChest) return
        repeat(40) {
            val room = rng.pick(rooms)
            val spot = findFreeSpot(level, room) ?: return@repeat
            val t = level.tile(spot.first, spot.second)
            t.feature = Feature.CHEST.ordinal
            if (pathExists(level)) return
            t.feature = Feature.NONE.ordinal
        }
    }

    private fun distance(x1: Int, y1: Int, x2: Int, y2: Int): Double {
        val dx = (x1 - x2).toDouble()
        val dy = (y1 - y2).toDouble()
        return sqrt(dx * dx + dy * dy)
    }
}

object Fov {
    private val mult = arrayOf(
        intArrayOf(1, 0, 0, -1, -1, 0, 0, 1),
        intArrayOf(0, 1, -1, 0, 0, -1, 1, 0),
        intArrayOf(0, 1, 1, 0, 0, -1, -1, 0),
        intArrayOf(1, 0, 0, 1, -1, 0, 0, -1)
    )

    fun compute(level: Level, px: Int, py: Int, radius: Int) {
        for (t in level.tiles) t.visible = false
        if (level.inBounds(px, py)) {
            val t = level.tile(px, py)
            t.visible = true
            t.explored = true
        }
        for (oct in 0 until 8) {
            cast(level, px, py, 1, 1.0, 0.0, radius,
                mult[0][oct], mult[1][oct], mult[2][oct], mult[3][oct])
        }
    }

    private fun cast(
        level: Level, cx: Int, cy: Int, row: Int, start: Double, end: Double,
        radius: Int, xx: Int, xy: Int, yx: Int, yy: Int
    ) {
        if (start < end) return
        var newStart = start
        var blocked = false
        var distance = row
        while (distance <= radius && !blocked) {
            val dy = -distance
            var dx = -distance
            while (dx <= 0) {
                val x = cx + dx * xx + dy * xy
                val y = cy + dx * yx + dy * yy
                val lSlope = (dx - 0.5) / (dy + 0.5)
                val rSlope = (dx + 0.5) / (dy - 0.5)
                if (start < rSlope) {
                    dx++
                    continue
                } else if (end > lSlope) {
                    break
                }
                if (x in 0 until MAP_W && y in 0 until MAP_H) {
                    val tdx = x - cx
                    val tdy = y - cy
                    if (tdx * tdx + tdy * tdy <= radius * radius + radius) {
                        val t = level.tile(x, y)
                        t.visible = true
                        t.explored = true
                        if (t.blocksSight()) {
                            if (!blocked) {
                                cast(level, cx, cy, distance + 1, newStart, lSlope, radius, xx, xy, yx, yy)
                            }
                            blocked = true
                            newStart = rSlope
                        }
                    }
                }
                dx++
            }
            distance++
        }
    }
}

object Pathfinder {
    private data class Node(val x: Int, val y: Int, val g: Int, val f: Int)

    private val DIRS = arrayOf(
        intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1),
        intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1)
    )

    fun findPath(
        level: Level, sx: Int, sy: Int, tx: Int, ty: Int,
        blocked: (Int, Int) -> Boolean,
        allowDiagonal: Boolean = true
    ): List<Pair<Int, Int>> {
        if (sx == tx && sy == ty) return emptyList()
        val size = MAP_W * MAP_H
        val gScore = IntArray(size) { Int.MAX_VALUE }
        val prev = IntArray(size) { -1 }
        val closed = BooleanArray(size)
        val startIdx = level.idx(sx, sy)
        gScore[startIdx] = 0
        val open = PriorityQueue<Node>(compareBy { it.f })
        open.add(Node(sx, sy, 0, heuristic(sx, sy, tx, ty)))
        var found = false
        var iterations = 0
        while (open.isNotEmpty() && iterations < 5000) {
            iterations++
            val cur = open.poll()
            val ci = level.idx(cur.x, cur.y)
            if (closed[ci]) continue
            closed[ci] = true
            if (cur.x == tx && cur.y == ty) {
                found = true
                break
            }
            for (d in DIRS) {
                if (!allowDiagonal && (d[0] != 0 && d[1] != 0)) continue
                val nx = cur.x + d[0]
                val ny = cur.y + d[1]
                if (nx !in 0 until MAP_W || ny !in 0 until MAP_H) continue
                if (!level.inBounds(nx, ny)) continue
                if (blocked(nx, ny)) continue
                if (d[0] != 0 && d[1] != 0) {
                    if (blocked(cur.x + d[0], cur.y) || blocked(cur.x, cur.y + d[1])) continue
                }
                val ni = level.idx(nx, ny)
                if (closed[ni]) continue
                val cost = if (d[0] != 0 && d[1] != 0) 14 else 10
                val ng = cur.g + cost
                if (ng < gScore[ni]) {
                    gScore[ni] = ng
                    prev[ni] = ci
                    open.add(Node(nx, ny, ng, ng + heuristic(nx, ny, tx, ty)))
                }
            }
        }
        if (!found) return emptyList()
        val cards = ArrayList<Pair<Int, Int>>()
        var cur = level.idx(tx, ty)
        var guard = 0
        while (cur != startIdx && guard++ < size) {
            cards.add((cur % MAP_W) to (cur / MAP_W))
            cur = prev[cur]
            if (cur < 0) break
        }
        cards.reverse()
        return cards
    }

    private fun heuristic(x1: Int, y1: Int, x2: Int, y2: Int): Int {
        val dx = abs(x1 - x2)
        val dy = abs(y1 - y2)
        return 10 * (dx + dy) + (14 - 20) * min(dx, dy)
    }

    fun canSee(level: Level, x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val dy = abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy
        var guard = 0
        while (guard++ < 200) {
            if (x == x1 && y == y1) return true
            if (!(x == x0 && y == y0) && level.tileOrNull(x, y)?.blocksSight() == true) return false
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                x += sx
            }
            if (e2 < dx) {
                err += dx
                y += sy
            }
        }
        return false
    }

    fun line(x0: Int, y0: Int, x1: Int, y1: Int): List<Pair<Int, Int>> {
        val result = ArrayList<Pair<Int, Int>>()
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val dy = abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy
        var guard = 0
        while (guard++ < 200) {
            result.add(x to y)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                x += sx
            }
            if (e2 < dx) {
                err += dx
                y += sy
            }
        }
        return result
    }
}
