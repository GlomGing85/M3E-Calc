package com.flexteam.m3ecalc.calc

import com.flexteam.m3ecalc.data.Categories
import com.flexteam.m3ecalc.data.Conversion
import com.flexteam.m3ecalc.data.Rate
import com.flexteam.m3ecalc.data.Units

/**
 * JVM smoke tests for the expression engine. The calc package has no Android
 * imports, so this runs on a plain JVM: `tools/test-calc.sh`.
 */
private var passed = 0
private var failed = 0

private fun check(expression: String, expected: String, options: CalcOptions = CalcOptions(), ans: Double? = null) {
    val actual = try {
        Calculator.evaluateText(expression, options, ans)
    } catch (e: CalcException) {
        "error:${e.message}"
    }
    if (actual == expected) {
        passed++
    } else {
        failed++
        println("  FAIL  $expression  expected [$expected] got [$actual]")
    }
}

private fun checkPreview(expression: String, expected: String?) {
    val actual = Calculator.preview(expression)
    if (actual == expected) {
        passed++
    } else {
        failed++
        println("  FAIL  preview($expression) expected [$expected] got [$actual]")
    }
}

private fun checkConvert(
    category: String,
    from: String,
    to: String,
    input: String,
    expected: String,
    rates: List<Rate> = emptyList()
) {
    val result = Units.convert(category, from, to, input, rates)
    val actual = when (result) {
        is Conversion.Value -> Calculator.clean(result.value).toString()
        is Conversion.Text -> result.text
        is Conversion.Failed -> "error:" + result.message
    }
    if (actual == expected) {
        passed++
    } else {
        failed++
        println("  FAIL  $category $input $from->$to expected [$expected] got [$actual]")
    }
}

private fun round6(value: Double): String =
    java.math.BigDecimal(value).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

fun main() {
    val plain = CalcOptions(thousandsSeparator = false)

    println("arithmetic")
    check("1+2", "3", plain)
    check("2+3×4", "14", plain)
    check("(2+3)×4", "20", plain)
    check("10÷4", "2.5", plain)
    check("0.1+0.2", "0.3", plain)
    check("2^10", "1024", plain)
    check("2^-2", "0.25", plain)
    check("-5+3", "-2", plain)
    check("100-20-5", "75", plain)
    check("10 mod 3", "1", plain)
    check("1000000×1000000", "1000000000000", plain)
    check("1234567+1", "1\u2009234\u2009568", CalcOptions(thousandsSeparator = true))

    println("percent")
    check("50%", "0.5", plain)
    check("200+10%", "220", plain)
    check("200-10%", "180", plain)
    check("200×10%", "20", plain)
    check("200÷50%", "400", plain)

    println("factorial, unary, implicit multiplication")
    check("5!", "120", plain)
    check("3!+1", "7", plain)
    check("2π", "6.2831853072", plain)
    check("3(4+5)", "27", plain)
    check("2sqrt(9)", "6", plain)

    println("functions")
    check("sin(30)", "0.5", plain)
    check("cos(60)", "0.5", plain)
    check("tan(45)", "1", plain)
    check("sin(0.5235987755982988)", "0.5", CalcOptions(angleUnit = AngleUnit.RAD, thousandsSeparator = false))
    check("sqrt(16)", "4", plain)
    check("sqrt 81", "9", plain)
    check("ln(1)", "0", plain)
    check("log(1000)", "3", plain)
    check("abs(-4)", "4", plain)
    check("inv(4)", "0.25", plain)
    check("round(2.6)", "3", plain)

    println("errors are reported, never crash")
    check("1÷0", "error:Division by zero", plain)
    check("sqrt(-1)", "error:Domain error", plain)
    check("ln(0)", "error:Domain error", plain)
    check("2+", "error:Unexpected end of expression", plain)
    check("(1+2", "error:Missing )", plain)
    check("", "error:Empty expression", plain)

    println("Ans")
    check("ans×2", "84", plain, ans = 42.0)
    check("ans", "error:No previous answer", plain)

    println("live preview completes partial input")
    checkPreview("12+", "12")
    checkPreview("(2+3", "5")
    checkPreview("2×", "2")
    checkPreview("", null)
    checkPreview("1÷0", null)

    println("formatting")
    val sep = CalcOptions(thousandsSeparator = true, precision = 2)
    check("1234567.891×1", "1\u2009234\u2009567.89", sep)
    check("1÷3", "0.33", sep)

    println("converter: length, weight, data")
    checkConvert(Categories.LENGTH, "m", "km", "1000", "1000.0".let { Calculator.clean(1.0).toString() })
    checkConvert(Categories.LENGTH, "mi", "km", "1", Calculator.clean(1.609344).toString())
    checkConvert(Categories.LENGTH, "in", "cm", "12", Calculator.clean(30.48).toString())
    checkConvert(Categories.WEIGHT, "kg", "lb", "1", Calculator.clean(1 / 0.45359237).toString())
    checkConvert(Categories.WEIGHT, "lb", "kg", "1", Calculator.clean(0.45359237).toString())
    checkConvert(Categories.DATA, "MB", "KB", "1", Calculator.clean(1024.0).toString())
    checkConvert(Categories.DATA, "bit", "B", "8", Calculator.clean(1.0).toString())

    println("converter: temperature")
    checkConvert(Categories.TEMPERATURE, "C", "F", "100", Calculator.clean(212.0).toString())
    checkConvert(Categories.TEMPERATURE, "C", "K", "0", Calculator.clean(273.15).toString())
    checkConvert(Categories.TEMPERATURE, "F", "C", "32", Calculator.clean(0.0).toString())
    checkConvert(Categories.TEMPERATURE, "K", "C", "0", Calculator.clean(-273.15).toString())

    println("converter: number bases")
    checkConvert(Categories.BASE, "DEC", "HEX", "255", "FF")
    checkConvert(Categories.BASE, "HEX", "DEC", "FF", "255")
    checkConvert(Categories.BASE, "BIN", "DEC", "1010", "10")
    checkConvert(Categories.BASE, "DEC", "OCT", "8", "10")
    checkConvert(Categories.BASE, "DEC", "BIN", "-5", "-101")
    checkConvert(Categories.BASE, "BIN", "DEC", "12", "error:Not a base 2 number")

    println("converter: currency and failures")
    val rates = listOf(Rate("USD", 1.0), Rate("EUR", 0.92))
    checkConvert(Categories.CURRENCY, "USD", "EUR", "100", Calculator.clean(92.0).toString(), rates)
    checkConvert(Categories.CURRENCY, "EUR", "USD", "92", Calculator.clean(100.0).toString(), rates)
    checkConvert(Categories.CURRENCY, "USD", "GBP", "1", "error:No rate for GBP", rates)
    checkConvert(Categories.LENGTH, "m", "km", "abc", "error:Enter a number")
    checkConvert(Categories.LENGTH, "m", "km", "", "error:")

    println("completion is what = evaluates")
    if (Calculator.complete("5+") == "5") passed++ else { failed++; println("  FAIL  complete(5+)") }
    if (Calculator.complete("(2+3") == "(2+3)") passed++ else { failed++; println("  FAIL  complete((2+3") }
    if (Calculator.complete("2×") == "2") passed++ else { failed++; println("  FAIL  complete(2x)") }
    if (Calculator.complete("") == null) passed++ else { failed++; println("  FAIL  complete(empty)") }
    check(Calculator.complete("5+")!!, "5", plain)
    if (Calculator.complete("sin(") == null) passed++ else { failed++; println("  FAIL  complete(sin()") }

    println("round trip stability")
    listOf("m" to "ft", "kg" to "oz", "GB" to "B").forEach { (from, to) ->
        val forward = Units.convert(Units.categoryOf(from), from, to, "7") as Conversion.Value
        val back = Units.convert(Units.categoryOf(from), to, from, forward.value.toBigDecimal().toPlainString()) as Conversion.Value
        val actual = round6(back.value)
        if (actual == "7") passed++ else { failed++; println("  FAIL  round trip $from->$to->$from got $actual") }
    }

    println("\n$passed passed, $failed failed")
    if (failed > 0) kotlin.system.exitProcess(1)
}
