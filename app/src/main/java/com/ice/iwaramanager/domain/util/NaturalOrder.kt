package com.ice.iwaramanager.domain.util

import java.util.Locale

object NaturalOrder {
    private val tokenRegex = Regex("(\\d+)|(\\D+)")

    fun compare(left: String, right: String): Int {
        val leftTokens = tokenize(left)
        val rightTokens = tokenize(right)
        val size = maxOf(leftTokens.size, rightTokens.size)
        for (index in 0 until size) {
            val leftToken = leftTokens.getOrNull(index) ?: return -1
            val rightToken = rightTokens.getOrNull(index) ?: return 1
            val comparison = compareToken(leftToken, rightToken)
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun tokenize(value: String): List<String> {
        val normalized = value.replace('\\', '/').lowercase(Locale.ROOT)
        return tokenRegex.findAll(normalized).map { it.value }.toList()
    }

    private fun compareToken(left: String, right: String): Int {
        if (!left.all(Char::isDigit) || !right.all(Char::isDigit)) {
            return left.compareTo(right)
        }
        val normalizedLeft = left.trimStart('0').ifEmpty { "0" }
        val normalizedRight = right.trimStart('0').ifEmpty { "0" }
        return when {
            normalizedLeft.length != normalizedRight.length ->
                normalizedLeft.length.compareTo(normalizedRight.length)
            else -> normalizedLeft.compareTo(normalizedRight)
        }
    }
}
