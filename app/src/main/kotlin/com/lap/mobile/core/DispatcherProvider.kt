package com.lap.mobile.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Injectable abstraction over the four standard coroutine dispatchers so
 * ViewModels and repositories can be unit-tested against a
 * kotlinx-coroutines-test `TestDispatcher` without touching production
 * thread pools. T023 will wire a `@Binds` in the Hilt DI module so the
 * interface is what gets injected.
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}

class DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}
