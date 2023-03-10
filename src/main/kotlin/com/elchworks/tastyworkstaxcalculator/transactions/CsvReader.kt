package com.elchworks.tastyworkstaxcalculator

import com.elchworks.tastyworkstaxcalculator.positions.NewTransactionEvent
import com.elchworks.tastyworkstaxcalculator.positions.OptionPositionStatus.ASSIGNED
import com.elchworks.tastyworkstaxcalculator.positions.OptionPositionStatus.EXPIRED
import com.elchworks.tastyworkstaxcalculator.transactions.Action
import com.elchworks.tastyworkstaxcalculator.transactions.OptionRemoval
import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade
import com.elchworks.tastyworkstaxcalculator.transactions.Transaction
import com.opencsv.CSVReaderBuilder
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.io.FileReader
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.SignStyle
import java.time.temporal.ChronoField

@Component
class CsvReader(
    private val eventPublisher: ApplicationEventPublisher
) {
    fun readCsv(inputStream: InputStream) {
        val reader = inputStream.bufferedReader()
        val header = reader.readLine()

        val csvReader =
            CSVReaderBuilder(FileReader("/home/elch/ws/tastyworks-tax-calculator/src/main/resources/tastyworks_transactions_x3569_2021-11-01_2021-12-31.csv")).build()
        csvReader
            .readAll()
            .filter {
                val type = it[1]
                val action = it[2]
                type == "Trade" ||
                (
                    type.contains("Receive Deliver") &&
                    action.isBlank()
                )
            }
            .filter { !it[0].contains("Date") }
            .map {
                parseTransaction(it)
            }
            .sortedBy { it.date }
            .forEach {
                eventPublisher.publishEvent(NewTransactionEvent(it))
            }
    }

    private fun parseTransaction(it: Array<String>): Transaction {
        val iterator = it.iterator()
        val date = parseDate(iterator.next())
        val type = iterator.next()
        val action = iterator.next()
        return when {
            type == "Trade" -> trade(date, action, iterator)
            type == "Receive Deliver" && action.isBlank() -> optionRemoval(date, iterator)
            // TODO assignment of stock
//            type == "Receive Deliver" && !action.isBlank() -> optionRemoval(date, action)
            else -> error("TODO")
        }
    }

    private fun optionRemoval(date: Instant, iterator: Iterator<String>): OptionRemoval {
        // skip Symbol, Instrument Type
        iterator.next()
        iterator.next()
        val description = iterator.next()
        val status = if (description.contains("assignment")) ASSIGNED else EXPIRED
        // skip Value, Quantity, averagePrice, commissions, fees, multiplier
        iterator.next()
        iterator.next()
        iterator.next()
        iterator.next()
        iterator.next()
        iterator.next()
        val rootSymbol = iterator.next()
        // skip underlying symbol
        iterator.next()
        val expirationDate = parsLocalDate(iterator.next())
        val strikePrice = iterator.next().toFloat()
        val callOrPut = iterator.next()
        return OptionRemoval(
            date = date,
            status = status,
            rootSymbol = rootSymbol,
            expirationDate = expirationDate,
            strikePrice = strikePrice,
            callOrPut = callOrPut
        )
    }

    private fun trade(date: Instant, action: String, iterator: Iterator<String>) = OptionTrade(
        date = date,
        action = Action.valueOf(action),
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
        expirationDate = parsLocalDate(iterator.next()),
        strikePrice = iterator.next().toFloat(),
        callOrPut = iterator.next(),
        orderNr = iterator.next().toInt()
    )

    private fun parsLocalDate(expirationDate: String): LocalDate =
        LocalDate.from(LOCAL_DATE_FORMATTER.parse(expirationDate))

    private fun parseDate(date: String): Instant = Instant.from(FORMATTER.parse(date))
    companion object {
        private val FORMATTER = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral('T')
            .append(DateTimeFormatter.ISO_LOCAL_TIME)
            .appendOffset("+HHmm", "Z")
            .toFormatter()

        private val LOCAL_DATE_FORMATTER = DateTimeFormatterBuilder()
            .appendValue(ChronoField.MONTH_OF_YEAR, 1, 2, SignStyle.NOT_NEGATIVE)
            .appendLiteral('/')
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .appendLiteral('/')
            .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
            .toFormatter()
    }
}




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
