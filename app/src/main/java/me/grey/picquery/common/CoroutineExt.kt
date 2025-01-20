package me.grey.picquery.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.qualifier.named
import org.koin.dsl.module

val dispatchersKoinModule = module {
    single<CoroutineDispatcher> { Dispatchers.IO }
    single<CoroutineDispatcher>(named("default")) { Dispatchers.Default }
}
