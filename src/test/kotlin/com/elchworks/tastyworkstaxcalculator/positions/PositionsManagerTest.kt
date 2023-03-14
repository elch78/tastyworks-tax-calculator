package com.elchworks.tastyworkstaxcalculator.positions

import com.elchworks.tastyworkstaxcalculator.test.randomAssignment
import com.elchworks.tastyworkstaxcalculator.test.randomOptionTrade
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
import java.time.LocalDate
import java.util.stream.Collectors

private const val SYMBOL = "ABC"

@SpringBootTest(classes = [PositionsManager::class])
@RecordApplicationEvents
class PositionsManagerTest @Autowired constructor(
    private val sut: PositionsManager,
) {

    @Test
    fun optionPositionsAreClosedFIFO(eventPublisher: ApplicationEvents) {
        val expirationDate1 = LocalDate.now()
        // Given
        val stoTx1 = randomOptionTrade().copy(
            rootSymbol = SYMBOL,
            action = SELL_TO_OPEN,
            expirationDate = expirationDate1,
        )
        val stoTx2 = randomOptionTrade().copy(
            rootSymbol = SYMBOL,
            action = SELL_TO_OPEN,
            expirationDate = expirationDate1,
        )
        val btcTx1 = randomOptionTrade().copy(
            rootSymbol = SYMBOL,
            action = BUY_TO_CLOSE,
            expirationDate = expirationDate1,
        )
        val btcTx2 = randomOptionTrade().copy(
            rootSymbol = SYMBOL,
            action = BUY_TO_CLOSE,
            expirationDate = expirationDate1,
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
                OptionBuyToCloseEvent(stoTx1, btcTx1),
                OptionBuyToCloseEvent(stoTx2, btcTx2),
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
