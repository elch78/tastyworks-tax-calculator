@file:Suppress("UNUSED_EXPRESSION")

package com.elchworks.tastyworkstaxcalculator

import com.elchworks.tastyworkstaxcalculator.convert.ExchangeRateRepository
import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYearRepository
import com.elchworks.tastyworkstaxcalculator.fiscalyear.ProfitsSummary
import com.elchworks.tastyworkstaxcalculator.positions.NewTransactionEvent
import com.elchworks.tastyworkstaxcalculator.positions.option.OptionPositionStatus.ASSIGNED
import com.elchworks.tastyworkstaxcalculator.test.randomBigDecimal
import com.elchworks.tastyworkstaxcalculator.test.randomDate
import com.elchworks.tastyworkstaxcalculator.test.randomOptionRemoval
import com.elchworks.tastyworkstaxcalculator.test.randomOptionTrade
import com.elchworks.tastyworkstaxcalculator.test.randomStockTrade
import com.elchworks.tastyworkstaxcalculator.test.randomString
import com.elchworks.tastyworkstaxcalculator.transactions.Action.BUY_TO_CLOSE
import com.elchworks.tastyworkstaxcalculator.transactions.Action.BUY_TO_OPEN
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_CLOSE
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_OPEN
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
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.annotation.DirtiesContext
import java.math.BigDecimal
import java.math.BigDecimal.ONE
import java.time.Instant
import java.time.LocalDate
import java.time.Month.DECEMBER
import java.time.Month.FEBRUARY
import java.time.Month.JANUARY
import java.time.Year
import java.time.ZoneId
import java.util.stream.Stream

@SpringBootTest
// Beans are stateful
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class End2EndTest @Autowired constructor(
    // mocked to prevent it from running
    @MockBean private val application: ApplicationRunner,
    private val eventPublisher: ApplicationEventPublisher,
    private val fiscalYearRepository: FiscalYearRepository,
    @MockBean private val exchangeRateRepository: ExchangeRateRepository
) {
    @Test
    fun optionPositionClosedSameYearWithoutProfitSameExchangeRate() {
        // Given
        val value = usd(5)
        val stoTx = randomOptionTrade().copy(
            randomDate(YEAR_2021, JANUARY),
            action = SELL_TO_OPEN,
            rootSymbol = SYMBOL,
            value = value
        )
        val btcTx = stoTx.copy(
            action = BUY_TO_CLOSE,
            value = value.negate()
        )
        withFixedExchangeRate()

        // When
        eventPublisher.publishEvent(NewTransactionEvent(stoTx))
        eventPublisher.publishEvent(NewTransactionEvent(btcTx))

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
            rootSymbol = SYMBOL,
            value = usd(SELL_VALUE_USD)
        )
        val btcTx = stoTx.copy(
            date = buyDate,
            action = BUY_TO_CLOSE,
            value = usd(-SELL_VALUE_USD)
        )
        withExchangeRate(sellDate, ONE)
        withExchangeRate(buyDate, TWO)

        // When
        eventPublisher.publishEvent(NewTransactionEvent(stoTx))
        eventPublisher.publishEvent(NewTransactionEvent(btcTx))

        // Then loss due to different exchange rate
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(eur(0), eur(SELL_VALUE_USD), eur(0)))
    }

    @Test
    fun optionPositionClosedSameYearWithProfitDueToExchangeRate() {
        // Given
        val sellDate = randomDate(YEAR_2021, JANUARY)
        val buyDate = randomDate(YEAR_2021, FEBRUARY)
        val stoTx = randomOptionTrade().copy(
            date = sellDate,
            action = SELL_TO_OPEN,
            rootSymbol = SYMBOL,
            value = usd(SELL_VALUE_USD)
        )
        val btcTx = stoTx.copy(
            date = buyDate,
            action = BUY_TO_CLOSE,
            value = usd(-SELL_VALUE_USD)
        )
        withExchangeRate(sellDate, TWO)
        withExchangeRate(buyDate, ONE)

        // When
        eventPublisher.publishEvent(NewTransactionEvent(stoTx))
        eventPublisher.publishEvent(NewTransactionEvent(btcTx))

        // Then loss due to different exchange rate
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(eur(SELL_VALUE_USD), eur(0), eur(0)))
    }

    @Test
    fun optionPositionClosedDifferentYearWithoutProfitSameExchangeRate() {
        // Given
        val stoTx = randomOptionTrade().copy(
            date = randomDate(YEAR_2021, DECEMBER),
            action = SELL_TO_OPEN,
            rootSymbol = SYMBOL,
            value = usd(SELL_VALUE_USD)
        )
        val btcTx = stoTx.copy(
            date = randomDate(YEAR_2022, JANUARY),
            action = BUY_TO_CLOSE,
            value = usd(-BUY_VALUE_USD)
        )
        withFixedExchangeRate()

        // When
        eventPublisher.publishEvent(NewTransactionEvent(stoTx))
        eventPublisher.publishEvent(NewTransactionEvent(btcTx))

        // Then sell value is profit for 2021 and buy value is a loss for 2022
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(eur(SELL_VALUE_EUR), eur(0), eur(0)))
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2022).profits())
            .isEqualTo(ProfitsSummary(eur(0), eur(BUY_VALUE_EUR), eur(0)))
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
        eventPublisher.publishEvent(NewTransactionEvent(stoTx))
        eventPublisher.publishEvent(NewTransactionEvent(assignmentTx))

        // Then
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(eur(SELL_VALUE_EUR), eur(0), eur(0)))
    }

    @ParameterizedTest
    @MethodSource
    fun simpleAssignmentPutAndCall(profitPerStock: BigDecimal) {
        // Given
        val premiumPut = randomBigDecimal()
        val premiumCall = randomBigDecimal()
        val stockBuyPrice = randomBigDecimal()
        val stockSellPrice = stockBuyPrice + profitPerStock

        val stoTxPut = defaultOptionStoTx().copy(
            callOrPut = "PUT",
            value = usd(premiumPut),
        )
        val assignmentPut = defaultAssignment().copy(
            callOrPut = "PUT",
        )
        val stockBtoTx = defaultStockTrade().copy(
            action = BUY_TO_OPEN,
            averagePrice = usd(-stockBuyPrice),
        )
        val stoTxCall = defaultOptionStoTx().copy(
            callOrPut = "CALL",
            value = usd(premiumCall)
        )
        val assignmentCall = defaultAssignment().copy(
            callOrPut = "CALL",
        )
        val stockStcTx = defaultStockTrade().copy(
            action = SELL_TO_CLOSE,
            averagePrice = usd(stockSellPrice)
        )
        withFixedExchangeRate()

        // When
        eventPublisher.publishEvent(NewTransactionEvent(stoTxPut))
        eventPublisher.publishEvent(NewTransactionEvent(assignmentPut))
        eventPublisher.publishEvent(NewTransactionEvent(stockBtoTx))
        eventPublisher.publishEvent(NewTransactionEvent(stoTxCall))
        eventPublisher.publishEvent(NewTransactionEvent(assignmentCall))
        eventPublisher.publishEvent(NewTransactionEvent(stockStcTx))

        // Then
        val expectedStockProfit = profitPerStock * BigDecimal("100") * EXCHANGE_RATE
        val expectedProfitFromOptions = (premiumPut + premiumCall) * EXCHANGE_RATE
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(eur(expectedProfitFromOptions), eur(0), eur(expectedStockProfit)))
    }

    private fun defaultStockTrade() = randomStockTrade().copy(
        symbol = SYMBOL,
        action = BUY_TO_OPEN,
        quantity = 100,
        averagePrice = usd(STRIKE_PRICE)
    )

    private fun withFixedExchangeRate() {
        whenever(exchangeRateRepository.monthlyRateUsdToEur(any())).thenReturn(EXCHANGE_RATE)
    }

    private fun withExchangeRate(date: Instant, exchangeRate: BigDecimal) {
        val localDate = date.atZone(ZoneId.of("CET")).toLocalDate()
        whenever(exchangeRateRepository.monthlyRateUsdToEur(eq(localDate)))
            .thenReturn(exchangeRate)
    }

    private fun defaultAssignment() = randomOptionRemoval().copy(
        date = randomDate(YEAR_2021, FEBRUARY),
        rootSymbol = SYMBOL,
        status = ASSIGNED,
        callOrPut = "PUT",
        strikePrice = usd(STRIKE_PRICE),
        expirationDate = EXPIRATION_DATE
    )

    private fun defaultOptionStoTx() = randomOptionTrade().copy(
        date = randomDate(YEAR_2021, JANUARY),
        action = SELL_TO_OPEN,
        rootSymbol = SYMBOL,
        value = usd(SELL_VALUE_USD),
        callOrPut = "PUT",
        strikePrice = usd(STRIKE_PRICE),
        expirationDate = EXPIRATION_DATE,
        commissions = usd(COMMISSIONS)
    )

    companion object {
        private val TWO = BigDecimal("2.0")
        private val YEAR_2021 = Year.of(2021)
        private val YEAR_2022 = Year.of(2022)
        private val SYMBOL = randomString("symbol")
        private val EXCHANGE_RATE = TWO
        private val SELL_VALUE_USD = randomBigDecimal()
        private val SELL_VALUE_EUR = SELL_VALUE_USD * EXCHANGE_RATE
        private val BUY_VALUE_USD = randomBigDecimal()
        private val BUY_VALUE_EUR = BUY_VALUE_USD * EXCHANGE_RATE
        private val STRIKE_PRICE = randomBigDecimal()
        private val COMMISSIONS = randomBigDecimal()
        private val FEE = randomBigDecimal()
        private val EXPIRATION_DATE = LocalDate.now()

        @JvmStatic
        fun simpleAssignmentPutAndCall() = Stream.of(
            Arguments.of(BigDecimal("10.0")),
            Arguments.of(BigDecimal("-10.0"))
        )
    }
}
