@file:Suppress("UNUSED_EXPRESSION")

package com.elchworks.tastyworkstaxcalculator

import com.elchworks.tastyworkstaxcalculator.convert.ExchangeRateRepository
import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYearRepository
import com.elchworks.tastyworkstaxcalculator.fiscalyear.ProfitsSummary
import com.elchworks.tastyworkstaxcalculator.test.*
import com.elchworks.tastyworkstaxcalculator.transactions.Action.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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

    // TODO test partial close with loss and different year ...
    //  btc tx that consumes more than one sto tx. e.g. sell 1 + sell 2 + buy 2


    private fun withFixedExchangeRate() {
        whenever(exchangeRateRepository.dailyRateUsdToEur(any())).thenReturn(EXCHANGE_RATE)
    }

    private fun withExchangeRate(date: Instant, exchangeRate: BigDecimal) {
        val localDate = date.atZone(ZoneId.of("CET")).toLocalDate()
        whenever(exchangeRateRepository.dailyRateUsdToEur(eq(localDate)))
            .thenReturn(exchangeRate)
    }
}

