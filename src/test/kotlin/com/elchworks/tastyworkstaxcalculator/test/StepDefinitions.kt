package com.elchworks.tastyworkstaxcalculator.test

import com.elchworks.tastyworkstaxcalculator.ApplicationRunner
import com.elchworks.tastyworkstaxcalculator.convert.ExchangeRateRepository
import com.elchworks.tastyworkstaxcalculator.eur
import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYearRepository
import com.elchworks.tastyworkstaxcalculator.fiscalyear.ProfitsSummary
import com.elchworks.tastyworkstaxcalculator.portfolio.NewTransactionEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.Portfolio
import com.elchworks.tastyworkstaxcalculator.transactions.Action
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_CLOSE
import com.elchworks.tastyworkstaxcalculator.transactions.Transaction
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.cucumber.spring.CucumberContextConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@CucumberContextConfiguration
@SpringBootTest
class StepDefinitions @Autowired constructor(
    // mocked to prevent it from running
    @MockitoBean private val application: ApplicationRunner,
    private val eventPublisher: ApplicationEventPublisher,
    private val portfolio: Portfolio,
    private val fiscalYearRepository: FiscalYearRepository,
    @MockitoBean private val exchangeRateRepository: ExchangeRateRepository,
){
    @Before
    fun before() {
        portfolio.reset()
        fiscalYearRepository.reset()
    }

    @Given("Exchange rate on {string} is {string} USD to EUR")
    fun givenExchangeRate(date: String, rate: String) {

        whenever(exchangeRateRepository.monthlyRateUsdToEur(date.toLocalDate()))
            .thenReturn(BigDecimal(rate))
    }

    @Given("Fixed exchange rate of {string} USD to EUR")
    fun givenExchangeRate(rate: String) {

        whenever(exchangeRateRepository.monthlyRateUsdToEur(any()))
            .thenReturn(BigDecimal(rate))
    }

    @When("Sell option {string} on {string}")
    fun sellOption(optionDescription: String, date: String) {

        publishTx(optionStoTx(optionDescription).copy(date = date.toLocalDate().toInstant()))
    }

    @When("Sell stock {int} {string} on {string} average price: {string}")
    fun sellStock(quantity: Int, symbol: String, date: String, price: String) {

        publishTx(randomStockTrade().copy(
            symbol = symbol,
            quantity = quantity,
            action = SELL_TO_CLOSE,
            averagePrice = price.toUsd(),
            date = date.toLocalDate().toInstant())
        )
    }


    @When("Assignment {string}")
    fun assignOption(optionDescription: String) {
        publishTx(optionAssignment(optionDescription))
        publishTx(assignmentStockTrade(optionDescription))
    }

    @Then("Portfolio should have a stock position for symbol {string} with quantity {int}")
    fun portfolioHasPosition(symbol: String, quantity: Int) {
        val positions = portfolio.getPositions(symbol)!!
        assertThat(positions).hasSize(1)
        assertThat(positions.peek().quantity()).isEqualTo(quantity)

    }

    @Then("Profits for fiscal year {int} should be options profits {double} losses {double} stocks {double}")
    fun portfolioHasPosition(year: Integer, profitsFromOptions: Double, lossesFromOptions: Double, profitsFromStocks: Double) {
        val fiscalYear = fiscalYearRepository.getFiscalYear(Year.of(year.toInt()))
        assertThat(fiscalYear.profits()).isEqualTo(
            ProfitsSummary(
                profitsFromOptions = eur(profitsFromOptions),
                lossesFromOptions = eur(lossesFromOptions),
                profitsFromStocks = eur(profitsFromStocks)
            ))
    }


    fun publishTx(tx: Transaction) {
        eventPublisher.publishEvent(NewTransactionEvent(tx))
    }


    companion object {
    }
}