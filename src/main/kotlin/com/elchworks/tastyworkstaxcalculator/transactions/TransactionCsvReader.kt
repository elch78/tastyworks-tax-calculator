package com.elchworks.tastyworkstaxcalculator

import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionPositionStatus.ASSIGNED
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionPositionStatus.EXPIRED
import com.elchworks.tastyworkstaxcalculator.transactions.*
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
            isStockTrade(columns) || isReverseSplit(columns) -> stockTrade(columns)
            isAssignment(columns) -> optionAssignment(columns)
            else -> error("Unknown transaction type. columns='${columns.joinToString(",")}'")
        }
    }

    private fun optionAssignment(columns: Array<String>) =
        OptionAssignment(
            date = parseDate(columns.date()),
            action = Action.valueOf(columns.action()),
            type = columns.type(),
            symbol = columns.symbol(),
            value = columns.value(),
            quantity = columns.quantity(),
            averagePrice = columns.averagePrice(),
            fees = columns.fees(),
            description = columns.description(),
            subType = columns.subType()
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

    private fun isReverseSplit(columns: Array<String>): Boolean {
        val type = columns.type()
        val instrumentType = columns.instrumentType()
        val subType = columns.subType()
        val isReverseSplit = type == "Receive Deliver" && subType == "Reverse Split" && instrumentType == "Equity"
        log.debug("isReverseSplit type='{}', subType='{}', instrumentType='{}', isOptionTrade='{}'", type, subType, instrumentType, isReverseSplit)
        return isReverseSplit
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

    private fun stockTrade(columns: Array<String>): StockTrade {
        val action = Action.valueOf(columns.action())
        var date = parseDate(columns.date())

        // deal with reverse splits.
        // To not make things unnecessarily complicated we just handle them as normal stock
        // transactions. Because the timestamp is identical for the BTO and STC transactions
        // add a second to the BTO transaction to make sure they are ordered correctly
        val isReverseSplit = isReverseSplit(columns)
        // for reverse split there are no commissions
        val commissions = if (isReverseSplit) usd(0.0) else columns.commissions()

        return StockTrade(
            date = date,
            symbol = columns.symbol(),
            action = action,
            type = columns.type(),
            value = columns.value(),
            description = columns.description(),
            quantity = columns.quantity(),
            averagePrice = columns.averagePrice(),
            commissions = commissions,
            fees = columns.fees(),
            subType = columns.subType()
        )
    }

    private fun optionRemoval(columns: Array<String>) =
        OptionRemoval(
            date = parseDate(columns.date()),
            type = columns.type(),
            subType = columns.subType(),
            status = if (columns.description().contains("assignment")) ASSIGNED else EXPIRED,
            symbol = columns.rootSymbol(),
            expirationDate = parsLocalDate(columns.expirationDate()),
            strikePrice = columns.strikePrice(),
            callOrPut = columns.callOrPut(),
            quantity = columns.quantity(),
            averagePrice = columns.averagePrice(),
            description = columns.description()
        )

    private fun optionTrade(columns: Array<String>) = OptionTrade(
        date = parseDate(columns.date()),
        action = Action.valueOf(columns.action()),
        symbol = columns.rootSymbol(),
        type = columns.type(),
        subType = columns.subType(),
        instrumentType = columns.instrumentType(),
        description = columns.description(),
        value = columns.value(),
        quantity = columns.quantity(),
        averagePrice = columns.averagePrice(),
        commissions = columns.commissions(),
        fees = columns.fees(),
        multiplier = columns.multiplier().toInt(),
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

/**
 * Determines if this CSV row is in the pre-2024 format (18 columns).
 *
 * The 2023 format has 18 columns, while the 2024 format has 21 columns.
 * The 2024 format introduced:
 * - "Sub Type" column at index 2 (shifting all subsequent columns by +1)
 * - "Total" column at index 19
 * - "Currency" column at index 20
 *
 * @return true if this row has 18 columns (2023 format), false if 21 columns (2024 format)
 */
private fun Array<String>.isPre2024Format() = this.size == 18

/**
 * Retrieves the value at the specified column index, accounting for format differences.
 *
 * This function handles the column index offset between 2023 and 2024 CSV formats:
 * - Columns 0-1 (Date, Type): Same index in both formats
 * - Columns 2+ (Action onwards): Index in 2024 format, adjusted by -1 for 2023 format
 *
 * All extension functions (action(), symbol(), etc.) should delegate to this function
 * to ensure correct column access regardless of CSV format.
 *
 * @param index The column index in 2024 format (0-based)
 * @return The string value at the appropriate column for the detected format
 *
 * Example:
 * - getColumn(3) returns this[3] for 2024 format (Action)
 * - getColumn(3) returns this[2] for 2023 format (Action, adjusted for missing Sub Type)
 */
private fun Array<String>.getColumn(index: Int): String {
    return if (isPre2024Format()) {
        // 2023 format: columns 0-1 are the same, columns 2+ are shifted by -1
        if (index <= 1) this[index] else this[index - 1]
    } else {
        // 2024 format: use index as-is
        this[index]
    }
}

fun Array<String>.date() = this[0]
fun Array<String>.type() = this[1]

/**
 * Returns the Sub Type column value (2024 format only).
 *
 * The Sub Type column was introduced in 2024 format at index 2. It contains values like:
 * - "Sell to Open", "Buy to Close" for trades
 * - "Assignment", "Expiration" for option removals
 * - "Reverse Split" for reverse split events
 *
 * For 2023 format files, this column does not exist and returns null.
 *
 * @return Sub Type value for 2024 format, null for 2023 format
 */
fun Array<String>.subType(): String? = if (isPre2024Format()) null else this[2]

fun Array<String>.action() = this.getColumn(3)
fun Array<String>.symbol() = this.getColumn(4)
fun Array<String>.instrumentType() = this.getColumn(5)
fun Array<String>.description() = this.getColumn(6)
fun Array<String>.value() = this.getColumn(7).replace(",", "").toFloat().toMonetaryAmountUsd()
fun Array<String>.quantity() = this.getColumn(8).toInt()
fun Array<String>.averagePrice() = this.getColumn(9).toFloat().toMonetaryAmountUsd()
fun Array<String>.commissions() = this.getColumn(10).toFloat().toMonetaryAmountUsd()
fun Array<String>.fees() = this.getColumn(11).toFloat().toMonetaryAmountUsd()
fun Array<String>.multiplier() = this.getColumn(12)
fun Array<String>.rootSymbol() = this.getColumn(13)
fun Array<String>.underlyingSymbol() = this.getColumn(14)
fun Array<String>.expirationDate() = this.getColumn(15)
fun Array<String>.strikePrice() = this.getColumn(16).toFloat().toMonetaryAmountUsd()
fun Array<String>.callOrPut() = this.getColumn(17)
fun Array<String>.orderNr() = this.getColumn(18)


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
