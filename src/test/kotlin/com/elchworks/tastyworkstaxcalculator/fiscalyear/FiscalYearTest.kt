package com.elchworks.tastyworkstaxcalculator.fiscalyear

import com.elchworks.tastyworkstaxcalculator.*
import com.elchworks.tastyworkstaxcalculator.convert.CurrencyExchange
import com.elchworks.tastyworkstaxcalculator.convert.ExchangeRateRepository
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionBuyToCloseEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionSellToOpenEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.stock.StockSellToCloseEvent
import com.elchworks.tastyworkstaxcalculator.test.randomOptionTrade
import com.elchworks.tastyworkstaxcalculator.test.randomStockTrade
import com.elchworks.tastyworkstaxcalculator.test.randomUsdAmount
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
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.Year
import java.util.stream.Stream
import javax.money.MonetaryAmount

@SpringBootTest(classes = [CurrencyExchange::class])
class FiscalYearTest @Autowired constructor(
    private val currencyExchange: CurrencyExchange
) {
    @MockitoBean
    private lateinit var exchangeRateRepository: ExchangeRateRepository

    private val sut by lazy { FiscalYear(currencyExchange, Year.of(2021)) }

    @Test
    fun optionExpired() {
        // Given
        val premium = randomUsdAmount(1f, 100f)
        val stoTx = randomOptionTrade().copy(value = premium, averagePrice = premium.divide(100))
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
        val premium = usd(5)
        val buyPrice = premium.negate() + netProfit
        val stoTx = randomOptionTrade().copy(value = premium, averagePrice = premium.divide(100), quantity = 1)
        val btcTx = randomOptionTrade().copy(value = buyPrice, averagePrice = buyPrice.divide(100), quantity = 1)
        withRateUsdToEur()

        // When
        sut.onOptionPositionOpened(OptionSellToOpenEvent(stoTx))
        sut.onOptionPositionClosed(OptionBuyToCloseEvent(stoTx, btcTx, 1))

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
        private val USD_EUR_EXCHANGE_RATE = BigDecimal("2.0")

        @JvmStatic
        fun optionClosed() = Stream.of(
            Arguments.of(usd(1)),
            Arguments.of(usd(-1)),
            Arguments.of(usd(0)),
        )
    }

    private fun withRateUsdToEur() {
        whenever(exchangeRateRepository.dailyRateUsdToEur(any()))
            .thenReturn(USD_EUR_EXCHANGE_RATE)
    }
}
