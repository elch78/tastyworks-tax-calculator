package com.elchworks.tastyworkstaxcalculator.transactions

import com.elchworks.tastyworkstaxcalculator.TransactionCsvReader
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionPositionStatus.ASSIGNED
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionPositionStatus.EXPIRED
import com.elchworks.tastyworkstaxcalculator.transactions.Action.*
import com.elchworks.tastyworkstaxcalculator.usd
import org.assertj.core.api.Assertions.assertThatList
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.math.BigDecimal
import java.math.BigDecimal.ZERO
import java.time.Instant
import java.time.LocalDate

class TransactionCsvReaderTest {

    @Test
    fun optionRemoval() {
        assertThatList(readFile("optionRemoval.csv"))
            .isEqualTo(listOf(
                OptionRemoval(
                    date = Instant.parse("2022-12-30T21:00:00Z"),
                    symbol = "PACB",
                    type = "Receive Deliver",
                    subType = "Expiration",
                    expirationDate = LocalDate.parse("2022-12-30"),
                    strikePrice = usd(BigDecimal("10.5")),
                    callOrPut = "CALL",
                    quantity = 20,
                    status = EXPIRED,
                    averagePrice = usd(ZERO),
                    description = "Removal of 2.0 PACB 12/30/22 Call 10.50 due to expiration.",
                ),
                OptionRemoval(
                    date = Instant.parse("2022-10-28T20:00:00Z"),
                    symbol = "PACB",
                    type = "Receive Deliver",
                    subType = "Expiration",
                    expirationDate = LocalDate.parse("2022-10-28"),
                    strikePrice = usd(BigDecimal("7.0")),
                    callOrPut = "PUT",
                    quantity = 10,
                    status = EXPIRED,
                    averagePrice = usd(ZERO),
                    description = "Removal of 1.0 PACB 10/28/22 Put 7.00 due to expiration.",
                ),
                OptionRemoval(
                    date = Instant.parse("2022-12-02T22:00:00Z"),
                    symbol = "TLRY",
                    type = "Receive Deliver",
                    subType = "Assignment",
                    expirationDate = LocalDate.parse("2022-12-02"),
                    strikePrice = usd(BigDecimal("4.0")),
                    callOrPut = "CALL",
                    quantity = 1,
                    status = ASSIGNED,
                    averagePrice = usd(ZERO),
                    description = "Removal of option due to assignment",
                ),
                OptionRemoval(
                    date = Instant.parse("2022-11-25T22:00:00Z"),
                    symbol = "APPH",
                    type = "Receive Deliver",
                    subType = "Assignment",
                    expirationDate = LocalDate.parse("2022-11-25"),
                    strikePrice = usd(BigDecimal("1.5")),
                    callOrPut = "PUT",
                    quantity = 5,
                    status = ASSIGNED,
                    averagePrice = usd(ZERO),
                    description = "Removal of option due to assignment",
                ),
            ))
    }

    @Test
    fun optionTrade() {
        assertThatList(readFile("optionTrade.csv"))
            .isEqualTo(listOf(
                OptionTrade(
                    date = Instant.parse("2022-12-21T15:31:40Z"),
                    symbol = "PACB",
                    type = "Trade",
                    subType = "Sell to Open",
                    expirationDate = LocalDate.parse("2022-12-30"),
                    strikePrice = usd(BigDecimal("10.5")),
                    callOrPut = "CALL",
                    action = SELL_TO_OPEN,
                    instrumentType = "Equity Option",
                    description = "Sold 2 PACB 12/30/22 Call 10.50 @ 0.10",
                    value = usd(20.0f),
                    quantity = 2,
                    averagePrice = usd(10.0f),
                    commissions = usd(-2.0f),
                    fees = usd(-0.254f),
                    multiplier = 100,
                    underlyingSymbol = "PACB",
                    orderNr = 247462519,
                ),
                OptionTrade(
                    date = Instant.parse("2022-12-02T14:30:04Z"),
                    symbol = "TLRY",
                    type = "Trade",
                    subType = "Buy to Close",
                    expirationDate = LocalDate.parse("2022-12-09"),
                    strikePrice = usd(BigDecimal("5.0")),
                    callOrPut = "CALL",
                    action = BUY_TO_CLOSE,
                    instrumentType = "Equity Option",
                    description = "Bought 1 TLRY 12/09/22 Call 5.00 @ 0.09",
                    value = usd(-9.0f),
                    quantity = 1,
                    averagePrice = usd(-9.0f),
                    commissions = usd(0.0f),
                    fees = usd(-0.12f),
                    multiplier = 100,
                    underlyingSymbol = "TLRY",
                    orderNr = 244821597,
                ),
                OptionTrade(
                    date = Instant.parse("2022-10-24T14:17:22Z"),
                    symbol = "PACB",
                    type = "Trade",
                    subType = "Sell to Open",
                    expirationDate = LocalDate.parse("2022-10-28"),
                    strikePrice = usd(BigDecimal("7.0")),
                    callOrPut = "PUT",
                    action = SELL_TO_OPEN,
                    instrumentType = "Equity Option",
                    description = "Sold 1 PACB 10/28/22 Put 7.00 @ 0.20",
                    value = usd(20.0f),
                    quantity = 1,
                    averagePrice = usd(20.0f),
                    commissions = usd(-1.0f),
                    fees = usd(-0.142f),
                    multiplier = 100,
                    underlyingSymbol = "PACB",
                    orderNr = 238917621,
                ),
                OptionTrade(
                    date = Instant.parse("2022-10-05T13:30:00Z"),
                    symbol = "MNMD",
                    type = "Trade",
                    subType = "Buy to Close",
                    expirationDate = LocalDate.parse("2022-10-21"),
                    strikePrice = usd(BigDecimal("7.0")),
                    callOrPut = "PUT",
                    action = BUY_TO_CLOSE,
                    instrumentType = "Equity Option",
                    description = "Bought 1 MNMD 10/21/22 Put 2.50 @ 0.20",
                    value = usd(-20.0f),
                    quantity = 1,
                    averagePrice = usd(-20.0f),
                    commissions = usd(0.0f),
                    fees = usd(-0.12f),
                    multiplier = 100,
                    underlyingSymbol = "MNMD",
                    orderNr = 236070484,
                ),
            ))
    }

    @Test
    fun optionAssignment() {
        assertThatList(readFile("optionAssignment.csv"))
            .isEqualTo(listOf<Transaction>(
                OptionAssignment(
                    date = Instant.parse("2022-11-18T22:00:00Z"),
                    action = SELL_TO_CLOSE,
                    type = "Receive Deliver",
                    symbol = "MMAT",
                    value = usd(100.0f),
                    quantity = 100,
                    averagePrice = usd(1.0f),
                    fees = usd(-5.023f),
                    description = "Sell to Close 100 MMAT @ 1.00",
                    subType = "Sell to Close"
                ),
                OptionAssignment(
                    date = Instant.parse("2022-12-16T22:00:00Z"),
                    action = BUY_TO_OPEN,
                    type = "Receive Deliver",
                    symbol = "TLRY",
                    value = usd(-700.0f),
                    quantity = 200,
                    averagePrice = usd(-3.5f),
                    fees = usd(-5.0f),
                    description = "Buy to Open 200 TLRY @ 3.50",
                    subType = "Buy to Open"
                ),
            ))
    }
    @Test
    fun stockTrade() {
        assertThatList(readFile("stockTrade.csv"))
            .isEqualTo(listOf(
                StockTrade(
                    date = Instant.parse("2022-03-28T13:30:27Z"),
                    symbol = "APPH",
                    action = SELL_TO_CLOSE,
                    type = "Trade",
                    value = usd(601.71f),
                    description = "Sold 100 APPH @ 6.02",
                    quantity = 100,
                    averagePrice = usd(6.02f),
                    commissions = usd(0.0f),
                    fees = usd(-0.102f),
                    subType = "Sell to Close"
                ),
                StockTrade(
                    date = Instant.parse("2024-06-17T10:47:02Z"),
                    symbol = "WKHS",
                    action = BUY_TO_OPEN,
                    type = "Receive Deliver",
                    value = usd(-1100.00),
                    description = "Reverse split: Open 20.0 WKHS",
                    quantity = 20,
                    averagePrice = usd(-55.0f),
                    commissions = usd(0.0f),
                    fees = usd(0.0f),
                    subType = "Reverse Split"
                ),
                StockTrade(
                    date = Instant.parse("2024-06-17T10:47:02Z"),
                    symbol = "WKHS",
                    action = SELL_TO_CLOSE,
                    type = "Receive Deliver",
                    value = usd(1100.00),
                    description = "Reverse split: Close 400.0 WKHS",
                    quantity = 400,
                    averagePrice = usd(2.75f),
                    commissions = usd(0.0f),
                    fees = usd(0.0f),
                    subType = "Reverse Split"
                ),
            ))
    }

    @Test
    fun optionTrade2023Format() {
        assertThatList(readFile("optionTrade2023.csv"))
            .isEqualTo(listOf(
                OptionTrade(
                    date = Instant.parse("2022-12-21T15:31:40Z"),
                    symbol = "PACB",
                    type = "Trade",
                    subType = null,
                    expirationDate = LocalDate.parse("2022-12-30"),
                    strikePrice = usd(BigDecimal("10.5")),
                    callOrPut = "CALL",
                    action = SELL_TO_OPEN,
                    instrumentType = "Equity Option",
                    description = "Sold 2 PACB 12/30/22 Call 10.50 @ 0.10",
                    value = usd(20.0f),
                    quantity = 2,
                    averagePrice = usd(10.0f),
                    commissions = usd(-2.0f),
                    fees = usd(-0.254f),
                    multiplier = 100,
                    underlyingSymbol = "PACB",
                    orderNr = 247462519,
                ),
                OptionTrade(
                    date = Instant.parse("2022-12-02T14:30:04Z"),
                    symbol = "TLRY",
                    type = "Trade",
                    subType = null,
                    expirationDate = LocalDate.parse("2022-12-09"),
                    strikePrice = usd(BigDecimal("5.0")),
                    callOrPut = "CALL",
                    action = BUY_TO_CLOSE,
                    instrumentType = "Equity Option",
                    description = "Bought 1 TLRY 12/09/22 Call 5.00 @ 0.09",
                    value = usd(-9.0f),
                    quantity = 1,
                    averagePrice = usd(-9.0f),
                    commissions = usd(0.0f),
                    fees = usd(-0.12f),
                    multiplier = 100,
                    underlyingSymbol = "TLRY",
                    orderNr = 244821597,
                )
            ))
    }

    @Test
    fun stockTrade2023Format() {
        assertThatList(readFile("stockTrade2023.csv"))
            .isEqualTo(listOf(
                StockTrade(
                    date = Instant.parse("2022-03-28T13:30:27Z"),
                    symbol = "APPH",
                    action = SELL_TO_CLOSE,
                    type = "Trade",
                    subType = null,
                    value = usd(601.71f),
                    description = "Sold 100 APPH @ 6.02",
                    quantity = 100,
                    averagePrice = usd(6.02f),
                    commissions = usd(0.0f),
                    fees = usd(-0.102f),
                )
            ))
    }

    @Test
    fun optionRemoval2023Format() {
        assertThatList(readFile("optionRemoval2023.csv"))
            .isEqualTo(listOf(
                OptionRemoval(
                    date = Instant.parse("2022-12-30T21:00:00Z"),
                    status = EXPIRED,
                    symbol = "PACB",
                    type = "Receive Deliver",
                    subType = null,
                    expirationDate = LocalDate.parse("2022-12-30"),
                    strikePrice = usd(BigDecimal("10.5")),
                    callOrPut = "CALL",
                    quantity = 2,
                    averagePrice = usd(0.0f),
                    description = "Removal of 2.0 PACB 12/30/22 Call 10.50 due to expiration.",
                ),
                OptionRemoval(
                    date = Instant.parse("2022-12-16T22:00:00Z"),
                    status = ASSIGNED,
                    symbol = "TLRY",
                    type = "Receive Deliver",
                    subType = null,
                    expirationDate = LocalDate.parse("2022-12-16"),
                    strikePrice = usd(BigDecimal("3.5")),
                    callOrPut = "PUT",
                    quantity = 2,
                    averagePrice = usd(0.0f),
                    description = "Removal of option due to assignment",
                )
            ))
    }

    @Test
    fun optionAssignment2023Format() {
        assertThatList(readFile("optionAssignment2023.csv"))
            .isEqualTo(listOf(
                OptionAssignment(
                    date = Instant.parse("2022-12-16T22:00:00Z"),
                    action = BUY_TO_OPEN,
                    type = "Receive Deliver",
                    symbol = "TLRY",
                    value = usd(-700.0f),
                    quantity = 200,
                    averagePrice = usd(-3.5f),
                    description = "Buy to Open 200 TLRY @ 3.50",
                    fees = usd(-5.0f),
                    subType = null
                ),
                OptionAssignment(
                    date = Instant.parse("2022-01-07T21:00:00Z"),
                    action = SELL_TO_CLOSE,
                    type = "Receive Deliver",
                    symbol = "CLF",
                    value = usd(2100.00f),
                    quantity = 100,
                    averagePrice = usd(21.00f),
                    description = "Sell to Close 100 CLF @ 21.00",
                    fees = usd(-5.032f),
                    subType = null
                )
            ))
    }

    private fun readFile(csvFile: String) =
        TransactionCsvReader().read(ClassPathResource(csvFile).file)
}
