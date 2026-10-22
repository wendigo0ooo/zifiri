package com.wendigo.zifiri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DungeonTest {

    private val blockers = setOf(
        TileType.WALL.ordinal,
        TileType.PILLAR.ordinal,
        TileType.RUBBLE.ordinal,
        TileType.LAVA.ordinal,
        TileType.VOID.ordinal
    )

    private fun reachable(level: Level, sx: Int, sy: Int, tx: Int, ty: Int): Boolean {
        val seen = BooleanArray(MAP_W * MAP_H)
        val queue = ArrayDeque<Int>()
        val start = level.idx(sx, sy)
        seen[start] = true
        queue.addLast(start)
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val x = i % MAP_W
            val y = i / MAP_W
            for (d in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val nx = x + d.first
                val ny = y + d.second
                if (nx !in 1 until MAP_W - 1 || ny !in 1 until MAP_H - 1) continue
                val ni = level.idx(nx, ny)
                if (seen[ni]) continue
                if (level.tile(nx, ny).type in blockers) continue
                seen[ni] = true
                queue.addLast(ni)
            }
        }
        return seen[level.idx(tx, ty)]
    }

    @Test
    fun floorsHaveReachableStairsAndBoundedFeatures() {
        var altarFloors = 0
        for (seed in 1L..20L) {
            val level = DungeonGenerator(Rng(seed)).generate(1)
            val stairs = level.tiles.count { it.type == TileType.STAIRS.ordinal }
            assertEquals("seed=$seed", 1, stairs)
            assertTrue("start walkable seed=$seed", level.tile(level.startX, level.startY).walkable())
            assertTrue("stairs walkable seed=$seed", level.tile(level.stairsX, level.stairsY).walkable())
            assertTrue("stairs unreachable seed=$seed", reachable(level, level.startX, level.startY, level.stairsX, level.stairsY))

            val chests = level.tiles.count {
                it.feature == Feature.CHEST.ordinal || it.feature == Feature.LOCKED_CHEST.ordinal
            }
            val altars = level.tiles.count { it.feature == Feature.ALTAR.ordinal }
            val traps = level.tiles.count {
                it.feature == Feature.TRAP_SPIKE.ordinal || it.feature == Feature.TRAP_POISON.ordinal ||
                    it.feature == Feature.TRAP_ALARM.ordinal || it.feature == Feature.TRAP_TELEPORT.ordinal
            }
            assertTrue("no chest seed=$seed", chests >= 1)
            assertTrue("altar over spawn seed=$seed altars=$altars", altars <= 1)
            assertTrue("trap count seed=$seed traps=$traps", traps <= Balance.trapCount(1))
            if (altars == 1) altarFloors++
        }
        assertTrue("altar guaranteed on first floor: $altarFloors/20", altarFloors >= 18)
    }

    @Test
    fun deeperFloorsKeepStairsConsistent() {
        for (seed in 1L..10L) {
            val level = DungeonGenerator(Rng(seed * 31)).generate(3)
            assertEquals(1, level.tiles.count { it.type == TileType.STAIRS.ordinal })
            assertTrue(reachable(level, level.startX, level.startY, level.stairsX, level.stairsY))
            val traps = level.tiles.count {
                it.feature == Feature.TRAP_SPIKE.ordinal || it.feature == Feature.TRAP_POISON.ordinal ||
                    it.feature == Feature.TRAP_ALARM.ordinal || it.feature == Feature.TRAP_TELEPORT.ordinal
            }
            assertTrue("depth3 trap count seed=$seed traps=$traps", traps <= Balance.trapCount(3))
        }
    }
}
