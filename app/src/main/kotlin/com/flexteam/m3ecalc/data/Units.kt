package com.flexteam.m3ecalc.data

import java.math.BigInteger

/** Converter categories, in the order the toolbar shows them. */
object Categories {
    const val CURRENCY = "currency"
    const val WEIGHT = "weight"
    const val DATA = "data"
    const val LENGTH = "length"
    const val TEMPERATURE = "temperature"
    const val BASE = "base"

    val all = listOf(CURRENCY, WEIGHT, DATA, LENGTH, TEMPERATURE, BASE)

    fun title(id: String): String = when (id) {
        CURRENCY -> "Currency"
        WEIGHT -> "Weight"
        DATA -> "Data"
        LENGTH -> "Length"
        TEMPERATURE -> "Temperature"
        else -> "Number base"
    }
}

/** One currency and how much of it equals 1 USD. Stored in the local database. */
data class Rate(val code: String, val rate: Double)

/** A unit of a linear category: [perBase] is the size of one unit in base units. */
data class ConverterUnit(val id: String, val label: String, val perBase: Double)

sealed class Conversion {
    data class Value(val value: Double) : Conversion()
    data class Text(val text: String) : Conversion()
    data class Failed(val message: String) : Conversion()
}

/**
 * Unit tables and the conversion maths.
 *
 * Length, weight and data are linear (a factor per unit). Temperature is
 * affine, so it gets explicit formulas. Currency rates are user-editable and
 * stored in the database, expressed per 1 USD. Number base is a radix change.
 */
object Units {

    val length = listOf(
        ConverterUnit("mm", "Millimetre", 0.001),
        ConverterUnit("cm", "Centimetre", 0.01),
        ConverterUnit("m", "Metre", 1.0),
        ConverterUnit("km", "Kilometre", 1000.0),
        ConverterUnit("in", "Inch", 0.0254),
        ConverterUnit("ft", "Foot", 0.3048),
        ConverterUnit("yd", "Yard", 0.9144),
        ConverterUnit("mi", "Mile", 1609.344),
        ConverterUnit("nmi", "Nautical mile", 1852.0)
    )

    val weight = listOf(
        ConverterUnit("mg", "Milligram", 0.000001),
        ConverterUnit("g", "Gram", 0.001),
        ConverterUnit("kg", "Kilogram", 1.0),
        ConverterUnit("t", "Tonne", 1000.0),
        ConverterUnit("oz", "Ounce", 0.028349523125),
        ConverterUnit("lb", "Pound", 0.45359237),
        ConverterUnit("st", "Stone", 6.35029318)
    )

    val data = listOf(
        ConverterUnit("bit", "Bit", 0.125),
        ConverterUnit("B", "Byte", 1.0),
        ConverterUnit("KB", "Kilobyte", 1024.0),
        ConverterUnit("MB", "Megabyte", 1048576.0),
        ConverterUnit("GB", "Gigabyte", 1073741824.0),
        ConverterUnit("TB", "Terabyte", 1099511627776.0),
        ConverterUnit("PB", "Petabyte", 1125899906842624.0)
    )

    val temperature = listOf(
        ConverterUnit("C", "Celsius", 1.0),
        ConverterUnit("F", "Fahrenheit", 1.0),
        ConverterUnit("K", "Kelvin", 1.0)
    )

    val base = listOf(
        ConverterUnit("DEC", "Decimal", 10.0),
        ConverterUnit("HEX", "Hexadecimal", 16.0),
        ConverterUnit("OCT", "Octal", 8.0),
        ConverterUnit("BIN", "Binary", 2.0)
    )

    fun units(category: String, rates: List<Rate> = emptyList()): List<ConverterUnit> =
        when (category) {
            Categories.LENGTH -> length
            Categories.WEIGHT -> weight
            Categories.DATA -> data
            Categories.TEMPERATURE -> temperature
            Categories.BASE -> base
            else -> rates.map { ConverterUnit(it.code, currencyName(it.code), it.rate) }
        }

    /** Category a unit id belongs to, used by the tests for round trips. */
    fun categoryOf(unitId: String): String = when (unitId) {
        in lengthIds -> com.flexteam.m3ecalc.data.Categories.LENGTH
        in weightIds -> Categories.WEIGHT
        else -> Categories.DATA
    }

    private val lengthIds get() = length.map { it.id }
    private val weightIds get() = weight.map { it.id }

    fun label(category: String, id: String, rates: List<Rate> = emptyList()): String =
        units(category, rates).firstOrNull { it.id == id }?.label ?: id

    fun convert(
        category: String,
        from: String,
        to: String,
        input: String,
        rates: List<Rate> = emptyList()
    ): Conversion {
        if (input.isBlank()) return Conversion.Failed("")
        return when (category) {
            Categories.TEMPERATURE -> temperature(from, to, input)
            Categories.BASE -> base(from, to, input)
            Categories.CURRENCY -> currency(from, to, input, rates)
            else -> linear(units(category), from, to, input)
        }
    }

    private fun linear(
        table: List<ConverterUnit>,
        from: String,
        to: String,
        input: String
    ): Conversion {
        val value = input.trim().toDoubleOrNull()
            ?: return Conversion.Failed("Enter a number")
        val fromUnit = table.firstOrNull { it.id == from }
            ?: return Conversion.Failed("Unknown unit $from")
        val toUnit = table.firstOrNull { it.id == to }
            ?: return Conversion.Failed("Unknown unit $to")
        if (toUnit.perBase == 0.0) return Conversion.Failed("Cannot convert")
        return Conversion.Value(value * fromUnit.perBase / toUnit.perBase)
    }

    private fun temperature(from: String, to: String, input: String): Conversion {
        val value = input.trim().toDoubleOrNull() ?: return Conversion.Failed("Enter a number")
        val celsius = when (from) {
            "C" -> value
            "F" -> (value - 32.0) * 5.0 / 9.0
            "K" -> value - 273.15
            else -> return Conversion.Failed("Unknown unit $from")
        }
        val result = when (to) {
            "C" -> celsius
            "F" -> celsius * 9.0 / 5.0 + 32.0
            "K" -> celsius + 273.15
            else -> return Conversion.Failed("Unknown unit $to")
        }
        return Conversion.Value(result)
    }

    private fun base(from: String, to: String, input: String): Conversion {
        val fromRadix = radix(from) ?: return Conversion.Failed("Unknown base $from")
        val toRadix = radix(to) ?: return Conversion.Failed("Unknown base $to")
        val cleaned = input.trim().replace(" ", "").replace("_", "")
        if (cleaned.isEmpty()) return Conversion.Failed("")
        val negative = cleaned.startsWith("-")
        val digits = if (negative) cleaned.substring(1) else cleaned
        if (digits.isEmpty()) return Conversion.Failed("Enter a number")
        val value = try {
            BigInteger(digits, fromRadix)
        } catch (_: NumberFormatException) {
            return Conversion.Failed("Not a base $fromRadix number")
        }
        val text = value.toString(toRadix).uppercase()
        return Conversion.Text((if (negative) "-" else "") + text)
    }

    private fun radix(id: String): Int? = when (id.uppercase()) {
        "DEC" -> 10
        "HEX" -> 16
        "OCT" -> 8
        "BIN" -> 2
        else -> null
    }

    private fun currency(from: String, to: String, input: String, rates: List<Rate>): Conversion {
        val value = input.trim().toDoubleOrNull() ?: return Conversion.Failed("Enter a number")
        if (rates.isEmpty()) return Conversion.Failed("No rates stored yet")
        val fromRate = rates.firstOrNull { it.code == from }?.rate
            ?: return Conversion.Failed("No rate for $from")
        val toRate = rates.firstOrNull { it.code == to }?.rate
            ?: return Conversion.Failed("No rate for $to")
        if (fromRate <= 0.0 || toRate <= 0.0) return Conversion.Failed("Rate must be positive")
        return Conversion.Value(value * toRate / fromRate)
    }

    /**
     * Reference rates bundled with the app, per 1 USD. They are editable in
     * the converter ("Edit rates") and are never silently refreshed, so the
     * UI always shows when they were last set.
     */
    val defaultRates = listOf(
        Rate("USD", 1.0), Rate("EUR", 0.92), Rate("GBP", 0.79), Rate("JPY", 155.0),
        Rate("CHF", 0.88), Rate("CAD", 1.37), Rate("AUD", 1.52), Rate("NZD", 1.68),
        Rate("SEK", 10.6), Rate("NOK", 10.9), Rate("DKK", 6.9), Rate("PLN", 4.0),
        Rate("CZK", 23.5), Rate("HUF", 380.0), Rate("RON", 4.6), Rate("UAH", 41.5),
        Rate("TRY", 35.0), Rate("ILS", 3.7), Rate("INR", 84.0), Rate("CNY", 7.25),
        Rate("KRW", 1380.0), Rate("SGD", 1.35), Rate("THB", 34.5), Rate("BRL", 5.4),
        Rate("MXN", 20.3), Rate("ZAR", 18.5), Rate("EGP", 49.0), Rate("VND", 25400.0)
    )

    private fun currencyName(code: String): String = when (code) {
        "USD" -> "US Dollar"
        "EUR" -> "Euro"
        "GBP" -> "Pound Sterling"
        "JPY" -> "Japanese Yen"
        "CHF" -> "Swiss Franc"
        "CAD" -> "Canadian Dollar"
        "AUD" -> "Australian Dollar"
        "NZD" -> "New Zealand Dollar"
        "SEK" -> "Swedish Krona"
        "NOK" -> "Norwegian Krone"
        "DKK" -> "Danish Krone"
        "PLN" -> "Polish Zloty"
        "CZK" -> "Czech Koruna"
        "HUF" -> "Hungarian Forint"
        "RON" -> "Romanian Leu"
        "UAH" -> "Ukrainian Hryvnia"
        "TRY" -> "Turkish Lira"
        "ILS" -> "Israeli Shekel"
        "INR" -> "Indian Rupee"
        "CNY" -> "Chinese Yuan"
        "KRW" -> "South Korean Won"
        "SGD" -> "Singapore Dollar"
        "THB" -> "Thai Baht"
        "BRL" -> "Brazilian Real"
        "MXN" -> "Mexican Peso"
        "ZAR" -> "South African Rand"
        "EGP" -> "Egyptian Pound"
        "VND" -> "Vietnamese Dong"
        else -> code
    }
}
