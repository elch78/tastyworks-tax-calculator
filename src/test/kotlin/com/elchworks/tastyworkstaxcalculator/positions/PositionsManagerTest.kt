package com.elchworks.tastyworkstaxcalculator.positions

import com.elchworks.tastyworkstaxcalculator.test.randomTrade
import com.elchworks.tastyworkstaxcalculator.transactions.Action.BUY_TO_CLOSE
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

@SpringBootTest(classes = [PositionsManager::class])
@RecordApplicationEvents
class PositionsManagerTest @Autowired constructor(
    private val sut: PositionsManager,
) {

    @Test
    fun positionsAreClosedFIFO(eventPublisher: ApplicationEvents) {
        val symbol1 = "ABC"
        val expirationDate1 = LocalDate.now()
        // Given
        val stoTx1 = randomTrade().copy(
            rootSymbol = symbol1,
            action = SELL_TO_OPEN,
            expirationDate = expirationDate1,
        )
        val stoTx2 = randomTrade().copy(
            rootSymbol = symbol1,
            action = SELL_TO_OPEN,
            expirationDate = expirationDate1,
        )
        val btcTx1 = randomTrade().copy(
            rootSymbol = symbol1,
            action = BUY_TO_CLOSE,
            expirationDate = expirationDate1,
        )
        val btcTx2 = randomTrade().copy(
            rootSymbol = symbol1,
            action = BUY_TO_CLOSE,
            expirationDate = expirationDate1,
        )


        // When
        sut.onNewTransaction(NewTransactionEvent(stoTx1))
        sut.onNewTransaction(NewTransactionEvent(stoTx2))
        sut.onNewTransaction(NewTransactionEvent(btcTx1))
        sut.onNewTransaction(NewTransactionEvent(btcTx2))
        val events = eventPublisher
            .stream()
            .filter { it !is TestContextEvent }
            .map { it as PayloadApplicationEvent<*> }
            .map { it.payload }
            .collect(Collectors.toList())


        assertThat(events).isEqualTo(
            listOf(
                OptionSellToOpenEvent(stoTx1),
                OptionSellToOpenEvent(stoTx2),
                OptionBuyToCloseEvent(stoTx1, btcTx1),
                OptionBuyToCloseEvent(stoTx2, btcTx2),
            ))
        // Then
    }
}
