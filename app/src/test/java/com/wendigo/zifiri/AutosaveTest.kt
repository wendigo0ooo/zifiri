package com.wendigo.zifiri

import org.junit.Assert.assertTrue
import org.junit.Test

class AutosaveTest {

    @Test
    fun turnAndDescentNotifyProgress() {
        val game = Game.newGame(HeroClass.SHADE, 7L)
        var saves = 0
        game.onProgress = { saves++ }

        game.endTurn()
        assertTrue("turn did not notify", saves >= 1)

        val before = saves
        game.player.x = game.level.stairsX
        game.player.y = game.level.stairsY
        assertTrue(game.tryDescend())
        assertTrue("descent did not notify", saves > before)
    }

    @Test
    fun firstFloorShowsOnboardingHint() {
        val game = Game.newGame(HeroClass.BLADE, 3L)
        assertTrue(game.hintText.isNotEmpty())
        assertTrue(game.hintUntil > System.currentTimeMillis())
    }
}
