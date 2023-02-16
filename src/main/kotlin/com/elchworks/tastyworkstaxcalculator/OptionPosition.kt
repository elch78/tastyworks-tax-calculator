package com.elchworks.tastyworkstaxcalculator

import org.slf4j.LoggerFactory

data class OptionPosition (
    val stoTx: Transaction,
) {
    private val log = LoggerFactory.getLogger(OptionPosition::class.java)

    fun netPremium() = Profit(value = stoTx.value, date = stoTx.date)

    fun openDate() = stoTx.date

    companion object {
        fun fromTransction(transaction: Transaction) = OptionPosition(
            stoTx = transaction,
        )
    }
}
