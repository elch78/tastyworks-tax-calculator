package com.elchworks.tastyworkstaxcalculator

import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate

data class OptionPosition (
    val rootSymbol: String,
    val expiration: LocalDate,
    val callOrPut: String,
    val value: Float,
    val openDate: Instant,
) {
    private val log = LoggerFactory.getLogger(OptionPosition::class.java)

    companion object {
        fun fromTransction(transaction: Transaction) = OptionPosition(
            rootSymbol = transaction.rootSymbol,
            expiration = transaction.expirationDate,
            callOrPut = transaction.callOrPut,
            value = transaction.value,
            openDate = transaction.date
        )
    }
}
