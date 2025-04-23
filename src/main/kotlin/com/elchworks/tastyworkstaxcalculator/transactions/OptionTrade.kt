package com.elchworks.tastyworkstaxcalculator.transactions

import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionPositionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.ZoneId
import java.time.temporal.ChronoField
import javax.money.MonetaryAmount

data class OptionTrade(
    override val date: Instant,
    override val symbol: String,
    override val expirationDate: LocalDate,
    override val strikePrice: MonetaryAmount,
    override val callOrPut: String,
    override val quantity: Int,
    override val averagePrice: MonetaryAmount,
    val action: Action,
    val instrumentType: String,
    val description: String,
    val value: MonetaryAmount,
    val commissions: MonetaryAmount,
    val fees: MonetaryAmount,
    val multiplier: Int,
    val underlyingSymbol: String,
    val orderNr: Int
    // FIXME: Transaction unnecessary
): Transaction, OptionTransaction

data class StockTrade(
    override val date: Instant,
    override val symbol: String,
    override val action: Action,
    override val value: MonetaryAmount,
    override val quantity: Int,
    override val averagePrice: MonetaryAmount,
    val description: String,
    val commissions: MonetaryAmount,
    val fees: MonetaryAmount
): StockTransaction

data class OptionRemoval(
    override val date: Instant,
    override val symbol: String,
    override val expirationDate: LocalDate,
    override val strikePrice: MonetaryAmount,
    override val callOrPut: String,
    override val quantity: Int,
    override val averagePrice: MonetaryAmount,
    val status: OptionPositionStatus,
): OptionTransaction

data class OptionAssignment(
    override val date: Instant,
    override val action: Action,
    override val symbol: String,
    override val value: MonetaryAmount,
    override val quantity: Int,
    override val averagePrice: MonetaryAmount,
    val fees: MonetaryAmount
): StockTransaction

interface Transaction {
    val date: Instant
    val symbol: String
    val quantity: Int
    val averagePrice: MonetaryAmount
}

interface OptionTransaction: Transaction {
    val callOrPut: String
    val expirationDate: LocalDate
    val strikePrice: MonetaryAmount
}

interface StockTransaction : Transaction{
    val action: Action
    val value: MonetaryAmount
}

fun Transaction.year(): Year = Year.of(this.date.atZone(ZoneId.of("CET")).get(ChronoField.YEAR))
fun OptionTrade.optionDescription() = "${this.symbol} ${this.callOrPut} ${this.expirationDate}@${this.strikePrice}"
