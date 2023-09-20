package me.grey.picquery.ui.albums



data class IndexingAlbumState(
    val status: Status = Status.None,
    val total: Int = 0,
    val current: Int = 0,
    val cost: Long = 0, // Time cost for encoding each item
) {
    enum class Status {
        None, Loading, Indexing, Finish, Error
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IndexingAlbumState

        if (status != other.status) return false
        if (total != other.total) return false
        if (current != other.current) return false

        return true
    }

    override fun hashCode(): Int {
        var result = status.hashCode()
        result = 31 * result + total
        result = 31 * result + current
        return result
    }
}