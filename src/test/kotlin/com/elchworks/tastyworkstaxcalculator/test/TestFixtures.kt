package com.elchworks.tastyworkstaxcalculator.test

import com.elchworks.tastyworkstaxcalculator.Transaction
import org.apache.commons.lang3.RandomUtils
import java.time.Instant
import java.time.LocalDate

fun randomTransaction() =
    Transaction(
        date = Instant.now(),
        type = "",
        action = "SELL_TO_OPEN",
        symbol = "",
        instrumentType = "",
        description = "",
        value = RandomUtils.nextFloat(),
        quantity = RandomUtils.nextInt(),
        averagePrice = 0,
        commissions = 0,
        fees = 0,
        multiplier = 100,
        rootSymbol = "rootSymbom",
        underlyingSymbol = "",
        expirationDate = LocalDate.now(),
        strikePrice = 5.0,
        callOrPut = "PUT",
        orderNr = 0
    )
