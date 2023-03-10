package com.elchworks.tastyworkstaxcalculator.test.positions

import com.elchworks.tastyworkstaxcalculator.ExchangeRate
import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYear
import com.elchworks.tastyworkstaxcalculator.positions.OptionBuyToCloseEvent
import com.elchworks.tastyworkstaxcalculator.positions.OptionSellToOpenEvent
import com.elchworks.tastyworkstaxcalculator.positions.Profit
import com.elchworks.tastyworkstaxcalculator.positions.ProfitAndLoss
import com.elchworks.tastyworkstaxcalculator.test.randomTrade
import org.apache.commons.lang3.RandomUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean

@SpringBootTest(classes = [])
class FiscalYearTest @Autowired constructor(
    @MockBean private val exchangeRate: ExchangeRate
) {
    @Test
    fun expired() {
        // Given
        val premium = RandomUtils.nextFloat(1.0F, 100.0F)
        val stoTx = randomTrade().copy(value = premium)
        withRateUsdToEur()
        val sut = FiscalYear(exchangeRate, 2021)

        // When
        sut.onPositionOpened(OptionSellToOpenEvent(stoTx))

        // Then
        assertThat(sut.profitAndLoss).isEqualTo(
            ProfitAndLoss(
            profit = premium * 2,
            loss = 0.0F
        )
        )
    }

    @ParameterizedTest
    @ValueSource(floats = [1.0f, -1.0f])
    fun closed(netProfit: Float) {
        // Given
        val premium = RandomUtils.nextFloat(1.0F, 100.0F)
        val stoTx = randomTrade().copy(value = premium, quantity = 1)
        val btcTx = randomTrade().copy(value = -premium + netProfit, quantity = 1)
        withRateUsdToEur()
        val sut = FiscalYear(exchangeRate, 2021)

        // When
        sut.onPositionOpened(OptionSellToOpenEvent(stoTx))
        sut.onPositionClosed(OptionBuyToCloseEvent(stoTx, btcTx))

        // Then
        val expectedNetProfitEur = netProfit * 2
        val expecteProfit = if(netProfit >= 0) expectedNetProfitEur else 0.0F
        val expecteLoss = if(netProfit >= 0) 0.0F else -expectedNetProfitEur
        assertThat(sut.profitAndLoss).isEqualTo(
            ProfitAndLoss(
            profit = expecteProfit,
            loss = expecteLoss
        )
        )
    }

    companion object {
        private const val USD_EUR_EXCHANGE_RATE = 2.0F
    }

    private fun withRateUsdToEur() {
        // For test use exchange rate USD to EUR of 2
        whenever(exchangeRate.usdToEur(any())).thenAnswer {
            val profit: Profit = it.getArgument<Any>(0) as Profit
            profit.value * USD_EUR_EXCHANGE_RATE
        }
    }
}
