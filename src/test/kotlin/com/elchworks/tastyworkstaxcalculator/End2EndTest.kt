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
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.ApplicationEventPublisher
import java.time.Month.JANUARY
import java.time.Year

@SpringBootTest
class End2EndTest @Autowired constructor(
    // mocked to prevent it from running
    @MockBean private val application: ApplicationRunner,
    private val eventPublisher: ApplicationEventPublisher,
    private val fiscalYearRepository: FiscalYearRepository,
    @MockBean private val exchangeRateRepository: ExchangeRateRepository
) {
    @Test
    fun optionPositionClosedSameYearWithoutProfit() {
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
        whenever(exchangeRateRepository.monthlyRateUsdToEur(any())).thenReturn(1.0f)

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
        val stoTx = randomOptionTrade().copy(
            randomDate(YEAR_2021, JANUARY),
            action = SELL_TO_OPEN,
            rootSymbol = SYMBOL,
            value = usd(VALUE)
        )
        val btcTx = stoTx.copy(
            action = BUY_TO_CLOSE,
            value = usd(-VALUE)
        )
        whenever(exchangeRateRepository.monthlyRateUsdToEur(any()))
            .thenReturn(1.0f, 2.0f)

        // When
        eventPublisher.publishEvent(NewTransactionEvent(stoTx))
        eventPublisher.publishEvent(NewTransactionEvent(btcTx))

        // Then LOSS equal to value due to exchange rate
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(eur(0), eur(VALUE), eur(0)))
    }


    companion object {
        private val YEAR_2021 = Year.of(2021)
        private val SYMBOL = randomString("symbol")
        private val VALUE = RandomUtils.nextFloat()
    }
}
