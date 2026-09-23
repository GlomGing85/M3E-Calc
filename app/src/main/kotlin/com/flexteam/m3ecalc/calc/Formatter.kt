package com.flexteam.m3ecalc.calc

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** Turns a computed value into what the display shows. */
object Formatter {

    private const val SCIENTIFIC_UPPER = 1e15
    private const val SCIENTIFIC_LOWER = 1e-9

    fun format(value: Double, options: CalcOptions): String {
        if (value.isNaN()) return "Undefined"
        if (value.isInfinite()) return if (value > 0) "\u221E" else "-\u221E"
        if (value == 0.0) return "0"

        val precision = options.precision.coerceIn(0, 15)
        val abs = kotlin.math.abs(value)
        if (abs >= SCIENTIFIC_UPPER || abs < SCIENTIFIC_LOWER) return scientific(value, precision)

        val decimals = BigDecimal.valueOf(value)
            .setScale(precision, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        val plain = decimals.toPlainString()
        if (plain.replace("-", "").replace(".", "").length > 15) return scientific(value, precision)
        return group(plain, options.thousandsSeparator)
    }

    private fun scientific(value: Double, precision: Int): String {
        val digits = precision.coerceIn(1, 12)
        val raw = String.format(Locale.US, "%.${digits}E", value)
        val parts = raw.split("E")
        val mantissa = BigDecimal(parts[0]).stripTrailingZeros().toPlainString()
        val exponent = parts[1].toInt()
        return "${group(mantissa, false)}E$exponent"
    }

    /** Adds thin-space thousands separators to the integer part. */
    fun group(plain: String, separator: Boolean): String {
        if (!separator) return plain
        val negative = plain.startsWith("-")
        val body = if (negative) plain.substring(1) else plain
        val dot = body.indexOf('.')
        val intPart = if (dot < 0) body else body.substring(0, dot)
        val fracPart = if (dot < 0) "" else body.substring(dot)
        val grouped = StringBuilder()
        var count = 0
        for (i in intPart.indices.reversed()) {
            if (count > 0 && count % 3 == 0) grouped.insert(0, '\u2009')
            grouped.insert(0, intPart[i])
            count++
        }
        return (if (negative) "-" else "") + grouped + fracPart
    }

    /**
     * Formats a converter output: full precision but never more than
     * [precision] decimals, and never in scientific notation for sane ranges.
     */
    fun conversion(value: Double, precision: Int, separator: Boolean): String {
        if (value.isNaN()) return "Undefined"
        if (value.isInfinite()) return if (value > 0) "\u221E" else "-\u221E"
        return format(Calculator.clean(value), CalcOptions(precision = precision, thousandsSeparator = separator))
    }
}
