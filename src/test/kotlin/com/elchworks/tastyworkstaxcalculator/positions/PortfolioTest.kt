package com.elchworks.tastyworkstaxcalculator.positions

import com.elchworks.tastyworkstaxcalculator.positions.option.OptionBuyToCloseEvent
import com.elchworks.tastyworkstaxcalculator.positions.option.OptionSellToOpenEvent
import com.elchworks.tastyworkstaxcalculator.positions.stock.StockSellToCloseEvent
import com.elchworks.tastyworkstaxcalculator.test.randomAssignment
import com.elchworks.tastyworkstaxcalculator.test.randomDate
import com.elchworks.tastyworkstaxcalculator.test.randomOptionTrade
import com.elchworks.tastyworkstaxcalculator.test.randomUsdAmount
import com.elchworks.tastyworkstaxcalculator.transactions.Action.BUY_TO_CLOSE
import com.elchworks.tastyworkstaxcalculator.transactions.Action.BUY_TO_OPEN
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_CLOSE
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_OPEN
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.PayloadApplicationEvent
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.context.event.TestContextEvent
import java.time.Month
import java.time.Year
import java.time.ZoneId
import java.util.stream.Collectors

private const val SYMBOL = "ABC"

@SpringBootTest(classes = [Portfolio::class])
@RecordApplicationEvents
class PortfolioTest @Autowired constructor(
    private val sut: Portfolio,
) {

    @Test
    fun optionPositionsAreClosedFIFO(eventPublisher: ApplicationEvents) {
        val expirationDate1 = randomDate(Year.of(2021), Month.JANUARY).atZone(ZoneId.of("CET")).toLocalDate()
        val expirationDate2 = randomDate(Year.of(2021), Month.JANUARY).atZone(ZoneId.of("CET")).toLocalDate()
        val strikePrice1 = randomUsdAmount(0.1f, 100f)
        val strikePrice2 = randomUsdAmount(0.1f, 100f)
        // Given
        val stoTx1 = randomOptionTrade().copy(
            rootSymbol = SYMBOL,
            action = SELL_TO_OPEN,
            expirationDate = expirationDate1,
            strikePrice = strikePrice1,
        )
        val stoTx2 = randomOptionTrade().copy(
            rootSymbol = SYMBOL,
            action = SELL_TO_OPEN,
            expirationDate = expirationDate2,
            strikePrice = strikePrice2,
        )
        val btcTx1 = randomOptionTrade().copy(
            rootSymbol = SYMBOL,
            action = BUY_TO_CLOSE,
            expirationDate = expirationDate1,
            strikePrice = strikePrice1,
        )
        val btcTx2 = randomOptionTrade().copy(
            rootSymbol = SYMBOL,
            action = BUY_TO_CLOSE,
            expirationDate = expirationDate2,
            strikePrice = strikePrice2
        )

        // When
        sut.onNewTransaction(NewTransactionEvent(stoTx1))
        sut.onNewTransaction(NewTransactionEvent(stoTx2))
        sut.onNewTransaction(NewTransactionEvent(btcTx1))
        sut.onNewTransaction(NewTransactionEvent(btcTx2))

        // Then
        assertThat(publishedEvents(eventPublisher)).isEqualTo(
            listOf(
                OptionSellToOpenEvent(stoTx1),
                OptionSellToOpenEvent(stoTx2),
                OptionBuyToCloseEvent(stoTx1, btcTx1, 1),
                OptionBuyToCloseEvent(stoTx2, btcTx2, 1),
            ))
    }

    @Test
    fun test(eventPublisher: ApplicationEvents) {
        // Given
        val btoTx1 = randomAssignment().copy(symbol = SYMBOL, action = BUY_TO_OPEN, quantity = 5)
        val btoTx2 = randomAssignment().copy(symbol = SYMBOL, action = BUY_TO_OPEN, quantity = 4)
        val stcTx1 = randomAssignment().copy(symbol = SYMBOL, action = SELL_TO_CLOSE, quantity = 3)

        // When
        sut.onNewTransaction(NewTransactionEvent(btoTx1))
        sut.onNewTransaction(NewTransactionEvent(btoTx2))
        sut.onNewTransaction(NewTransactionEvent(stcTx1))
        sut.onNewTransaction(NewTransactionEvent(stcTx1))
        sut.onNewTransaction(NewTransactionEvent(stcTx1))

        // Then
        assertThat(publishedEvents(eventPublisher)).isEqualTo(
            listOf(
                StockSellToCloseEvent(btoTx1, stcTx1, 3),
                StockSellToCloseEvent(btoTx1, stcTx1, 2),
                StockSellToCloseEvent(btoTx2, stcTx1, 1),
                StockSellToCloseEvent(btoTx2, stcTx1, 3),
            ))
    }

    private fun publishedEvents(eventPublisher: ApplicationEvents): MutableList<Any>? =
        eventPublisher
            .stream()
            .filter { it !is TestContextEvent }
            .map { it as PayloadApplicationEvent<*> }
            .map { it.payload }
            .collect(Collectors.toList())
}
