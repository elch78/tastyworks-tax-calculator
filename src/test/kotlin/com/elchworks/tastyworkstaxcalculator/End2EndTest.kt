@file:Suppress("UNUSED_EXPRESSION")

package com.elchworks.tastyworkstaxcalculator

import com.elchworks.tastyworkstaxcalculator.convert.ExchangeRateRepository
import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYearRepository
import com.elchworks.tastyworkstaxcalculator.fiscalyear.ProfitsSummary
import com.elchworks.tastyworkstaxcalculator.test.*
import com.elchworks.tastyworkstaxcalculator.transactions.Action.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.annotation.DirtiesContext
import java.math.BigDecimal
import java.math.BigDecimal.ONE
import java.time.Instant
import java.time.Month.*
import java.time.ZoneId
import java.util.stream.Stream

@SpringBootTest
// Beans are stateful
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class End2EndTest @Autowired constructor(
    // mocked to prevent it from running
    @MockBean private val application: ApplicationRunner,
    private val fiscalYearRepository: FiscalYearRepository,
    private val scenario: TestScenario,
    @MockBean private val exchangeRateRepository: ExchangeRateRepository
) {
    @Test
    fun optionPositionClosedSameYearWithoutProfitSameExchangeRate() {
        // Given
        val value = usd(5)
        val stoTx = randomOptionTrade().copy(
            randomDate(YEAR_2021, JANUARY),
            action = SELL_TO_OPEN,
            symbol = SYMBOL,
            averagePrice = value,
            value = value,
        )
        val btcTx = stoTx.copy(
            action = BUY_TO_CLOSE,
            averagePrice = value.negate(),
            value = value.negate(),
        )
        withFixedExchangeRate()

        // When
        scenario.publishTx(stoTx)
        scenario.publishTx(btcTx)

        // Then
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(eur(0), eur(0), eur(0)))
    }

    @Test
    fun optionPositionClosedSameYearWithLossDueToExchangeRate() {
        // Given
        val sellDate = randomDate(YEAR_2021, JANUARY)
        val buyDate = randomDate(YEAR_2021, FEBRUARY)
        val stoTx = randomOptionTrade().copy(
            date = sellDate,
            action = SELL_TO_OPEN,
            symbol = SYMBOL,
            value = usd(SELL_VALUE_USD),
            averagePrice = usd(SELL_VALUE_USD),
        )
        val btcTx = stoTx.copy(
            date = buyDate,
            action = BUY_TO_CLOSE,
            value = usd(-SELL_VALUE_USD),
            averagePrice = usd(-SELL_VALUE_USD),
        )
        withExchangeRate(sellDate, ONE)
        withExchangeRate(buyDate, TWO)

        // When
        scenario.publishTx(stoTx)
        scenario.publishTx(btcTx)

        // Then loss due to different exchange rate
        val lossesFromOptions = eur(SELL_VALUE_USD)
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(eur(0), lossesFromOptions, eur(0)))
    }

    @Test
    fun optionPositionClosedSameYearWithProfitDueToExchangeRate() {
        // Given
        val sellDate = randomDate(YEAR_2021, JANUARY)
        val buyDate = randomDate(YEAR_2021, FEBRUARY)
        val stoTx = randomOptionTrade().copy(
            date = sellDate,
            action = SELL_TO_OPEN,
            symbol = SYMBOL,
            averagePrice = usd(SELL_VALUE_USD),
            value = usd(SELL_VALUE_USD),
        )
        val btcTx = stoTx.copy(
            date = buyDate,
            action = BUY_TO_CLOSE,
            averagePrice = usd(-SELL_VALUE_USD),
            value = usd(-SELL_VALUE_USD),
        )
        withExchangeRate(sellDate, TWO)
        withExchangeRate(buyDate, ONE)

        // When
        scenario.publishTx(stoTx)
        scenario.publishTx(btcTx)

        // Then loss due to different exchange rate
        val profitsFromOptions = eur(SELL_VALUE_USD)
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(profitsFromOptions, eur(0), eur(0)))
    }

    @Test
    fun optionPositionClosedDifferentYearWithoutProfitSameExchangeRate() {
        // Given
        val stoTx = randomOptionTrade().copy(
            date = randomDate(YEAR_2021, DECEMBER),
            action = SELL_TO_OPEN,
            symbol = SYMBOL,
            averagePrice = usd(SELL_VALUE_USD),
            value = usd(SELL_VALUE_USD),
        )
        val btcTx = stoTx.copy(
            date = randomDate(YEAR_2022, JANUARY),
            action = BUY_TO_CLOSE,
            averagePrice = usd(-BUY_VALUE_USD),
            value = usd(-BUY_VALUE_USD),
        )
        withFixedExchangeRate()

        // When
        scenario.publishTx(stoTx)
        scenario.publishTx(btcTx)

        // Then sell value is profit for 2021 and buy value is a loss for 2022
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(eur(SELL_VALUE_EUR), eur(0), eur(0)))
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2022).profits())
            .isEqualTo(ProfitsSummary(eur(0), eur(BUY_VALUE_EUR), eur(0)))
    }

    // TODO test partial close with loss and different year ...
    //  btc tx that consumes more than one sto tx. e.g. sell 1 + sell 2 + buy 2

    @Test
    fun optionPostionClosedPartiallySameYear() {
        val sellPrice = usd(2)
        val buyPrice = usd(-2)
        // Given stoTx with quantity 2
        val stoTx = randomOptionTrade().copy(
            date = randomDate(YEAR_2021, JANUARY),
            action = SELL_TO_OPEN,
            symbol = SYMBOL,
            value = sellPrice * 2,
            averagePrice = sellPrice,
            quantity = 2
        )
        // btcTx with quantity 1
        val btcTx = stoTx.copy(
            date = randomDate(YEAR_2021, FEBRUARY),
            action = BUY_TO_CLOSE,
            symbol = SYMBOL,
            value = buyPrice,
            averagePrice = buyPrice,
            quantity = 1
        )
        withFixedExchangeRate()

        // When
        scenario.publishTx(stoTx)

        // Then profit contains premium of two options
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(eur(8), eur(0), eur(0)))

        // When
        scenario.publishTx(btcTx)

        // Then only one option is sold. Premium of one option is left
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(eur(4), eur(0), eur(0)))

        // When another btc tx with amount 1
        scenario.publishTx(btcTx)

        // Then the second option is closed. No profit left
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(eur(0), eur(0), eur(0)))
    }

    @Test
    fun simpleAssigment() {
        // Given
        val callOrPut = "PUT"
        val stoTx = defaultOptionStoTx().copy(
            callOrPut = callOrPut
        )
        val assignmentTx = defaultAssignment().copy(
            callOrPut = callOrPut
        )
        withFixedExchangeRate()

        // When
        scenario.publishTx(stoTx)
        scenario.publishTx(assignmentTx)

        // Then
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(eur(SELL_VALUE_EUR), eur(0), eur(0)))
    }

    @ParameterizedTest
    @MethodSource
    fun simpleAssignmentPutAndCall(profitPerStock: BigDecimal) {
        // Given
        val premiumPut = randomUsdAmount()
        val premiumCall = randomUsdAmount()
        val stockBuyPrice = randomUsdAmount()
        val stockSellPrice = stockBuyPrice + usd(profitPerStock)
        withFixedExchangeRate()

        // When
        scenario.assignedPut(premium = premiumPut, strikePrice = stockBuyPrice)
        scenario.assignedCall(premium = premiumCall, strikePrice = stockSellPrice)

        // Then
        val expectedStockProfit = profitPerStock * BigDecimal("100") * EXCHANGE_RATE
        val expectedProfitFromOptions = ((premiumPut + premiumCall) * EXCHANGE_RATE).toEur()
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(expectedProfitFromOptions, eur(0), eur(expectedStockProfit)))
    }

    @Test
    fun reverseSplit() {
        // Given
        withFixedExchangeRate()

        // When
        // two assignments. Average price 15
        scenario.assignedPut(premium = ZERO_USD, strikePrice = usd(10.0))
        scenario.assignedPut(premium = ZERO_USD, strikePrice = usd(20.0))

        // Reverse split 20:1
        // new average price: 300
        scenario.reverseSplit(originalQuantity = 200, newQuantity = 10)

        // STC 5
        scenario.sellStock(quantity = 5, price = usd(400.0))

        // Then
        // 5 stocks sold, 100 USD (200 EUR) profit per stock
        val expectedProfitFromStocks = eur(1000.00)
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(eur(0.0), eur(0), expectedProfitFromStocks))
    }

    private fun withFixedExchangeRate() {
        whenever(exchangeRateRepository.monthlyRateUsdToEur(any())).thenReturn(EXCHANGE_RATE)
    }

    private fun withExchangeRate(date: Instant, exchangeRate: BigDecimal) {
        val localDate = date.atZone(ZoneId.of("CET")).toLocalDate()
        whenever(exchangeRateRepository.monthlyRateUsdToEur(eq(localDate)))
            .thenReturn(exchangeRate)
    }

    companion object {


        @JvmStatic
        fun simpleAssignmentPutAndCall() = Stream.of(
            Arguments.of(BigDecimal("10.0")),
            Arguments.of(BigDecimal("-10.0"))
        )
    }

}

