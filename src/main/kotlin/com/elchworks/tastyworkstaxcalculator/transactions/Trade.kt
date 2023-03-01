package com.elchworks.tastyworkstaxcalculator.transactions

import java.time.Instant
import java.time.LocalDate

data class Trade(
    override val date: Instant,
    val type: String,
    val action: String,
    val symbol: String,
    val instrumentType: String,
    val description: String,
    val value: Float,
    val quantity: Int,
    val averagePrice: Number,
    val commissions: Number,
    val fees: Number,
    val multiplier: Number,
    val rootSymbol: String,
    val underlyingSymbol: String,
    val expirationDate: LocalDate,
    val strikePrice: Number,
    val callOrPut: String,
    val orderNr: Int
): Transaction

data class OptionRemoval(
    override val date: Instant,
    val status: String,
): Transaction

interface Transaction {
    val date: Instant
}
