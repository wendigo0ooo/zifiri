package com.wendigo.zifiri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopTest {

    @Test
    fun fullInventoryDoesNotBurnGold() {
        val game = Game.newGame(HeroClass.BLADE, 11L)
        game.player.inventory.clear()
        repeat(24) { game.player.addItem(Item(Items.RUSTY_BLADE)) }
        assertEquals(24, game.player.inventory.size)
        game.player.gold = 1000
        game.shopStock = mutableListOf(Item(Items.BREAD) to 10)

        val bought = game.buyFromShop(0)

        assertFalse(bought)
        assertEquals(1000, game.player.gold)
        assertEquals(1, game.shopStock.size)
    }

    @Test
    fun successfulPurchaseDeductsGoldAndMovesItem() {
        val game = Game.newGame(HeroClass.EMBER, 12L)
        game.player.inventory.clear()
        game.player.gold = 1000
        game.shopStock = mutableListOf(Item(Items.BREAD) to 10)

        val bought = game.buyFromShop(0)

        assertTrue(bought)
        assertEquals(990, game.player.gold)
        assertEquals(0, game.shopStock.size)
        assertTrue(game.player.inventory.any { it.id == Items.BREAD })
    }
}
