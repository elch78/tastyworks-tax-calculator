package com.elchworks.tastyworkstaxcalculator.test

import com.elchworks.tastyworkstaxcalculator.Transaction
import org.apache.commons.lang3.RandomUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

fun randomTransaction() =
    Transaction(
        date = randomDateIn2021(),
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

private fun randomDateIn2021(): Instant =
    ZonedDateTime.of(2021, 11, 1, 1, 1, 1, 0, ZoneId.of("CET"))
        .plusMinutes(RandomUtils.nextLong(1,  86400))
        .toInstant()
