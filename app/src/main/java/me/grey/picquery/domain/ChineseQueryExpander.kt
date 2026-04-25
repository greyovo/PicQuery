package me.grey.picquery.domain

internal object ChineseQueryExpander {
    private val aliases = linkedMapOf(
        "米粉" to listOf("rice noodles", "rice noodle soup", "vermicelli", "rice vermicelli"),
        "粉" to listOf("rice noodles", "noodles", "vermicelli"),
        "牛肉粉" to listOf("beef rice noodles", "beef noodle soup"),
        "螺蛳粉" to listOf("luosifen", "snail rice noodles", "spicy rice noodles"),
        "河粉" to listOf("flat rice noodles", "rice noodles"),
        "炒粉" to listOf("fried rice noodles", "stir fried noodles"),
        "面条" to listOf("noodles", "noodle soup"),
        "拉面" to listOf("ramen", "noodle soup"),
        "米饭" to listOf("rice", "cooked rice"),
        "饭" to listOf("rice", "meal"),
        "饺子" to listOf("dumplings", "jiaozi"),
        "包子" to listOf("steamed buns", "baozi"),
        "小笼包" to listOf("xiaolongbao", "soup dumplings"),
        "馄饨" to listOf("wonton soup", "wontons"),
        "火锅" to listOf("hot pot", "hotpot"),
        "烧烤" to listOf("barbecue", "grill", "skewers"),
        "串串" to listOf("skewers", "hot pot skewers"),
        "奶茶" to listOf("milk tea", "bubble tea"),
        "咖啡" to listOf("coffee"),
        "蛋糕" to listOf("cake"),
        "甜品" to listOf("dessert", "sweets"),
        "猫" to listOf("cat"),
        "狗" to listOf("dog"),
        "花" to listOf("flower", "flowers"),
        "夕阳" to listOf("sunset"),
        "日落" to listOf("sunset"),
        "天空" to listOf("sky"),
        "海" to listOf("sea", "ocean"),
        "雪" to listOf("snow"),
        "车" to listOf("car"),
        "火车" to listOf("train"),
        "飞机" to listOf("airplane", "plane"),
        "证件" to listOf("document", "ID card", "certificate"),
        "截图" to listOf("screenshot"),
        "二维码" to listOf("QR code"),
        "发票" to listOf("invoice", "receipt")
    )

    fun expand(text: String): List<String> {
        val normalized = text.trim()
        if (normalized.isEmpty()) return emptyList()

        val candidates = linkedSetOf<String>()
        aliases[normalized]?.let { candidates.addAll(it) }

        aliases.forEach { (keyword, values) ->
            if (keyword != normalized && normalized.contains(keyword)) {
                candidates.addAll(values)
            }
        }

        return candidates.toList()
    }

    fun mergeCandidates(originalText: String, translatedText: String): List<String> {
        val candidates = linkedSetOf<String>()
        candidates.addAll(expand(originalText))
        translatedText.trim().takeIf { it.isNotEmpty() }?.let { candidates.add(it) }
        originalText.trim().takeIf { it.isNotEmpty() }?.let { candidates.add(it) }
        return candidates.toList()
    }
}
