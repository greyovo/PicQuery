package me.grey.picquery.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module

val dispatchersKoinModule = module {
    single<CoroutineDispatcher> { Dispatchers.IO }
    single<CoroutineDispatcher>(named("default")) { Dispatchers.Default }
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>()) }
}
