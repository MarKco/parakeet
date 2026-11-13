package com.ilsecondodasinistra.parakeet

import com.ilsecondodasinistra.parakeet.domain.MathProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MathProblemTest {
    /** Evaluates "a × b + c − d" with the same rules the generator uses. */
    private fun eval(text: String): Int {
        val tokens = text.split(" ")
        var i = 0
        var total = tokens[i++].toInt()
        if (tokens.getOrNull(i) == "×") { i++; total *= tokens[i++].toInt() }
        while (i < tokens.size) {
            val op = tokens[i++]; val n = tokens[i++].toInt()
            total += if (op == "+") n else -n
        }
        return total
    }

    @Test fun answerMatchesText() {
        repeat(200) { n ->
            val p = MathProblem.generate(operators = 1 + n % 5, random = Random(n))
            assertEquals(p.text, eval(p.text), p.answer)
            assertTrue(p.answer > 0)
        }
    }

    @Test fun operatorCountIsRespected() {
        val p = MathProblem.generate(operators = 3, random = Random(1))
        assertEquals(3, p.text.split(" ").count { it in setOf("+", "−", "×") })
    }
}
