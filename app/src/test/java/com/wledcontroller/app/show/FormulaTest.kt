package com.wledcontroller.app.show

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FormulaTest {
    private fun v(x: Float = 0f, y: Float = 0f, t: Float = 0f) = Formula.Vars(x, y, t)

    @Test
    fun arithmeticAndPrecedence() {
        assertEquals(7f, Formula.parse("1 + 2*3").eval(v()), 1e-5f)
        assertEquals(9f, Formula.parse("(1+2)*3").eval(v()), 1e-5f)
        assertEquals(512f, Formula.parse("2^3^2").eval(v()), 1e-3f)
        assertEquals(-4f, Formula.parse("-2*2").eval(v()), 1e-5f)
        assertEquals(0.25f, Formula.parse("fract(x*2.5)").eval(v(x = 0.5f)), 1e-5f)
        assertEquals(1f, Formula.parse("sin(pi/2)").eval(v()), 1e-5f)
        assertEquals(0.5f, Formula.parse("mix(0, 1, t)").eval(v(t = 0.5f)), 1e-5f)
        assertEquals(0f, Formula.parse("1/0").eval(v()), 0f)
    }

    @Test
    fun everyExampleParses() {
        Formula.examples.forEach { (name, f) -> assertNull("$name: ${Formula.check(f)}", Formula.check(f)) }
    }

    @Test
    fun mistakesAreExplained() {
        assertNotNull(Formula.check("sin(x"))
        assertNotNull(Formula.check("wobble(x)"))
        assertNotNull(Formula.check("x + z"))
        assertNotNull(Formula.check(""))
        assertEquals("\"z\" isn't something I know (use x, y, t, r, a, i, pi, speed, size).", Formula.check("x + z"))
    }
}
