package io.muun.apollo.presentation.ui.utils

/**
 * Rotates enum values cyclically. Given A, B, C, then A.rotate() = B, B.rotate() = C, C.rotate() = A.
 */
inline fun <reified T : Enum<T>> T.rotate(): T {
    val values = enumValues<T>()
    return values[(ordinal + 1) % values.size]
}
