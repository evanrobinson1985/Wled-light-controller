package com.wledcontroller.app.show

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A small maths language for the Shader effect: like a GLSL shader, the formula is worked out
 * for every light every frame, and its value (0..1, wrapping) picks the colour from the palette.
 *
 * Variables: x, y (0..1 across and up), t (seconds), r and a (distance and angle from the
 * middle), i (0..1 along the prop), pi, speed, size. Functions: sin cos tan abs sqrt floor
 * fract exp log pow min max clamp mix step smoothstep atan2 length noise. Operators + - * / % ^.
 */
class Formula private constructor(private val root: Node) {

    class Vars(var x: Float = 0f, var y: Float = 0f, var t: Float = 0f, var i: Float = 0f, var speed: Float = 0.5f, var size: Float = 0.5f)

    fun eval(v: Vars): Float = root.eval(v).let { if (it.isNaN() || it.isInfinite()) 0f else it }

    private sealed interface Node { fun eval(v: Vars): Float }
    private class Num(val n: Float) : Node { override fun eval(v: Vars) = n }
    private class Var(val name: String) : Node {
        override fun eval(v: Vars) = when (name) {
            "x" -> v.x; "y" -> v.y; "t" -> v.t; "i" -> v.i
            "r" -> hypot(v.x - 0.5f, v.y - 0.5f)
            "a" -> atan2(v.y - 0.5f, v.x - 0.5f)
            "pi" -> PI.toFloat(); "speed" -> v.speed; "size" -> v.size
            else -> 0f
        }
    }
    private class Neg(val a: Node) : Node { override fun eval(v: Vars) = -a.eval(v) }
    private class Bin(val op: Char, val a: Node, val b: Node) : Node {
        override fun eval(v: Vars): Float {
            val x = a.eval(v); val y = b.eval(v)
            return when (op) {
                '+' -> x + y; '-' -> x - y; '*' -> x * y
                '/' -> if (y == 0f) 0f else x / y
                '%' -> if (y == 0f) 0f else x - y * floor(x / y)
                else -> x.pow(y)
            }
        }
    }
    private class Call(val f: String, val args: List<Node>) : Node {
        override fun eval(v: Vars): Float {
            fun a(n: Int) = args.getOrNull(n)?.eval(v) ?: 0f
            return when (f) {
                "sin" -> sin(a(0)); "cos" -> cos(a(0)); "tan" -> tan(a(0))
                "abs" -> abs(a(0)); "sqrt" -> sqrt(abs(a(0))); "floor" -> floor(a(0))
                "fract" -> a(0).let { it - floor(it) }
                "exp" -> exp(a(0)); "log" -> ln(abs(a(0)).coerceAtLeast(1e-6f))
                "pow" -> abs(a(0)).pow(a(1))
                "min" -> minOf(a(0), a(1)); "max" -> maxOf(a(0), a(1))
                "clamp" -> a(0).coerceIn(a(1), maxOf(a(1), a(2)))
                "mix" -> a(0) + (a(1) - a(0)) * a(2)
                "step" -> if (a(1) < a(0)) 0f else 1f
                "smoothstep" -> { val e0 = a(0); val e1 = a(1); val k = ((a(2) - e0) / (e1 - e0).let { if (it == 0f) 1e-6f else it }).coerceIn(0f, 1f); k * k * (3 - 2 * k) }
                "atan2" -> atan2(a(0), a(1))
                "length" -> hypot(a(0), a(1))
                "noise" -> noise(a(0), a(1))
                else -> 0f
            }
        }
    }

    companion object {
        private val functions = setOf("sin", "cos", "tan", "abs", "sqrt", "floor", "fract", "exp", "log", "pow", "min", "max",
            "clamp", "mix", "step", "smoothstep", "atan2", "length", "noise")
        private val variables = setOf("x", "y", "t", "i", "r", "a", "pi", "speed", "size")

        /** Ready-made formulas to start from. */
        val examples = listOf(
            "Waves" to "sin(x*10 + t*3)*0.5 + 0.5",
            "Rings" to "fract(r*6 - t*0.5)",
            "Swirl" to "fract(a/(2*pi) + r*3 - t*0.3)",
            "Checker" to "step(0.5, fract(x*4 + floor(t))) * 0.5 + step(0.5, fract(y*4))*0.5",
            "Plasma" to "(sin(x*8+t) + sin(y*7-t*1.3) + sin((x+y)*6+t*0.7))/6 + 0.5",
            "Rain" to "fract(y*3 + t*(0.5 + noise(floor(x*20), 1)))",
            "Pulse" to "sin(t*4)*0.5 + 0.5",
            "Lava" to "noise(x*4 + t*0.3, y*4 - t*0.2)",
        )

        /** Parses [text]; throws IllegalArgumentException with a plain-English reason when it can't. */
        fun parse(text: String): Formula = Formula(Parser(text.lowercase()).parseAll())

        /** Null when fine, otherwise what's wrong. */
        fun check(text: String): String? = runCatching { parse(text) }.exceptionOrNull()?.message

        private fun noise(x: Float, y: Float): Float {
            val ix = floor(x).toInt(); val iy = floor(y).toInt()
            val fx = x - ix; val fy = y - iy
            fun h(a: Int, b: Int): Float {
                var n = a * 374761393 + b * 668265263
                n = (n xor (n ushr 13)) * 1274126177
                return ((n xor (n ushr 16)) and 0xFFFF) / 65535f
            }
            fun s(t: Float) = t * t * (3 - 2 * t)
            val top = h(ix, iy) + (h(ix + 1, iy) - h(ix, iy)) * s(fx)
            val bot = h(ix, iy + 1) + (h(ix + 1, iy + 1) - h(ix, iy + 1)) * s(fx)
            return top + (bot - top) * s(fy)
        }
    }

    private class Parser(val s: String) {
        var p = 0
        fun parseAll(): Node {
            require(s.isNotBlank()) { "Type a formula." }
            val n = expr()
            skip()
            require(p >= s.length) { "Didn't understand \"${s.substring(p).take(12)}\"." }
            return n
        }
        fun skip() { while (p < s.length && s[p].isWhitespace()) p++ }
        fun peek(): Char? { skip(); return s.getOrNull(p) }
        fun expr(): Node {
            var n = term()
            while (true) {
                val c = peek()
                if (c == '+' || c == '-') { p++; n = Bin(c, n, term()) } else return n
            }
        }
        fun term(): Node {
            var n = power()
            while (true) {
                val c = peek()
                if (c == '*' || c == '/' || c == '%') { p++; n = Bin(c, n, power()) } else return n
            }
        }
        fun power(): Node {
            val base = unary()
            return if (peek() == '^') { p++; Bin('^', base, power()) } else base
        }
        fun unary(): Node = when (peek()) {
            '-' -> { p++; Neg(unary()) }
            '+' -> { p++; unary() }
            else -> atom()
        }
        fun atom(): Node {
            val c = peek() ?: throw IllegalArgumentException("The formula ends too soon.")
            if (c == '(') {
                p++
                val n = expr()
                require(peek() == ')') { "A bracket isn't closed." }
                p++
                return n
            }
            if (c.isDigit() || c == '.') {
                val start = p
                while (p < s.length && (s[p].isDigit() || s[p] == '.')) p++
                return Num(s.substring(start, p).toFloatOrNull() ?: throw IllegalArgumentException("\"${s.substring(start, p)}\" isn't a number."))
            }
            if (c.isLetter()) {
                val start = p
                while (p < s.length && (s[p].isLetterOrDigit() || s[p] == '_')) p++
                val name = s.substring(start, p)
                if (peek() == '(') {
                    require(name in functions) { "\"$name\" isn't a function I know." }
                    p++
                    val args = ArrayList<Node>()
                    if (peek() != ')') {
                        args += expr()
                        while (peek() == ',') { p++; args += expr() }
                    }
                    require(peek() == ')') { "A bracket isn't closed after $name." }
                    p++
                    return Call(name, args)
                }
                require(name in variables) { "\"$name\" isn't something I know (use x, y, t, r, a, i, pi, speed, size)." }
                return Var(name)
            }
            throw IllegalArgumentException("Didn't expect \"$c\".")
        }
    }
}
