package com.elchworks.tastyworkstaxcalculator

import com.opencsv.CSVReaderBuilder
import java.io.FileReader
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.time.format.SignStyle
import java.time.temporal.ChronoField

val FORMATTER = DateTimeFormatterBuilder()
    .parseCaseInsensitive()
    .append(DateTimeFormatter.ISO_LOCAL_DATE)
    .appendLiteral('T')
    .append(DateTimeFormatter.ISO_LOCAL_TIME)
    .appendOffset("+HHmm", "Z")
    .toFormatter()

val LOCAL_DATE_FORMATTER = DateTimeFormatterBuilder()
    .appendValue(ChronoField.MONTH_OF_YEAR, 1, 2, SignStyle.NOT_NEGATIVE)
    .appendLiteral('/')
    .appendValue(ChronoField.DAY_OF_MONTH, 2)
    .appendLiteral('/')
    .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
    .toFormatter()

fun readCsv(inputStream: InputStream): List<Transaction> {
    val reader = inputStream.bufferedReader()
    val header = reader.readLine()

    val csvReader =
        CSVReaderBuilder(FileReader("/home/elch/ws/tastyworks-tax-calculator/src/main/resources/tastyworks_transactions_x3569_2021-11-01_2021-12-31.csv")).build()
    return csvReader
        .readAll()
        .filter {
            val type = it[1]
            !type.contains("Money Movement")
                    &&!type.contains("Receive Deliver")}
        .filter { !it[0].contains("Date") }
        .map {
            val iterator = it.iterator()
            Transaction(
            date = parseDate(iterator.next()),
            type = iterator.next(),
            action = iterator.next(),
            symbol = iterator.next(),
            instrumentType = iterator.next(),
            description = iterator.next(),
            value = iterator.next().toFloat(),
            quantity = iterator.next().toInt(),
            averagePrice = iterator.next().toFloat(),
            commissions = iterator.next().toFloat(),
            fees = iterator.next().toFloat(),
            multiplier = iterator.next().toInt(),
            rootSymbol = iterator.next(),
            underlyingSymbol = iterator.next(),
            expirationDate = parsLocalDate(iterator.next()),// Not needed yet
            strikePrice = iterator.next().toFloat(),
            callOrPut = iterator.next(),
            orderNr = iterator.next().toInt()
        ) }

//    return reader.lineSequence()
//        .filter { it.isNotBlank() }
//        .filter { !it.contains("Money Movement") }
//        .map {
//            val split = it.split(',', ignoreCase = false, limit = 18)
//            val (date, type, action, symbol, instrumentType, description, value, quantity, averagePrice, commissions, fees, multiplier, rootSymbol,
//                underlyingSymbol, expirationDate, strikePrice, callOrPut, orderNr) = split
//
//            Transaction(
//                date = parseDate(date),
//                type = type,
//                action = action,
//                symbol = symbol,
//                instrumentType = instrumentType,
//                description = description,
//                value = 0,
//                quantity = 0,
//                averagePrice = 0.0,
//                commissions = 0.0,
//                fees = 0.0,
//                multiplier = Integer.parseInt(multiplier),
//                rootSymbol = rootSymbol,
//                underlyingSymbol = underlyingSymbol,
//                expirationDate = parsLocalDate(expirationDate),// Not needed yet
//                strikePrice = 0,
//                callOrPut = callOrPut,
//                orderNr = 0
//            )
//        }.toList()
}

private fun parsLocalDate(expirationDate: String): LocalDate =
    LocalDate.from(LOCAL_DATE_FORMATTER.parse(expirationDate))

private fun parseDate(date: String): Instant = Instant.from(FORMATTER.parse(date))

operator fun <T> List<T>.component6() = this[5]
operator fun <T> List<T>.component7() = this[6]
operator fun <T> List<T>.component8() = this[7]
operator fun <T> List<T>.component9() = this[8]
operator fun <T> List<T>.component10() = this[9]
operator fun <T> List<T>.component11() = this[10]
operator fun <T> List<T>.component12() = this[11]
operator fun <T> List<T>.component13() = this[12]
operator fun <T> List<T>.component14() = this[13]
operator fun <T> List<T>.component15() = this[14]
operator fun <T> List<T>.component16() = this[15]
operator fun <T> List<T>.component17() = this[16]
operator fun <T> List<T>.component18() = this[17]
