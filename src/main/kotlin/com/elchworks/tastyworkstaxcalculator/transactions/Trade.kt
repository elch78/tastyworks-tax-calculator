package com.elchworks.tastyworkstaxcalculator.transactions

import com.elchworks.tastyworkstaxcalculator.positions.OptionPositionStatus
import java.time.Instant
import java.time.LocalDate

data class Trade(
    override val date: Instant,
    override val rootSymbol: String,
    override val expirationDate: LocalDate,
    override val strikePrice: Number,
    override val callOrPut: String,
    val action: Action,
    val symbol: String,
    val instrumentType: String,
    val description: String,
    val value: Float,
    val quantity: Int,
    val averagePrice: Number,
    val commissions: Number,
    val fees: Number,
    val multiplier: Number,
    val underlyingSymbol: String,
    val orderNr: Int
): Transaction, OptionTransaction

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
