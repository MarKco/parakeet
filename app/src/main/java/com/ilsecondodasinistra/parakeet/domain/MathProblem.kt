package com.ilsecondodasinistra.parakeet.domain

import kotlin.random.Random

/** A wake-up arithmetic problem such as "7 × 14 + 38 − 21". [operators] counts every operator, × included. */
data class MathProblem(val text: String, val answer: Int) {
    companion object {
        fun generate(operators: Int, random: Random = Random.Default): MathProblem {
            val count = operators.coerceIn(1, 6)
            while (true) {
                val sb = StringBuilder()
                var total: Int
                var remaining = count
                if (count >= 2) {
                    val a = random.nextInt(3, 10)
                    val b = random.nextInt(11, 20)
                    sb.append("$a × $b")
                    total = a * b
                    remaining -= 1
                } else {
                    val a = random.nextInt(10, 100)
                    sb.append(a)
                    total = a
                }
                repeat(remaining) {
                    val n = random.nextInt(10, 100)
                    if (random.nextBoolean()) { sb.append(" + $n"); total += n } else { sb.append(" − $n"); total -= n }
                }
                if (total > 0) return MathProblem(sb.toString(), total)
            }
        }
    }
}
