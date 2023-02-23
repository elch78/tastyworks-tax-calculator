package com.elchworks.tastyworkstaxcalculator.test.positions

import com.elchworks.tastyworkstaxcalculator.ExchangeRate
import com.elchworks.tastyworkstaxcalculator.positions.OptionPosition
import com.elchworks.tastyworkstaxcalculator.positions.Profit
import com.elchworks.tastyworkstaxcalculator.positions.ProfitAndLoss
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
import java.time.ZonedDateTime
import java.time.temporal.ChronoField.YEAR
import java.util.stream.Stream

@SpringBootTest(classes = [])
class OptionPositionTest @Autowired constructor(
    @MockBean private val exchangeRate: ExchangeRate
) {
    @Test
    fun profitAndLossExpiredPosition() {
        // Given
        val premium = RandomUtils.nextFloat(1.0F, 100.0F)
        val stoTx = randomTransaction().copy(value = premium)
        withRateUsdToEur()

        // When
        val sut = OptionPosition(stoTx, exchangeRate)

        // Then
        assertThat(sut.profitAndLoss()).isEqualTo(
            ProfitAndLoss(
            profit = premium * 2,
            loss = 0.0F
        )
        )
    }

    @ParameterizedTest
    @MethodSource
    fun profitAndLossClosedPosition(netProfit: Float) {
        // Given
        val premium = RandomUtils.nextFloat(1.0F, 100.0F)
        val stoTx = randomTransaction().copy(value = premium, quantity = 1)
        val btcTx = randomTransaction().copy(value = -premium + netProfit, quantity = 1)
        withRateUsdToEur()

        // When
        val sut = OptionPosition(stoTx, exchangeRate)
        sut.buyToClose(btcTx)

        // Then
        val expectedNetProfitEur = netProfit * 2
        val expecteProfit = if(netProfit >= 0) expectedNetProfitEur else 0.0F
        val expecteLoss = if(netProfit >= 0) 0.0F else -expectedNetProfitEur
        assertThat(sut.profitAndLoss()).isEqualTo(
            ProfitAndLoss(
            profit = expecteProfit,
            loss = expecteLoss
        )
        )
    }

    @Test
    fun closedInYear()
    {
        // Given
        val stoTx = randomTransaction()
        val closedInYear = 2021
        val btcTxClosed = ZonedDateTime.now().with(YEAR, closedInYear.toLong()).toInstant()
        val btcTx = randomTransaction().copy(date = btcTxClosed)

        // When
        val sut = OptionPosition(stoTx, exchangeRate)

        // Then
        assertThat(sut.isClosedInYear(closedInYear)).isFalse
        assertThat(sut.isClosedInYear(closedInYear + 1)).isFalse
        assertThat(sut.isClosedInYear(closedInYear - 1)).isFalse

        // When
        sut.buyToClose(btcTx)

        // Then
        assertThat(sut.isClosedInYear(closedInYear)).isTrue
        assertThat(sut.isClosedInYear(closedInYear + 1)).isFalse
        assertThat(sut.isClosedInYear(closedInYear - 1)).isFalse
    }

    companion object {
        private const val USD_EUR_EXCHANGE_RATE = 2.0F
        @JvmStatic
        fun closed(): Stream<Arguments> = Stream.of(
            // positive netProfit
            Arguments.of(1.0F),
            // negative netProfit
            Arguments.of(-1.0F)
        )
    }

    private fun withRateUsdToEur() {
        // For test use exchange rate USD to EUR of 2
        whenever(exchangeRate.usdToEur(any())).thenAnswer {
            val profit: Profit = it.getArgument<Any>(0) as Profit
            profit.value * USD_EUR_EXCHANGE_RATE
        }
    }
}
