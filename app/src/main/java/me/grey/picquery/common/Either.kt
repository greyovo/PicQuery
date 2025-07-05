package me.grey.picquery.common

import kotlin.coroutines.cancellation.CancellationException

sealed class Either<out L, out R> {
    data class Left<out L>(val value: L) : Either<L, Nothing>()

    data class Right<out R>(val value: R) : Either<Nothing, R>()

    companion object {
        fun <R> right(value: R): Either<Nothing, R> = Right(value)

        fun <L> left(value: L): Either<L, Nothing> = Left(value)
    }
}

fun <T, R> Either<T, R>.fold(left: (T) -> Any, right: (R) -> Any): Any = when (this) {
    is Either.Left -> left(value)
    is Either.Right -> right(value)
}

fun <T, R> Either<T, R>.getOrHandle(default: (T) -> R): R = when (this) {
    is Either.Left -> default(value)
    is Either.Right -> value
}

suspend fun <R> tryCatch(block: () -> R): Either<Throwable, R> {
    return try {
        Either.right(block())
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        Either.left(e)
    }
}
