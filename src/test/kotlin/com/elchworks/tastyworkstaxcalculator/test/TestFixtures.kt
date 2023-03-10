package com.elchworks.tastyworkstaxcalculator.test

import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_OPEN
import com.elchworks.tastyworkstaxcalculator.transactions.Trade
import org.apache.commons.lang3.RandomUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

fun randomTrade() =
    Trade(
        date = randomDateIn2021(),
        action = SELL_TO_OPEN,
        symbol = "",
        instrumentType = "",
        description = "",
        value = RandomUtils.nextFloat(),
        quantity = 1, // not random for now. In order to prevent error message "Currently only complete closing of positions is supported."
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
