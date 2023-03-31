package com.elchworks.tastyworkstaxcalculator.test.fiscalyear

import com.elchworks.tastyworkstaxcalculator.convert.ExchangeRateRepository
import com.elchworks.tastyworkstaxcalculator.convert.currencyExchange
import com.elchworks.tastyworkstaxcalculator.eur
import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYear
import com.elchworks.tastyworkstaxcalculator.fiscalyear.ProfitsSummary
import com.elchworks.tastyworkstaxcalculator.plus
import com.elchworks.tastyworkstaxcalculator.positions.option.OptionBuyToCloseEvent
import com.elchworks.tastyworkstaxcalculator.positions.option.OptionSellToOpenEvent
import com.elchworks.tastyworkstaxcalculator.positions.stock.StockSellToCloseEvent
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
import java.time.Year
import java.util.stream.Stream
import javax.money.MonetaryAmount

class FiscalYearTest {
    private val exchangeRateRepository: ExchangeRateRepository = mock()
    private val currencyExchange: currencyExchange = currencyExchange(exchangeRateRepository)
    private val sut = FiscalYear(currencyExchange, Year.of(2021))

    @Test
    fun optionExpired() {
        // Given
        val premium = randomUsdAmount(1f, 100f)
        val stoTx = randomOptionTrade().copy(value = premium)
        withRateUsdToEur()

        // When
        sut.onOptionPositionOpened(OptionSellToOpenEvent(stoTx))

        // Then
        assertThat(sut.profits()).isEqualTo(
            ProfitsSummary(
                profitsFromOptions = (premium * 2).toEur(),
                lossesFromOptions = eur(0),
                eur(0)
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
        assertThat(sut.profits()).isEqualTo(
            ProfitsSummary(
                profitsFromOptions = expecteProfit,
                lossesFromOptions = expecteLoss,
                profitsFromStocks = eur(0)
        )
        )
    }

    @Test
    fun stockPositionClosed() {
        // Given
        val profit = randomUsdAmount(1.0f, 10.0f)
        val buyPrice = randomUsdAmount(1.0f, 100.0f).negate()
        val sellPrice = buyPrice.negate() + profit
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
        assertThat(sut.profits()).isEqualTo(
            ProfitsSummary(
                profitsFromOptions = eur(0),
                lossesFromOptions = eur(0),
                profitsFromStocks = (profit * quantitySold * USD_EUR_EXCHANGE_RATE).toEur()
            )
        )
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
        whenever(exchangeRateRepository.monthlyRateUsdToEur(any()))
            .thenReturn(USD_EUR_EXCHANGE_RATE)
    }
}
