package com.flexteam.m3ecalc.calc

/** How trigonometric functions interpret angles. */
enum class AngleUnit { DEG, RAD }

class CalcException(message: String) : Exception(message)

data class CalcOptions(
    val angleUnit: AngleUnit = AngleUnit.DEG,
    /** Number of decimal places kept in a result. */
    val precision: Int = 10,
    val thousandsSeparator: Boolean = true
)

/**
 * The expression engine.
 *
 * A hand written tokenizer plus recursive descent parser, so the calculator
 * supports precedence, parentheses, unary minus, postfix `!` and `%`,
 * implicit multiplication (`2pi`, `3(4+5)`), functions, constants and `Ans`.
 *
 * Percent follows common calculator behaviour: a lone `10%` is `0.1`, while a
 * percent on the right of `+`/`-` is a percentage *of the left operand*, so
 * `200 + 10%` is `220`. Next to `*` and `/` it is a plain `/100`.
 *
 * This file deliberately has no Android imports so the engine can be exercised
 * on a plain JVM (see tools/test-calc.sh).
 */
object Calculator {

    fun evaluate(input: String, options: CalcOptions = CalcOptions(), ans: Double? = null): Double {
        val tokens = tokenize(normalize(input))
        if (tokens.isEmpty()) throw CalcException("Empty expression")
        val parser = Parser(tokens, options, ans)
        val value = parser.parse()
        if (value.isNaN()) throw CalcException("Undefined")
        return clean(value)
    }

    /** Formatted result of a full evaluation. */
    fun evaluateText(
        input: String,
        options: CalcOptions = CalcOptions(),
        ans: Double? = null
    ): String = Formatter.format(evaluate(input, options, ans), options)

    /**
     * Best-effort result while the user is still typing: unbalanced
     * parentheses and dangling operators are completed automatically. Returns
     * null when nothing sensible can be shown yet.
     */
    fun preview(
        input: String,
        options: CalcOptions = CalcOptions(),
        ans: Double? = null
    ): String? {
        val candidate = complete(input) ?: return null
        return try {
            val value = evaluate(candidate, options, ans)
            if (value.isNaN() || value.isInfinite()) null else Formatter.format(value, options)
        } catch (_: CalcException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    /** Balances parentheses and drops a trailing operator, if that helps. */
    fun complete(input: String): String? {
        var text = input.trim()
        if (text.isEmpty()) return null
        // Drop trailing operators / function calls that have no operand yet.
        val trailing = Regex("(?:[+\\-\u00D7\u00F7^*/%!]|\\b[A-Za-z]+\\()$")
        var guard = 0
        while (trailing.containsMatchIn(text) && guard++ < 24) {
            text = trailing.replace(text, "").trimEnd()
        }
        if (text.isEmpty()) return null
        val open = text.count { it == '(' }
        val close = text.count { it == ')' }
        if (open > close) text += ")".repeat(open - close)
        return text.ifEmpty { null }
    }

    // ------------------------------------------------------------------ tokens

    private sealed interface Token
    private data class Num(val value: Double) : Token
    private data class Op(val symbol: String) : Token
    private data class Name(val text: String) : Token
    private object Open : Token
    private object Close : Token

    private fun normalize(input: String): String = input
        .replace('\u00D7', '*')      // ×
        .replace('\u00F7', '/')      // ÷
        .replace('\u2212', '-')      // −
        .replace('\u2013', '-')      // –
        .replace("\u221A", "sqrt")   // √
        .replace("\u03C0", "pi")     // π
        .replace("\u00B2", "^2")     // ²
        .replace("\u00B3", "^3")     // ³
        .replace("\u2009", "")       // thin space used as a group separator
        .replace(" ", "")

    private fun tokenize(text: String): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch.isDigit() || ch == '.' -> {
                    val start = i
                    var dots = 0
                    while (i < text.length && (text[i].isDigit() || text[i] == '.')) {
                        if (text[i] == '.') dots++
                        i++
                    }
                    if (dots > 1) throw CalcException("Malformed number")
                    val raw = text.substring(start, i)
                    out.add(Num(raw.toDoubleOrNull() ?: throw CalcException("Malformed number")))
                }
                ch.isLetter() -> {
                    val start = i
                    while (i < text.length && (text[i].isLetter())) i++
                    out.add(Name(text.substring(start, i).lowercase()))
                }
                ch == '(' -> {
                    out.add(Open); i++
                }
                ch == ')' -> {
                    out.add(Close); i++
                }
                ch in "+-*/^%!" -> {
                    out.add(Op(ch.toString())); i++
                }
                ch == ',' || ch == '_' -> i++ // group separators inside a typed number
                else -> throw CalcException("Unexpected '$ch'")
            }
        }
        return out
    }

    // ------------------------------------------------------------------ parser

    private class Parser(
        private val tokens: List<Token>,
        private val options: CalcOptions,
        private val ans: Double?
    ) {
        private var pos = 0

        private fun peek(): Token? = tokens.getOrNull(pos)
        private fun next(): Token = tokens[pos++]

        fun parse(): Double {
            val (value, _) = expression()
            if (pos != tokens.size) throw CalcException("Unexpected trailing input")
            return value
        }

        /** Returns the value and whether it ended in a percent. */
        private fun expression(): Pair<Double, Boolean> {
            var (left, _) = term()
            while (true) {
                val token = peek() as? Op ?: break
                if (token.symbol != "+" && token.symbol != "-") break
                next()
                val (right, rightPercent) = term()
                left = when {
                    token.symbol == "+" && rightPercent -> left + left * right
                    token.symbol == "-" && rightPercent -> left - left * right
                    token.symbol == "+" -> left + right
                    else -> left - right
                }
            }
            return Pair(left, false)
        }

        private fun term(): Pair<Double, Boolean> {
            var (left, percent) = unary()
            while (true) {
                val token = peek()
                if (token is Op && (token.symbol == "*" || token.symbol == "/")) {
                    next()
                    val (right, _) = unary()
                    left = if (token.symbol == "*") left * right else divide(left, right)
                    percent = false
                } else if (token is Name && token.text == "mod") {
                    next()
                    val (right, _) = unary()
                    if (right == 0.0) throw CalcException("Modulo by zero")
                    left %= right
                    percent = false
                } else if (token is Num || token is Open || token is Name) {
                    // implicit multiplication: 2pi, 3(4+1), 2sin(30)
                    val (right, _) = unary()
                    left *= right
                    percent = false
                } else break
            }
            return Pair(left, percent)
        }

        private fun unary(): Pair<Double, Boolean> {
            val token = peek()
            if (token is Op && (token.symbol == "-" || token.symbol == "+")) {
                next()
                val (value, percent) = unary()
                return Pair(if (token.symbol == "-") -value else value, percent)
            }
            return power()
        }

        private fun power(): Pair<Double, Boolean> {
            val (base, percent) = postfix()
            val token = peek()
            if (token is Op && token.symbol == "^") {
                next()
                val (exponent, _) = unary()
                val result = pow(base, exponent)
                return Pair(result, false)
            }
            return Pair(base, percent)
        }

        private fun postfix(): Pair<Double, Boolean> {
            var (value, percent) = primary()
            while (true) {
                val token = peek() as? Op ?: break
                when (token.symbol) {
                    "!" -> {
                        next(); value = factorial(value); percent = false
                    }
                    "%" -> {
                        next(); value /= 100.0; percent = true
                    }
                    else -> return Pair(value, percent)
                }
            }
            return Pair(value, percent)
        }

        private fun primary(): Pair<Double, Boolean> {
            val token = peek() ?: throw CalcException("Unexpected end of expression")
            return when (token) {
                is Num -> {
                    next(); Pair(token.value, false)
                }
                is Open -> {
                    next()
                    val result = expression()
                    val closing = peek()
                    if (closing !is Close) throw CalcException("Missing )")
                    next()
                    result
                }
                is Name -> {
                    next()
                    call(token.text)
                }
                else -> throw CalcException("Unexpected symbol")
            }
        }

        private fun call(name: String): Pair<Double, Boolean> {
            when (name) {
                "pi" -> return Pair(Math.PI, false)
                "e" -> return Pair(Math.E, false)
                "ans" -> return Pair(ans ?: throw CalcException("No previous answer"), false)
                "rand" -> return Pair(Math.random(), false)
            }
            // Function call: either f(x) or, for sqrt, a bare operand.
            val argument = if (peek() is Open) {
                next()
                val result = expression()
                if (peek() !is Close) throw CalcException("Missing )")
                next()
                result.first
            } else if (name == "sqrt" || name == "cbrt") {
                postfix().first
            } else {
                throw CalcException("Expected ( after $name")
            }
            val value = applyFunction(name, argument)
            return Pair(value, false)
        }

        private fun applyFunction(name: String, x: Double): Double = when (name) {
            "sin" -> kotlin.math.sin(toRadians(x))
            "cos" -> kotlin.math.cos(toRadians(x))
            "tan" -> {
                val radians = toRadians(x)
                val cos = kotlin.math.cos(radians)
                if (kotlin.math.abs(cos) < 1e-12) throw CalcException("Undefined")
                kotlin.math.tan(radians)
            }
            "asin" -> if (x < -1 || x > 1) throw CalcException("Domain error")
                else fromRadians(kotlin.math.asin(x))
            "acos" -> if (x < -1 || x > 1) throw CalcException("Domain error")
                else fromRadians(kotlin.math.acos(x))
            "atan" -> fromRadians(kotlin.math.atan(x))
            "sinh" -> kotlin.math.sinh(x)
            "cosh" -> kotlin.math.cosh(x)
            "tanh" -> kotlin.math.tanh(x)
            "ln" -> if (x <= 0) throw CalcException("Domain error") else kotlin.math.ln(x)
            "log" -> if (x <= 0) throw CalcException("Domain error") else kotlin.math.log10(x)
            "log2" -> if (x <= 0) throw CalcException("Domain error") else kotlin.math.log2(x)
            "exp" -> kotlin.math.exp(x)
            "sqrt" -> if (x < 0) throw CalcException("Domain error") else kotlin.math.sqrt(x)
            "cbrt" -> kotlin.math.cbrt(x)
            "abs" -> kotlin.math.abs(x)
            "floor" -> kotlin.math.floor(x)
            "ceil" -> kotlin.math.ceil(x)
            "round" -> kotlin.math.round(x)
            "sign" -> kotlin.math.sign(x)
            "inv" -> divide(1.0, x)
            "fact" -> factorial(x)
            else -> throw CalcException("Unknown function $name")
        }

        private fun toRadians(x: Double) = if (options.angleUnit == AngleUnit.DEG) Math.toRadians(x) else x
        private fun fromRadians(x: Double) = if (options.angleUnit == AngleUnit.DEG) Math.toDegrees(x) else x

        private fun divide(a: Double, b: Double): Double {
            if (b == 0.0) throw CalcException("Division by zero")
            return a / b
        }

        private fun pow(base: Double, exponent: Double): Double {
            val result = Math.pow(base, exponent)
            if (result.isNaN()) throw CalcException("Undefined")
            if (result.isInfinite()) throw CalcException("Overflow")
            return result
        }

        private fun factorial(x: Double): Double {
            if (x < 0 || x != kotlin.math.floor(x)) throw CalcException("Domain error")
            if (x > 170) throw CalcException("Overflow")
            var result = 1.0
            var i = 2
            while (i <= x.toInt()) {
                result *= i
                i++
            }
            return result
        }
    }

    /** Kills binary floating point noise: 0.1 + 0.2 == 0.3. */
    fun clean(value: Double): Double {
        if (value == 0.0 || value.isNaN() || value.isInfinite()) return value
        val rounded = java.math.BigDecimal(value)
            .round(java.math.MathContext(12, java.math.RoundingMode.HALF_EVEN))
            .toDouble()
        return if (kotlin.math.abs(rounded) < 1e-12) 0.0 else rounded
    }
}
