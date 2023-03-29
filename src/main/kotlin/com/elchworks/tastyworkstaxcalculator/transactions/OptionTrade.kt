package com.elchworks.tastyworkstaxcalculator.transactions

import com.elchworks.tastyworkstaxcalculator.positions.option.OptionPositionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoField
import javax.money.MonetaryAmount

data class OptionTrade(
    override val date: Instant,
    override val rootSymbol: String,
    override val expirationDate: LocalDate,
    override val strikePrice: Float,
    override val callOrPut: String,
    val action: Action,
    val symbol: String,
    val instrumentType: String,
    val description: String,
    val value: MonetaryAmount,
    val quantity: Int,
    val averagePrice: MonetaryAmount,
    val commissions: Float,
    val fees: Float,
    val multiplier: Int,
    val underlyingSymbol: String,
    val orderNr: Int
): Transaction, OptionTransaction

data class StockTrade(
    override val date: Instant,
    override val symbol: String,
    override val action: Action,
    override val value: MonetaryAmount,
    override val quantity: Int,
    override val averagePrice: MonetaryAmount,
    val description: String,
    val commissions: Float,
    val fees: Float
): StockTransaction

data class OptionRemoval(
    override val date: Instant,
    override val rootSymbol: String,
    override val expirationDate: LocalDate,
    override val strikePrice: Number,
    override val callOrPut: String,
    val status: OptionPositionStatus,
): OptionTransaction

data class OptionAssignment(
    override val date: Instant,
    override val action: Action,
    override val symbol: String,
    override val value: MonetaryAmount,
    override val quantity: Int,
    override val averagePrice: MonetaryAmount,
    val fees: Float
): StockTransaction

interface Transaction {
    val date: Instant
}

interface OptionTransaction: Transaction {
    val callOrPut: String
    val rootSymbol: String
    val expirationDate: LocalDate
    val strikePrice: Number
}

interface StockTransaction : Transaction{
    val action: Action
    val symbol: String
    val quantity: Int
    val value: MonetaryAmount
    val averagePrice: MonetaryAmount
}

fun Transaction.year(): Int = this.date.atZone(ZoneId.of("CET")).get(ChronoField.YEAR)
fun OptionTrade.optionDescription() = "${this.rootSymbol} ${this.callOrPut} ${this.expirationDate}@${this.strikePrice}"
