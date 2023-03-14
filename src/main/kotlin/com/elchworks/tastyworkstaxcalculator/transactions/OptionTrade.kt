package com.elchworks.tastyworkstaxcalculator.transactions

import com.elchworks.tastyworkstaxcalculator.positions.OptionPositionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoField

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
    val value: Float,
    val quantity: Int,
    val averagePrice: Float,
    val commissions: Float,
    val fees: Float,
    val multiplier: Int,
    val underlyingSymbol: String,
    val orderNr: Int
): Transaction, OptionTransaction

data class StockTrade(
    override val date: Instant,
    val symbol: String,
    val action: Action,
    val value: Float,
    val description: String,
    val quantity: Int,
    val averagePrice: Float,
    val commissions: Float,
    val fees: Float
): Transaction

data class OptionRemoval(
    override val date: Instant,
    override val rootSymbol: String,
    override val expirationDate: LocalDate,
    override val strikePrice: Number,
    override val callOrPut: String,
    val status: OptionPositionStatus,
): Transaction, OptionTransaction

interface Transaction {
    val date: Instant
}

interface OptionTransaction {
    val callOrPut: String
    val rootSymbol: String
    val expirationDate: LocalDate
    val strikePrice: Number
}
fun Transaction.year(): Int = this.date.atZone(ZoneId.of("CET")).get(ChronoField.YEAR)
fun OptionTrade.optionDescription() = "${this.rootSymbol} ${this.callOrPut} ${this.expirationDate}@${this.strikePrice}"
