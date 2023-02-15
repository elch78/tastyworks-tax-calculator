package com.elchworks.tastyworkstaxcalculator

import java.time.Instant
import java.time.LocalDate

data class Transaction(
    val date: Instant,
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
) {
    fun key() = "$callOrPut-$rootSymbol-$expirationDate-$strikePrice"
}
