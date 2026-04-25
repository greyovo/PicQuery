package me.grey.picquery.feature.tf

object TFLiteTensorShape {
    fun outputElementCount(shape: IntArray): Int {
        require(shape.isNotEmpty()) { "TFLite output shape must not be empty." }
        require(shape.all { it > 0 }) {
            "TFLite output shape must contain only positive dimensions: ${shape.joinToString()}."
        }
        return shape.fold(1) { total, dimension -> total * dimension }
    }
}
