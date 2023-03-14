package com.elchworks.tastyworkstaxcalculator.positions

import com.elchworks.tastyworkstaxcalculator.test.randomStockTrade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.math.max
import kotlin.math.min

class StockPositionTest {
    @ParameterizedTest
    @ValueSource(ints = [5, 10, 15])
    fun sellToClose(soldQuantity: Int) {
        // Given
        val quantity = 10
        val btoTx = randomStockTrade().copy(quantity = quantity)
        val sut = StockPosition(btoTx)

        // When
        val quantityLeft = sut.sellToClose(soldQuantity)

        // Then
        val expectedQuantityLeftInTx = max(0, soldQuantity - quantity)
        val expectedQuantityLeftInPosition = max(0, quantity - soldQuantity)
        val expectedSold = min(quantity, soldQuantity)
        assertThat(quantityLeft).isEqualTo(PositionCloseResult(expectedSold, expectedQuantityLeftInTx, expectedQuantityLeftInPosition))
    }

    @Test
    fun partialSellTx() {
        // Given
        val btoTx = randomStockTrade().copy(quantity = 10)
        val sut = StockPosition(btoTx)

        // When
        assertThat(sut.sellToClose(4)).isEqualTo(PositionCloseResult(4, 0, 6))
        assertThat(sut.sellToClose(4)).isEqualTo(PositionCloseResult(4, 0, 2))
        assertThat(sut.sellToClose(4)).isEqualTo(PositionCloseResult(2,2, 0))
    }
}
