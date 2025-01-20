package me.grey.picquery.common

/**
 * Calculate the remaining time according to the cost of each item
 *
 * @param current The index of current processing item
 * @param total The total num of all items
 * @param costPerItem In milliseconds
 * @return Seconds in Long that represent the remaining time
 */
fun calculateRemainingTime(
    current: Int, total: Int, costPerItem: Long
): Long {
    if (costPerItem.toInt() == 0) return 0L
    val remainItem = (total - current)
    return (remainItem * (costPerItem) / 1000)
}