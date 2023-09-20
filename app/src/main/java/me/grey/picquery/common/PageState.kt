package me.grey.picquery.common

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

enum class PageState {
    None, Loading, Empty, Success, Warning, Error,
}

fun <T : ViewModel> createViewModel(activity: FragmentActivity?, cls: Class<T>?): T {
    return ViewModelProvider(activity!!)[cls!!]
}