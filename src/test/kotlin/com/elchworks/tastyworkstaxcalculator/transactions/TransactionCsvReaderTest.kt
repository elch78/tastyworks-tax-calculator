package com.elchworks.tastyworkstaxcalculator.transactions

import com.elchworks.tastyworkstaxcalculator.TransactionCsvReader
import com.elchworks.tastyworkstaxcalculator.positions.option.OptionPositionStatus.ASSIGNED
import com.elchworks.tastyworkstaxcalculator.positions.option.OptionPositionStatus.EXPIRED
import com.elchworks.tastyworkstaxcalculator.transactions.Action.BUY_TO_CLOSE
import com.elchworks.tastyworkstaxcalculator.transactions.Action.BUY_TO_OPEN
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_CLOSE
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_OPEN
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
                    rootSymbol = "PACB",
                    expirationDate = LocalDate.parse("2022-12-30"),
                    strikePrice = usd(BigDecimal("10.5")),
                    callOrPut = "CALL",
                    quantity = 20,
                    status = EXPIRED,
                    averagePrice = usd(ZERO),
                ),
                OptionRemoval(
                    date = Instant.parse("2022-10-28T20:00:00Z"),
                    rootSymbol = "PACB",
                    expirationDate = LocalDate.parse("2022-10-28"),
                    strikePrice = usd(BigDecimal("7.0")),
                    callOrPut = "PUT",
                    quantity = 10,
                    status = EXPIRED,
                    averagePrice = usd(ZERO),
                ),
                OptionRemoval(
                    date = Instant.parse("2022-12-02T22:00:00Z"),
                    rootSymbol = "TLRY",
                    expirationDate = LocalDate.parse("2022-12-02"),
                    strikePrice = usd(BigDecimal("4.0")),
                    callOrPut = "CALL",
                    quantity = 1,
                    status = ASSIGNED,
                    averagePrice = usd(ZERO),
                ),
                OptionRemoval(
                    date = Instant.parse("2022-11-25T22:00:00Z"),
                    rootSymbol = "APPH",
                    expirationDate = LocalDate.parse("2022-11-25"),
                    strikePrice = usd(BigDecimal("1.5")),
                    callOrPut = "PUT",
                    quantity = 5,
                    status = ASSIGNED,
                    averagePrice = usd(ZERO),
                ),
            ))
    }

    @Test
    fun optionTrade() {
        assertThatList(readFile("optionTrade.csv"))
            .isEqualTo(listOf(
                OptionTrade(
                    date = Instant.parse("2022-12-21T15:31:40Z"),
                    rootSymbol = "PACB",
                    expirationDate = LocalDate.parse("2022-12-30"),
                    strikePrice = usd(BigDecimal("10.5")),
                    callOrPut = "CALL",
                    action = SELL_TO_OPEN,
                    symbol = "PACB  221230C00010500",
                    instrumentType = "Equity Option",
                    description = "Sold 2 PACB 12/30/22 Call 10.50 @ 0.10",
                    value = usd(20.0f),
                    quantity = 2,
                    averagePrice = usd(10.0f),
                    commissions = usd(-2.0f),
                    fees = usd(-0.254f),
                    multiplier = 100,
                    underlyingSymbol = "PACB",
                    orderNr = 247462519
                ),
                OptionTrade(
                    date = Instant.parse("2022-12-02T14:30:04Z"),
                    rootSymbol = "TLRY",
                    expirationDate = LocalDate.parse("2022-12-09"),
                    strikePrice = usd(BigDecimal("5.0")),
                    callOrPut = "CALL",
                    action = BUY_TO_CLOSE,
                    symbol = "TLRY  221209C00005000",
                    instrumentType = "Equity Option",
                    description = "Bought 1 TLRY 12/09/22 Call 5.00 @ 0.09",
                    value = usd(-9.0f),
                    quantity = 1,
                    averagePrice = usd(-9.0f),
                    commissions = usd(0.0f),
                    fees = usd(-0.12f),
                    multiplier = 100,
                    underlyingSymbol = "TLRY",
                    orderNr = 244821597
                ),
                OptionTrade(
                    date = Instant.parse("2022-10-24T14:17:22Z"),
                    rootSymbol = "PACB",
                    expirationDate = LocalDate.parse("2022-10-28"),
                    strikePrice = usd(BigDecimal("7.0")),
                    callOrPut = "PUT",
                    action = SELL_TO_OPEN,
                    symbol = "PACB  221028P00007000",
                    instrumentType = "Equity Option",
                    description = "Sold 1 PACB 10/28/22 Put 7.00 @ 0.20",
                    value = usd(20.0f),
                    quantity = 1,
                    averagePrice = usd(20.0f),
                    commissions = usd(-1.0f),
                    fees = usd(-0.142f),
                    multiplier = 100,
                    underlyingSymbol = "PACB",
                    orderNr = 238917621
                ),
                OptionTrade(
                    date = Instant.parse("2022-10-05T13:30:00Z"),
                    rootSymbol = "MNMD",
                    expirationDate = LocalDate.parse("2022-10-21"),
                    strikePrice = usd(BigDecimal("7.0")),
                    callOrPut = "PUT",
                    action = BUY_TO_CLOSE,
                    symbol = "MNMD  221021P00002500",
                    instrumentType = "Equity Option",
                    description = "Bought 1 MNMD 10/21/22 Put 2.50 @ 0.20",
                    value = usd(-20.0f),
                    quantity = 1,
                    averagePrice = usd(-20.0f),
                    commissions = usd(0.0f),
                    fees = usd(-0.12f),
                    multiplier = 100,
                    underlyingSymbol = "MNMD",
                    orderNr = 236070484
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
                    symbol = "MMAT",
                    value = usd(100.0f),
                    quantity = 100,
                    averagePrice = usd(1.0f),
                    fees = usd(-5.023f)
                ),
                OptionAssignment(
                    date = Instant.parse("2022-12-16T22:00:00Z"),
                    action = BUY_TO_OPEN,
                    symbol = "TLRY",
                    value = usd(-700.0f),
                    quantity = 200,
                    averagePrice = usd(-3.5f),
                    fees = usd(-5.0f)
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
                    value = usd(601.71f),
                    description = "Sold 100 APPH @ 6.02",
                    quantity = 100,
                    averagePrice = usd(6.02f),
                    commissions = usd(0.0f),
                    fees = usd(-0.102f)
                )
            ))
    }

    private fun readFile(csvFile: String) =
        TransactionCsvReader().read(ClassPathResource(csvFile).file)
}
