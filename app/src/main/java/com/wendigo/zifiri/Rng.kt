package com.wendigo.zifiri

import java.util.Random

class Rng(var seed: Long = System.nanoTime()) {
    val random = Random(seed)

    fun nextInt(bound: Int): Int = random.nextInt(bound.coerceAtLeast(1))
    fun nextInt(from: Int, toInclusive: Int): Int {
        if (toInclusive <= from) return from
        return from + random.nextInt(toInclusive - from + 1)
    }

    fun nextFloat(): Float = random.nextFloat()
    fun nextDouble(): Double = random.nextDouble()
    fun chance(p: Double): Boolean = random.nextDouble() < p
    fun chance(p: Float): Boolean = random.nextFloat() < p

    fun <T> pick(list: List<T>): T = list[random.nextInt(list.size)]
    fun <T> pickOrNull(list: List<T>): T? = if (list.isEmpty()) null else pick(list)
    fun <T> shuffle(list: MutableList<T>): MutableList<T> {
        for (i in list.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
        }
        return list
    }
}
