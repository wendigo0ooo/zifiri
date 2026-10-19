package com.wendigo.zifiri

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Sfx {
    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    var enabled: Boolean = true

    private val resMap: Map<String, Int> = mapOf(
        "step" to R.raw.snd_step,
        "splash" to R.raw.snd_splash,
        "open" to R.raw.snd_open,
        "chest" to R.raw.snd_chest,
        "stairs" to R.raw.snd_stairs,
        "wait" to R.raw.snd_wait,
        "hit" to R.raw.snd_hit,
        "crit" to R.raw.snd_crit,
        "miss" to R.raw.snd_miss,
        "hurt" to R.raw.snd_hurt,
        "death" to R.raw.snd_death,
        "arrow" to R.raw.snd_arrow,
        "fire" to R.raw.snd_fire,
        "slam" to R.raw.snd_slam,
        "pickup" to R.raw.snd_pickup,
        "gold" to R.raw.snd_gold,
        "potion" to R.raw.snd_potion,
        "scroll" to R.raw.snd_scroll,
        "equip" to R.raw.snd_equip,
        "oil" to R.raw.snd_oil,
        "eat" to R.raw.snd_eat,
        "empty" to R.raw.snd_empty,
        "upgrade" to R.raw.snd_upgrade,
        "skill" to R.raw.snd_skill,
        "blink" to R.raw.snd_blink,
        "void" to R.raw.snd_void,
        "trap" to R.raw.snd_trap,
        "alarm" to R.raw.snd_alarm,
        "altar" to R.raw.snd_altar,
        "shop" to R.raw.snd_shop,
        "levelup" to R.raw.snd_levelup,
        "boss" to R.raw.snd_boss,
        "boss_death" to R.raw.snd_boss_death,
        "seal" to R.raw.snd_seal,
        "victory" to R.raw.snd_victory,
        "gameover" to R.raw.snd_gameover
    )

    fun init(context: Context) {
        if (pool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(attrs).build()
        pool = p
        for ((key, res) in resMap) {
            ids[key] = p.load(context, res, 1)
        }
    }

    fun play(id: String, rate: Float = 1f, volume: Float = 0.85f) {
        if (!enabled) return
        val p = pool ?: return
        val s = ids[id] ?: ids["empty"] ?: return
        p.play(s, volume, volume, 1, 0, rate.coerceIn(0.7f, 1.4f))
    }

    fun release() {
        pool?.release()
        pool = null
        ids.clear()
    }

    fun vibrate(context: Context, ms: Long = 24L, amplitude: Int = 90) {
        val vib = if (Build.VERSION.SDK_INT >= 31) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vib.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 26) {
            vib.vibrate(VibrationEffect.createOneShot(ms, amplitude.coerceIn(1, 255)))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(ms)
        }
    }
}
