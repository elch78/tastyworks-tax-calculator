package com.elchworks.tastyworkstaxcalculator.test

import com.elchworks.tastyworkstaxcalculator.ApplicationRunner
import com.elchworks.tastyworkstaxcalculator.portfolio.NewTransactionEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.Portfolio
import com.elchworks.tastyworkstaxcalculator.transactions.Transaction
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.cucumber.spring.CucumberContextConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.context.bean.override.mockito.MockitoBean

@CucumberContextConfiguration
@SpringBootTest
class StepDefinitions @Autowired constructor(
    // mocked to prevent it from running
    @MockitoBean
    private val application: ApplicationRunner,
    private val eventPublisher: ApplicationEventPublisher,
    private val portfolio: Portfolio
){


    @When("Sell option {string}")
    fun sellOption(optionDescription: String) {
        publishTx(optionStoTx(optionDescription))
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

    fun publishTx(tx: Transaction) {
        eventPublisher.publishEvent(NewTransactionEvent(tx))
    }


    companion object {
    }
}