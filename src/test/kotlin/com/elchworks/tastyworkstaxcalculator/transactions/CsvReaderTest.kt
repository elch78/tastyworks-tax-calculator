package com.elchworks.tastyworkstaxcalculator.transactions

import com.elchworks.tastyworkstaxcalculator.CsvReader
import com.elchworks.tastyworkstaxcalculator.positions.OptionPositionStatus.ASSIGNED
import com.elchworks.tastyworkstaxcalculator.positions.OptionPositionStatus.EXPIRED
import com.elchworks.tastyworkstaxcalculator.transactions.Action.BUY_TO_CLOSE
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_CLOSE
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_OPEN
import org.assertj.core.api.Assertions.assertThatList
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.time.Instant
import java.time.LocalDate

class CsvReaderTest {

    @Test
    fun optionRemoval() {
        assertThatList(readFile("optionRemoval.csv"))
            .isEqualTo(listOf(
                OptionRemoval(
                    date = Instant.parse("2022-12-30T21:00:00Z"),
                    rootSymbol = "PACB",
                    expirationDate = LocalDate.parse("2022-12-30"),
                    strikePrice = 10.5F,
                    callOrPut = "CALL",
                    status = EXPIRED
                ),
                OptionRemoval(
                    date = Instant.parse("2022-10-28T20:00:00Z"),
                    rootSymbol = "PACB",
                    expirationDate = LocalDate.parse("2022-10-28"),
                    strikePrice = 7.0F,
                    callOrPut = "PUT",
                    status = EXPIRED
                ),
                OptionRemoval(
                    date = Instant.parse("2022-12-02T22:00:00Z"),
                    rootSymbol = "TLRY",
                    expirationDate = LocalDate.parse("2022-12-02"),
                    strikePrice = 4.0F,
                    callOrPut = "CALL",
                    status = ASSIGNED
                ),
                OptionRemoval(
                    date = Instant.parse("2022-11-25T22:00:00Z"),
                    rootSymbol = "APPH",
                    expirationDate = LocalDate.parse("2022-11-25"),
                    strikePrice = 1.5F,
                    callOrPut = "PUT",
                    status = ASSIGNED
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
                    strikePrice = 10.5f,
                    callOrPut = "CALL",
                    action = SELL_TO_OPEN,
                    symbol = "PACB  221230C00010500",
                    instrumentType = "Equity Option",
                    description = "Sold 2 PACB 12/30/22 Call 10.50 @ 0.10",
                    value = 20.0f,
                    quantity = 2,
                    averagePrice = 10.0f,
                    commissions = -2.0f,
                    fees = -0.254f,
                    multiplier = 100,
                    underlyingSymbol = "PACB",
                    orderNr = 247462519
                ),
                OptionTrade(
                    date = Instant.parse("2022-12-02T14:30:04Z"),
                    rootSymbol = "TLRY",
                    expirationDate = LocalDate.parse("2022-12-09"),
                    strikePrice = 5.0f,
                    callOrPut = "CALL",
                    action = BUY_TO_CLOSE,
                    symbol = "TLRY  221209C00005000",
                    instrumentType = "Equity Option",
                    description = "Bought 1 TLRY 12/09/22 Call 5.00 @ 0.09",
                    value = -9.0f,
                    quantity = 1,
                    averagePrice = -9.0f,
                    commissions = 0.0f,
                    fees = -0.12f,
                    multiplier = 100,
                    underlyingSymbol = "TLRY",
                    orderNr = 244821597
                ),
                OptionTrade(
                    date = Instant.parse("2022-10-24T14:17:22Z"),
                    rootSymbol = "PACB",
                    expirationDate = LocalDate.parse("2022-10-28"),
                    strikePrice = 7.0f,
                    callOrPut = "PUT",
                    action = SELL_TO_OPEN,
                    symbol = "PACB  221028P00007000",
                    instrumentType = "Equity Option",
                    description = "Sold 1 PACB 10/28/22 Put 7.00 @ 0.20",
                    value = 20.0f,
                    quantity = 1,
                    averagePrice = 20.0f,
                    commissions = -1.0f,
                    fees = -0.142f,
                    multiplier = 100,
                    underlyingSymbol = "PACB",
                    orderNr = 238917621
                ),
                OptionTrade(
                    date = Instant.parse("2022-10-05T13:30:00Z"),
                    rootSymbol = "MNMD",
                    expirationDate = LocalDate.parse("2022-10-21"),
                    strikePrice = 7.0f,
                    callOrPut = "PUT",
                    action = BUY_TO_CLOSE,
                    symbol = "MNMD  221021P00002500",
                    instrumentType = "Equity Option",
                    description = "Bought 1 MNMD 10/21/22 Put 2.50 @ 0.20",
                    value = -20.0f,
                    quantity = 1,
                    averagePrice = -20.0f,
                    commissions = 0.0f,
                    fees = -0.12f,
                    multiplier = 100,
                    underlyingSymbol = "MNMD",
                    orderNr = 236070484
                ),
            ))
    }

    @Disabled("TODO parse assignments")
    @Test
    fun optionAssignment() {
        assertThatList(readFile("optionAssignment.csv"))
            .isEqualTo(listOf<Transaction>(
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
                    value = 601.71f,
                    description = "Sold 100 APPH @ 6.02",
                    quantity = 100,
                    averagePrice = 6.02f,
                    commissions = 0.0f,
                    fees = -0.102f
                )
            ))
    }

    private fun readFile(csvFile: String) =
        CsvReader().readCsv(ClassPathResource(csvFile).file)
}
