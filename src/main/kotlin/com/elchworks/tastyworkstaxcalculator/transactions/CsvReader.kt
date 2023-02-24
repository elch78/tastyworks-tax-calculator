package com.elchworks.tastyworkstaxcalculator

import com.elchworks.tastyworkstaxcalculator.transactions.AllTransactionsProcessedEvent
import com.elchworks.tastyworkstaxcalculator.transactions.NewTransactionEvent
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
                )
            }
            .sortedBy { it.date }
            .forEach {
                eventPublisher.publishEvent(NewTransactionEvent(it))
            }
            eventPublisher.publishEvent(AllTransactionsProcessedEvent())
    }

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
