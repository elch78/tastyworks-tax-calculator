@file:Suppress("UNUSED_EXPRESSION")

package com.elchworks.tastyworkstaxcalculator

import com.elchworks.tastyworkstaxcalculator.convert.ExchangeRateRepository
import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYearRepository
import com.elchworks.tastyworkstaxcalculator.fiscalyear.ProfitsSummary
import com.elchworks.tastyworkstaxcalculator.positions.NewTransactionEvent
import com.elchworks.tastyworkstaxcalculator.test.randomDate
import com.elchworks.tastyworkstaxcalculator.test.randomOptionTrade
import com.elchworks.tastyworkstaxcalculator.test.randomString
import com.elchworks.tastyworkstaxcalculator.transactions.Action.BUY_TO_CLOSE
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_OPEN
import org.apache.commons.lang3.RandomUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.annotation.DirtiesContext
import java.time.Instant
import java.time.Month.DECEMBER
import java.time.Month.FEBRUARY
import java.time.Month.JANUARY
import java.time.Year
import java.time.ZoneId

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
    fun optionPositionClosedSameYearWithProfitDueToExchangeRate() {
        // Given
        val sellDate = randomDate(YEAR_2021, JANUARY)
        val buyDate = randomDate(YEAR_2021, FEBRUARY)
        val stoTx = randomOptionTrade().copy(
            date = sellDate,
            action = SELL_TO_OPEN,
            rootSymbol = SYMBOL,
            value = usd(SELL_VALUE)
        )
        val btcTx = stoTx.copy(
            date = buyDate,
            action = BUY_TO_CLOSE,
            value = usd(-SELL_VALUE)
        )
        withExchangeRate(sellDate, 1.0f)
        withExchangeRate(buyDate, 2.0f)

        // When
        eventPublisher.publishEvent(NewTransactionEvent(stoTx))
        eventPublisher.publishEvent(NewTransactionEvent(btcTx))

        // Then loss due to different exchange rate
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(eur(0), eur(SELL_VALUE), eur(0)))
    }

    @Test
    fun optionPositionClosedDifferentYearWithoutProfitSameExchangeRate() {
        // Given
        val stoTx = randomOptionTrade().copy(
            date = randomDate(YEAR_2021, DECEMBER),
            action = SELL_TO_OPEN,
            rootSymbol = SYMBOL,
            value = usd(SELL_VALUE)
        )
        val btcTx = stoTx.copy(
            date = randomDate(YEAR_2022, JANUARY),
            action = BUY_TO_CLOSE,
            value = usd(-BUY_VALUE)
        )
        withFixedExchangeRate()

        // When
        eventPublisher.publishEvent(NewTransactionEvent(stoTx))
        eventPublisher.publishEvent(NewTransactionEvent(btcTx))

        // Then
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(eur(SELL_VALUE), eur(0), eur(0)))
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2022).profits())
            .isEqualTo(ProfitsSummary(eur(0), eur(BUY_VALUE), eur(0)))
    }

    private fun withFixedExchangeRate() {
        whenever(exchangeRateRepository.monthlyRateUsdToEur(any())).thenReturn(1.0f)
    }

    private fun withExchangeRate(date: Instant, exchangeRate: Float) {
        val localDate = date.atZone(ZoneId.of("CET")).toLocalDate()
        whenever(exchangeRateRepository.monthlyRateUsdToEur(eq(localDate)))
            .thenReturn(exchangeRate)
    }


    companion object {
        private val YEAR_2021 = Year.of(2021)
        private val YEAR_2022 = Year.of(2022)
        private val SYMBOL = randomString("symbol")
        private val SELL_VALUE = RandomUtils.nextFloat()
        private val BUY_VALUE = RandomUtils.nextFloat()
    }
}
