package com.elchworks.tastyworkstaxcalculator.test.positions

import com.elchworks.tastyworkstaxcalculator.ExchangeRate
import com.elchworks.tastyworkstaxcalculator.eur
import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYear
import com.elchworks.tastyworkstaxcalculator.plus
import com.elchworks.tastyworkstaxcalculator.positions.OptionBuyToCloseEvent
import com.elchworks.tastyworkstaxcalculator.positions.OptionSellToOpenEvent
import com.elchworks.tastyworkstaxcalculator.positions.Profit
import com.elchworks.tastyworkstaxcalculator.positions.ProfitAndLoss
import com.elchworks.tastyworkstaxcalculator.positions.StockSellToCloseEvent
import com.elchworks.tastyworkstaxcalculator.test.randomOptionTrade
import com.elchworks.tastyworkstaxcalculator.test.randomStockTrade
import com.elchworks.tastyworkstaxcalculator.test.randomUsdAmount
import com.elchworks.tastyworkstaxcalculator.times
import com.elchworks.tastyworkstaxcalculator.toEur
import com.elchworks.tastyworkstaxcalculator.usd
import org.apache.commons.lang3.RandomUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.stream.Stream
import javax.money.MonetaryAmount

class FiscalYearTest {
    private val exchangeRate: ExchangeRate = mock()
    private val sut = FiscalYear(exchangeRate, 2021)

    @Test
    fun optionExpired() {
        // Given
        val premium = randomUsdAmount(1f, 100f)
        val stoTx = randomOptionTrade().copy(value = premium)
        withRateUsdToEur()

        // When
        sut.onOptionPositionOpened(OptionSellToOpenEvent(stoTx))

        // Then
        assertThat(sut.profitAndLossFromOptions).isEqualTo(
            ProfitAndLoss(
            profit = (premium * 2).toEur(),
            loss = eur(0)
        )
        )
    }

    @ParameterizedTest
    @MethodSource
    fun optionClosed(netProfit: MonetaryAmount) {
        // Given
        val premium = randomUsdAmount()
        val stoTx = randomOptionTrade().copy(value = premium, quantity = 1)
        val btcTx = randomOptionTrade().copy(value = premium.negate() + netProfit, quantity = 1)
        withRateUsdToEur()

        // When
        sut.onOptionPositionOpened(OptionSellToOpenEvent(stoTx))
        sut.onOptionPositionClosed(OptionBuyToCloseEvent(stoTx, btcTx))

        // Then
        val expectedNetProfitEur = (netProfit * 2).toEur()
        val expecteProfit = if(netProfit.isPositive) expectedNetProfitEur else eur(0)
        val expecteLoss = if(netProfit.isPositive) eur(0) else expectedNetProfitEur.negate()
        assertThat(sut.profitAndLossFromOptions).isEqualTo(
            ProfitAndLoss(
            profit = expecteProfit,
            loss = expecteLoss
        )
        )
    }

    @Test
    fun test() {
        // Given
        val profit = randomUsdAmount(1.0f, 10.0f)
        val buyPrice = randomUsdAmount(1.0f, 100.0f)
        val sellPrice = buyPrice + profit
        val btoTx = randomStockTrade().copy(
            averagePrice = buyPrice
        )
        val stcTx = randomStockTrade().copy(
            averagePrice = sellPrice
        )
        val quantitySold = RandomUtils.nextInt(1, 10)
        val event = StockSellToCloseEvent(btoTx, stcTx, quantitySold)
        withRateUsdToEur()

        // When
        sut.onStockPositionClosed(event)

        // Then
        assertThat(sut.profitAndLossFromStocks).isEqualTo((profit * quantitySold * USD_EUR_EXCHANGE_RATE).toEur())
    }

    companion object {
        private const val USD_EUR_EXCHANGE_RATE = 2.0F

        @JvmStatic
        fun optionClosed() = Stream.of(
            Arguments.of(usd(1)),
            Arguments.of(usd(-1)),
        )
    }

    private fun withRateUsdToEur() {
        // For test use exchange rate USD to EUR of 2
        whenever(exchangeRate.usdToEur(any())).thenAnswer {
            val profit: Profit = it.getArgument<Any>(0) as Profit
            eur((profit.value * USD_EUR_EXCHANGE_RATE).number)
        }
    }
}
