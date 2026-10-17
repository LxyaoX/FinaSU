package me.weishu.kernelsu.data.security

import kotlin.math.abs

/** Validation and the canonical text form both credentials are hashed from. */
object Credentials {
    const val PIN_LENGTH_SHORT = 4
    const val PIN_LENGTH_LONG = 6
    const val PATTERN_NODES_MIN = 4
    const val PATTERN_NODES_MAX = 9
    const val PATTERN_NODE_COUNT = 9


    fun isValidPin(pin: CharArray): Boolean =
        (pin.size == PIN_LENGTH_SHORT || pin.size == PIN_LENGTH_LONG) && pin.all { it in '0'..'9' }

    fun isValidPattern(nodes: List<Int>): Boolean =
        nodes.size in PATTERN_NODES_MIN..PATTERN_NODES_MAX &&
            nodes.all { it in 0 until PATTERN_NODE_COUNT } &&
            nodes.toSet().size == nodes.size

    /**
     * A stored pattern may predate the node interpolation below. Verification therefore has to
     * accept both the sampled sequence and the sequence with the nodes crossed by it.
     */
    fun patternSecret(nodes: List<Int>, interpolate: Boolean = false): CharArray? {
        if (!isValidPattern(nodes)) return null
        val sequence = if (interpolate) interpolatePatternNodes(nodes) else nodes
        if (!isValidPattern(sequence)) return null
        return sequence.joinToString("-").toCharArray()
    }

    /**
     * Every sequence that can represent the same visual gesture: the sampled form, the form with
     * crossed nodes added, and the legal forms obtained by dropping such crossed nodes again.
     * The sampled form is first so a legacy record keeps its original meaning.
     */
    fun patternCandidates(secret: CharArray): List<CharArray> {
        val nodes = parsePatternNodes(secret) ?: return emptyList()
        val variants = linkedSetOf<List<Int>>()
        val pending = ArrayDeque<List<Int>>()

        fun remember(sequence: List<Int>) {
            if (isValidPattern(sequence) && variants.add(sequence)) pending.addLast(sequence)
        }

        remember(nodes)
        remember(interpolatePatternNodes(nodes))
        while (pending.isNotEmpty()) {
            val sequence = pending.removeFirst()
            for (index in 1 until sequence.lastIndex) {
                val previous = sequence[index - 1]
                val next = sequence[index + 1]
                if (sequence[index] !in nodesBetween(previous, next)) continue
                remember(sequence.toMutableList().apply { removeAt(index) })
            }
        }
        return variants.map { it.joinToString("-").toCharArray() }
    }

    /** Parses the hyphen-separated canonical text; malformed or duplicate nodes are rejected. */
    private fun isValidPatternText(text: CharArray): Boolean = parsePatternNodes(text) != null

    private fun parsePatternNodes(text: CharArray): List<Int>? {
        if (text.isEmpty()) return null
        val nodes = mutableListOf<Int>()
        var value = 0
        var digits = 0
        for (ch in text) {
            if (ch == '-') {
                if (digits == 0) return null
                nodes += value
                value = 0
                digits = 0
            } else if (ch in '0'..'9') {
                value = value * 10 + (ch - '0')
                digits++
                if (digits > 1) return null
            } else {
                return null
            }
        }
        if (digits == 0) return null
        nodes += value
        return nodes.takeIf(::isValidPattern)
    }

    fun isValid(kind: CredentialKind, secret: CharArray): Boolean = when (kind) {
        CredentialKind.PIN -> isValidPin(secret)
        CredentialKind.PATTERN -> isValidPatternText(secret)
    }

    /** Adds nodes crossed by a straight segment, matching the system pattern lock behaviour. */
    private fun interpolatePatternNodes(nodes: List<Int>): List<Int> {
        if (nodes.isEmpty()) return nodes
        val expanded = mutableListOf(nodes.first())
        nodes.drop(1).forEach { node ->
            val previous = expanded.last()
            expanded += nodesBetween(previous, node).filter { it !in expanded }
            if (node !in expanded) expanded += node
        }
        return expanded
    }

    private fun nodesBetween(from: Int, to: Int): List<Int> {
        val rowStep = to / 3 - from / 3
        val columnStep = to % 3 - from % 3
        val steps = gcd(abs(rowStep), abs(columnStep))
        if (steps <= 1) return emptyList()
        val rowDelta = rowStep / steps
        val columnDelta = columnStep / steps
        return (1 until steps).map { index ->
            (from / 3 + rowDelta * index) * 3 + from % 3 + columnDelta * index
        }
    }

    private fun gcd(first: Int, second: Int): Int {
        var left = first
        var right = second
        while (right != 0) {
            val remainder = left % right
            left = right
            right = remainder
        }
        return left
    }
}
