package me.grey.picquery.ui.albums

data class EncodingAlbumState(
    val total: Int = 0,
    val current: Int = 0,
    val cost: Long = 0, // Time cost for encoding each item
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncodingAlbumState

        if (total != other.total) return false
        if (current != other.current) return false

        return true
    }

    override fun hashCode(): Int {
        var result = total
        result = 31 * result + current
        return result
    }
}