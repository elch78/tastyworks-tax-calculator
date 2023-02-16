package com.elchworks.tastyworkstaxcalculator

import com.elchworks.tastyworkstaxcalculator.test.randomTransaction
import org.apache.commons.lang3.RandomUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.annotation.DirtiesContext
import java.util.stream.Stream

@SpringBootTest(classes = [TaxProfitAndLoss::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TaxProfitAndLossTest @Autowired constructor(
    private val sut: TaxProfitAndLoss,
    @MockBean private val exchangeRate: ExchangeRate,
){
    @Test
    fun justProfit() {
        // Given
        val txSTO1 = randomTransaction().copy(value = RandomUtils.nextFloat())
        val txSTO2 = randomTransaction().copy(value = RandomUtils.nextFloat())
        val txSTO3 = randomTransaction().copy(value = RandomUtils.nextFloat())
        withRateUsdToEur()


        // When
        sut.onOptionSellToOpen(stoEvent(txSTO1))
        sut.onOptionSellToOpen(stoEvent(txSTO2))
        sut.onOptionSellToOpen(stoEvent(txSTO3))

        // Then
        assertThat(sut.profit).isEqualTo(USD_EUR_EXCHANGE_RATE * (txSTO1.value + txSTO2.value + txSTO3.value))
        assertThat(sut.loss).isEqualTo(0.0F)
    }

    @ParameterizedTest
    @MethodSource
    fun buyToClose(netProfit: Float) {
        // Given
        val txSTO1 = randomTransaction().copy(value = RandomUtils.nextFloat(1.0F, 1000.0F))
        val txBTC = randomTransaction().copy(value = netProfit - txSTO1.value)
        withRateUsdToEur()

        // When
        sut.onOptionSellToOpen(stoEvent(txSTO1))
        sut.onOptionBuyToClose(btcEvent(txSTO1, txBTC))

        // Then
        assertThat(sut.profit).isEqualTo(netProfit * USD_EUR_EXCHANGE_RATE)
        val expectedLoss = if (netProfit >= 0) 0.0F else -netProfit * USD_EUR_EXCHANGE_RATE
        assertThat(sut.loss).isEqualTo(expectedLoss)
    }

    companion object {
        private const val USD_EUR_EXCHANGE_RATE = 2.0F
        @JvmStatic
        fun buyToClose(): Stream<Arguments> = Stream.of(
            // positive netProfit
            Arguments.of(1.0F),
            // negative netProfit
            Arguments.of(-1.0F)
        )
    }

    private fun btcEvent(stoTx: Transaction, btcTx: Transaction) =
        OptionBuyToCloseEvent(
            OptionPosition.fromTransction(stoTx),
            btcTx
        )

    private fun stoEvent(stoTx: Transaction) =
        OptionSellToOpenEvent(
            OptionPosition.fromTransction(stoTx),
            stoTx
        )


    private fun withRateUsdToEur() {
        // For test use exchange rate USD to EUR of 2
        whenever(exchangeRate.usdToEur(any())).thenAnswer {
            val profit: Profit = it.getArgument<Any>(0) as Profit
            profit.value * USD_EUR_EXCHANGE_RATE
        }
    }
}
