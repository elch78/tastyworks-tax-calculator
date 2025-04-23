@file:Suppress("UNUSED_EXPRESSION")

package com.elchworks.tastyworkstaxcalculator

import com.elchworks.tastyworkstaxcalculator.convert.ExchangeRateRepository
import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYearRepository
import com.elchworks.tastyworkstaxcalculator.fiscalyear.ProfitsSummary
import com.elchworks.tastyworkstaxcalculator.portfolio.NewTransactionEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionPositionStatus.ASSIGNED
import com.elchworks.tastyworkstaxcalculator.test.randomBigDecimal
import com.elchworks.tastyworkstaxcalculator.test.randomDate
import com.elchworks.tastyworkstaxcalculator.test.randomOptionRemoval
import com.elchworks.tastyworkstaxcalculator.test.randomOptionTrade
import com.elchworks.tastyworkstaxcalculator.test.randomStockTrade
import com.elchworks.tastyworkstaxcalculator.test.randomString
import com.elchworks.tastyworkstaxcalculator.test.randomUsdAmount
import com.elchworks.tastyworkstaxcalculator.transactions.Action.BUY_TO_CLOSE
import com.elchworks.tastyworkstaxcalculator.transactions.Action.BUY_TO_OPEN
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_CLOSE
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_OPEN
import com.elchworks.tastyworkstaxcalculator.transactions.Transaction
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
import java.time.Month
import java.time.Month.DECEMBER
import java.time.Month.FEBRUARY
import java.time.Month.JANUARY
import java.time.Year
import java.time.ZoneId
import java.util.stream.Stream
import kotlin.plus

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
        publishTx(stoTx)
        publishTx(btcTx)

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
        publishTx(stoTx)
        publishTx(btcTx)

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
        publishTx(stoTx)
        publishTx(btcTx)

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
        publishTx(stoTx)
        publishTx(btcTx)

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
        publishTx(stoTx)

        // Then profit contains premium of two options
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(eur(8), eur(0), eur(0)))

        // When
        publishTx(btcTx)

        // Then only one option is sold. Premium of one option is left
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(eur(4), eur(0), eur(0)))

        // When another btc tx with amount 1
        publishTx(btcTx)

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
        publishTx(stoTx)
        publishTx(assignmentTx)

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
        publishTx(stoTxPut)
        publishTx(assignmentPut)
        publishTx(stockBtoTx)
        publishTx(stoTxCall)
        publishTx(assignmentCall)
        publishTx(stockStcTx)

        // Then
        val expectedStockProfit = profitPerStock * BigDecimal("100") * EXCHANGE_RATE
        val expectedProfitFromOptions = (premiumPut + premiumCall) * EXCHANGE_RATE
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(eur(expectedProfitFromOptions), eur(0), eur(expectedStockProfit)))
    }

    @Test
    fun reverseSplit() {
        // Given
        val strikePrice1 = usd(10.0)
        val strikePrice2 = usd(20.0)
        val stockSellPrice = usd(400.0)
        val premiumPut = usd(0.0)
        val splitDate = randomDate(YEAR_2022, Month.FEBRUARY);
//        val stockSellPrice = stockBuyPrice + profitPerStock

        withFixedExchangeRate()

        // When
        // assignments 100@10
        publishTx(
            defaultOptionStoTx().copy(
                callOrPut = "PUT",
                value = premiumPut,
            )
        )
        publishTx(
            defaultAssignment().copy(
                callOrPut = "PUT",
                averagePrice = strikePrice1,
            )
        )
        publishTx(
            defaultStockTrade().copy(
                action = BUY_TO_OPEN,
                averagePrice = strikePrice1.negate(),
            )
        )
        // assignments 100@20
        publishTx(
            defaultOptionStoTx().copy(
                callOrPut = "PUT",
                value = premiumPut,
            )
        )
        publishTx(
            defaultAssignment().copy(
                callOrPut = "PUT",
                averagePrice = strikePrice2,
            )
        )
        publishTx(
            defaultStockTrade().copy(
                action = BUY_TO_OPEN,
                averagePrice = strikePrice2.negate(),
            )
        )

        // Reverse split 20:1
        // new average price: 300
        publishTx(defaultReverseSplitTransaction().copy(
            date = splitDate,
            quantity = 200,
            // price of the reverse split transaction must be ignored. The original
            // buy price of the portfolio has to be retained for the tax calculation.
            averagePrice = randomUsdAmount(),
            action = SELL_TO_CLOSE
        ))
        publishTx(defaultReverseSplitTransaction().copy(
            date = splitDate,
            quantity = 10,
            // price of the reverse split transaction must be ignored. The original
            // buy price of the portfolio has to be retained for the tax calculation.
            averagePrice = randomUsdAmount(),
            action = BUY_TO_OPEN
        ))

        // STC 5
        publishTx(defaultStockTrade().copy(
            action = SELL_TO_CLOSE,
            quantity = 5,
            averagePrice = stockSellPrice
        ))

        // Then
        // 5 stocks sold, 100 USD (200 EUR) profit per stock
        val expectedProfitFromStocks = eur(1000.00)
        assertThat(fiscalYearRepository.getFiscalYear(YEAR_2021).profits())
            .isEqualTo(ProfitsSummary(eur(0.0), eur(0), expectedProfitFromStocks))
    }

    private fun defaultReverseSplitTransaction() =
        defaultStockTrade().copy(
            type = "Reverse Split"
        )

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
        symbol = SYMBOL,
        status = ASSIGNED,
        callOrPut = "PUT",
        strikePrice = usd(STRIKE_PRICE),
        expirationDate = EXPIRATION_DATE
    )

    private fun defaultOptionStoTx() = randomOptionTrade().copy(
        date = randomDate(YEAR_2021, JANUARY),
        action = SELL_TO_OPEN,
        symbol = SYMBOL,
        value = usd(SELL_VALUE_USD),
        callOrPut = "PUT",
        strikePrice = usd(STRIKE_PRICE),
        expirationDate = EXPIRATION_DATE,
        commissions = usd(COMMISSIONS)
    )

    private fun publishTx(stoTxPut: Transaction) {
        eventPublisher.publishEvent(NewTransactionEvent(stoTxPut))
    }

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

