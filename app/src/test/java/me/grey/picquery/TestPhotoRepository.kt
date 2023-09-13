package me.grey.picquery

import android.util.Log
import org.junit.Test

fun main() {
    println(calculateRemainingTime(120, 3087, 35))
}

fun calculateRemainingTime(
    current: Int, total: Int, costPerItem: Long
): Long {
    if (costPerItem.toInt() == 0) return 0L
    val remainItem = (total - current)
    val res = (remainItem * (costPerItem) / 1000)
    return res
}