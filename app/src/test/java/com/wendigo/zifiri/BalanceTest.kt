package com.wendigo.zifiri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceTest {

    @Test
    fun firstFloorIsGentlerThanDeepFloors() {
        assertEquals(7, Balance.enemyCount(1))
        assertEquals(10, Balance.enemyCount(2))
        assertEquals(2, Balance.trapCount(1))
        assertEquals(9, Balance.trapCount(8))
    }

    @Test
    fun firstFloorGuaranteesLootAndAltar() {
        assertEquals(2, Balance.chestCount(1, 10))
        assertEquals(1, Balance.chestCount(3, 4))
        assertEquals(1.0, Balance.altarChance(1), 0.0)
        assertTrue(Balance.altarChance(5) < 1.0)
    }

    @Test
    fun firstFloorTrapCannotOneShotStartingHeroes() {
        val rng = Rng(42L)
        repeat(50) {
            val dmg = Balance.spikeDamage(rng, 1)
            assertTrue("damage=$dmg", dmg in 3..5)
        }
    }
}
