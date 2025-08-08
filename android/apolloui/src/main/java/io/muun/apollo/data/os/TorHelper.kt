package io.muun.apollo.data.os

object TorHelper {
    // See: https://www.notion.so/muunwallet/0f57145518dc4c86aa43b13b89f0aafc
    fun process(text: String): String {
        return text.map { character ->
            when {
                character.isLetter() -> {
                    val base = if (character.isLowerCase()) 'a'.code else 'A'.code
                    val offset = ((character.code - base + 13) % 26 + base)
                    offset.toChar()
                }

                else -> character
            }
        }.joinToString("")
    }
}