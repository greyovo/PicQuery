package me.grey.picquery.domain

import android.util.ArrayMap

class UnionFind(private val n: Int) {
    private val parent: IntArray = IntArray(n) { it }
    private val size: IntArray = IntArray(n) { 1 }

    fun union(x: Int, y: Int) {
        val rootX = find(x)
        val rootY = find(y)
        if (rootX == rootY) return

        if (size[rootX] < size[rootY]) {
            parent[rootX] = rootY
            size[rootY] += size[rootX]
        } else {
            parent[rootY] = rootX
            size[rootX] += size[rootY]
        }
    }

    private fun find(x: Int): Int {
        if (parent[x] != x) {
            parent[x] = find(parent[x])
        }
        return parent[x]
    }

    fun getGroups(): List<List<Int>> {
        val groups = ArrayMap<Int, MutableList<Int>>()
        for (i in 0 until n) {
            val root = find(i)
            groups.getOrPut(root) { mutableListOf() }.add(i)
        }
        return groups.values.filter { it.size > 1 }
    }
}