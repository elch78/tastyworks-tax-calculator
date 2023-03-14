package com.elchworks.tastyworkstaxcalculator.positions

import com.elchworks.tastyworkstaxcalculator.test.randomStockTrade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class StockPositionTest {
    @ParameterizedTest
    @ValueSource(ints = [5, 10, 15])
    fun sellToClose(soldQuantity: Int) {
        // Given
        val btoTx = randomStockTrade().copy(quantity = 10)
        val stcTx = randomStockTrade().copy(quantity = soldQuantity)
        val sut = StockPosition(btoTx)

        // When
        val quantityLeft = sut.sellToClose(stcTx)

        // Then
        assertThat(quantityLeft).isEqualTo(10 - soldQuantity)
    }

    @Test
    fun partialSellTx() {
        // Given
        val btoTx = randomStockTrade().copy(quantity = 10)
        val stcTx = randomStockTrade().copy(quantity = 4)
        val sut = StockPosition(btoTx)

        // When
        assertThat(sut.sellToClose(stcTx)).isEqualTo(6)
        assertThat(sut.sellToClose(stcTx)).isEqualTo(2)
        assertThat(sut.sellToClose(stcTx)).isEqualTo(-2)
    }
}
