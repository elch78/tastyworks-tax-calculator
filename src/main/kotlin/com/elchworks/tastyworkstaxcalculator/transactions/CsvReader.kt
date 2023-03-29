package com.elchworks.tastyworkstaxcalculator

import com.elchworks.tastyworkstaxcalculator.positions.OptionPositionStatus.ASSIGNED
import com.elchworks.tastyworkstaxcalculator.positions.OptionPositionStatus.EXPIRED
import com.elchworks.tastyworkstaxcalculator.transactions.Action
import com.elchworks.tastyworkstaxcalculator.transactions.OptionAssignment
import com.elchworks.tastyworkstaxcalculator.transactions.OptionRemoval
import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade
import com.elchworks.tastyworkstaxcalculator.transactions.StockTrade
import com.elchworks.tastyworkstaxcalculator.transactions.Transaction
import com.opencsv.CSVReaderBuilder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.io.FileReader
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.SignStyle
import java.time.temporal.ChronoField

@Component
class CsvReader {
    private val log = LoggerFactory.getLogger(CsvReader::class.java)

    fun readCsv(file: File): List<Transaction> {

        val csvReader =
            CSVReaderBuilder(FileReader(file.absoluteFile)).build()
        return csvReader
            .readAll()
            .filter {
                val type = it[1]
                val action = it[2]
                type == "Trade" ||
                (
                    type.contains("Receive Deliver")
                )
            }
            .filter { !isHeaderLine(it) }
            .map {
                try {
                    parseTransaction(it)
                } catch (e: Exception) {
                    // FIXME: This is not producing the expected outcome. I.e. printing all the values of the line
                    throw RuntimeException("Failed to parse ${it.joinToString { " " }}", e)
                }
            }
    }

    private fun isHeaderLine(it: Array<String>) = it.date().contains("Date")

    private fun parseTransaction(columns: Array<String>): Transaction {
        log.debug("parseTransaction columns='{}'", columns)
        return when {
            isOptionTrade(columns) -> optionTrade(columns)
            isOptionRemoval(columns) -> optionRemoval(columns)
            isStockTrade(columns) -> stockTrade(columns)
            isAssignment(columns) -> optionAssignment(columns)
            else -> error("TODO")
        }
    }

    private fun optionAssignment(columns: Array<String>) =
        OptionAssignment(
            date = parseDate(columns.date()),
            action = Action.valueOf(columns.action()),
            symbol = columns.symbol(),
            value = columns.value(),
            quantity = columns.quantity(),
            averagePrice = columns.averagePrice(),
            fees = columns.fees()
        )

    private fun isAssignment(columns: Array<String>): Boolean {
        val type = columns.type()
        val instrumentType = columns.instrumentType()
        val isAssignment = type == "Receive Deliver" && instrumentType == "Equity"
        log.debug("isAssignment type='{}', instrumentType='{}', isAssignment='{}'", type, instrumentType, isAssignment)
        return isAssignment
    }

    private fun isOptionTrade(columns: Array<String>): Boolean {
        val type = columns.type()
        val instrumentType = columns.instrumentType()
        val isOptionTrade = type == "Trade" && instrumentType == "Equity Option"
        log.debug("isOptionTrade type='{}', instrumentType='{}', isOptionTrade='{}'", type, instrumentType, isOptionTrade)
        return isOptionTrade
    }

    private fun isOptionRemoval(columns: Array<String>): Boolean {
        val type = columns.type()
        val action = columns.action()
        val isOptionRemoval = type == "Receive Deliver" && action.isBlank()
        log.debug("isOptionRemoval type='{}', action='{}', isOptionRemoval='{}'", type, action, isOptionRemoval)
        return isOptionRemoval
    }

    private fun isStockTrade(columns: Array<String>): Boolean {
        val type = columns.type()
        val instrumentType = columns.instrumentType()
        val action = columns.action()
        val isStockTrade = type == "Trade" && instrumentType == "Equity" && action == "SELL_TO_CLOSE"
        log.debug("isStockTrade type='{}', instrumentType='{}', action='{}', isStockTrade='{}'", type, instrumentType, action, isStockTrade)
        return isStockTrade
    }

    private fun stockTrade(columns: Array<String>) =
        StockTrade(
            date = parseDate(columns.date()),
            symbol = columns.symbol(),
            action = Action.valueOf(columns.action()),
            value = columns.value(),
            description = columns.description(),
            quantity = columns.quantity(),
            averagePrice = columns.averagePrice(),
            commissions = columns.commissions(),
            fees = columns.fees()
        )

    private fun optionRemoval(colums: Array<String>) =
        OptionRemoval(
            date = parseDate(colums.date()),
            status = if (colums.description().contains("assignment")) ASSIGNED else EXPIRED,
            rootSymbol = colums.rootSymbol(),
            expirationDate = parsLocalDate(colums.expirationDate()),
            strikePrice = colums.strikePrice().toFloat(),
            callOrPut = colums.callOrPut()
        )

    private fun optionTrade(columns: Array<String>) = OptionTrade(
        date = parseDate(columns.date()),
        action = Action.valueOf(columns.action()),
        symbol = columns.symbol(),
        instrumentType = columns.instrumentType(),
        description = columns.description(),
        value = columns.value(),
        quantity = columns.quantity(),
        averagePrice = columns.averagePrice(),
        commissions = columns.commissions(),
        fees = columns.fees(),
        multiplier = columns.multiplier().toInt(),
        rootSymbol = columns.rootSymbol(),
        underlyingSymbol = columns.underlyingSymbol(),
        expirationDate = parsLocalDate(columns.expirationDate()),
        strikePrice = columns.strikePrice().toFloat(),
        callOrPut = columns.callOrPut(),
        orderNr = columns.orderNr().toInt()
    )

    private fun parsLocalDate(expirationDate: String): LocalDate =
        LocalDate.from(LOCAL_DATE_FORMATTER.parse(expirationDate))

    private fun parseDate(date: String): Instant {
        val parsed = Instant.from(FORMATTER.parse(date))
        log.info("parsed date='{}', parsed='{}'", date, parsed)
        return parsed
    }

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

fun Array<String>.date() = this[0]
fun Array<String>.type() = this[1]
fun Array<String>.action() = this[2]
fun Array<String>.symbol() = this[3]
fun Array<String>.instrumentType() = this[4]
fun Array<String>.description() = this[5]
fun Array<String>.value() = this[6].replace(",", "").toFloat().toMonetaryAmountUsd()
fun Array<String>.quantity() = this[7].toInt()
fun Array<String>.averagePrice() = this[8].toFloat().toMonetaryAmountUsd()
fun Array<String>.commissions() = this[9].toFloat()
fun Array<String>.fees() = this[10].toFloat()
fun Array<String>.multiplier() = this[11]
fun Array<String>.rootSymbol() = this[12]
fun Array<String>.underlyingSymbol() = this[13]
fun Array<String>.expirationDate() = this[14]
fun Array<String>.strikePrice() = this[15]
fun Array<String>.callOrPut() = this[16]
fun Array<String>.orderNr() = this[17]


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
