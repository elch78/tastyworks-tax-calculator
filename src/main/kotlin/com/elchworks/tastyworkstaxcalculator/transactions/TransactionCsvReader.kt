package com.elchworks.tastyworkstaxcalculator

import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionPositionStatus.ASSIGNED
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionPositionStatus.EXPIRED
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
class TransactionCsvReader {
    private val log = LoggerFactory.getLogger(TransactionCsvReader::class.java)

    fun read(file: File): List<Transaction> {

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
            else -> error("Unknown transaction type. columns='${columns.joinToString(",")}'")
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

    /**
     * @return true if option is removed due to expiration or removal
     */
    private fun isOptionRemoval(columns: Array<String>): Boolean {
        val type = columns.type()
        val instrumentType = columns.instrumentType()
        val isOptionRemoval = type == "Receive Deliver" && instrumentType == "Equity Option"
        log.debug("isOptionRemoval type='{}', instrumentType='{}', isOptionRemoval='{}'", type, instrumentType, isOptionRemoval)
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
            strikePrice = colums.strikePrice(),
            callOrPut = colums.callOrPut(),
            quantity = colums.quantity(),
            averagePrice = colums.averagePrice(),
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
        strikePrice = columns.strikePrice(),
        callOrPut = columns.callOrPut(),
        orderNr = columns.orderNr().toInt()
    )

    private fun parsLocalDate(expirationDate: String): LocalDate =
        LocalDate.from(LOCAL_DATE_FORMATTER.parse(expirationDate))

    private fun parseDate(date: String): Instant {
        val parsed = Instant.from(FORMATTER.parse(date))
        log.debug("parsed date='{}', parsed='{}'", date, parsed)
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
fun Array<String>.action() = this[3]
fun Array<String>.symbol() = this[4]
fun Array<String>.instrumentType() = this[5]
fun Array<String>.description() = this[6]
fun Array<String>.value() = this[7].replace(",", "").toFloat().toMonetaryAmountUsd()
fun Array<String>.quantity() = this[8].toInt()
fun Array<String>.averagePrice() = this[9].toFloat().toMonetaryAmountUsd()
fun Array<String>.commissions() = this[10].toFloat().toMonetaryAmountUsd()
fun Array<String>.fees() = this[11].toFloat().toMonetaryAmountUsd()
fun Array<String>.multiplier() = this[12]
fun Array<String>.rootSymbol() = this[13]
fun Array<String>.underlyingSymbol() = this[14]
fun Array<String>.expirationDate() = this[15]
fun Array<String>.strikePrice() = this[16].toFloat().toMonetaryAmountUsd()
fun Array<String>.callOrPut() = this[17]
fun Array<String>.orderNr() = this[18]


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
