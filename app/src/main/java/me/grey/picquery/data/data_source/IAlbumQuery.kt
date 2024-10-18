package me.grey.picquery.data.data_source

import me.grey.picquery.data.model.Photo

fun interface IAlbumQuery {
    fun onAlbumQuery(photos:List<Photo>)
}