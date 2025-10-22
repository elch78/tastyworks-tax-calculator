package com.elchworks.tastyworkstaxcalculator.test

import com.elchworks.tastyworkstaxcalculator.ApplicationRunner
import com.elchworks.tastyworkstaxcalculator.convert.ExchangeRateRepository
import com.elchworks.tastyworkstaxcalculator.eur
import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYearRepository
import com.elchworks.tastyworkstaxcalculator.fiscalyear.ProfitsSummary
import com.elchworks.tastyworkstaxcalculator.portfolio.NewTransactionEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.Portfolio
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionShortPosition
import com.elchworks.tastyworkstaxcalculator.snapshot.SnapshotDeserializer
import com.elchworks.tastyworkstaxcalculator.snapshot.SnapshotFileService
import com.elchworks.tastyworkstaxcalculator.snapshot.StateSnapshot
import com.elchworks.tastyworkstaxcalculator.snapshot.StateSnapshotManager
import com.elchworks.tastyworkstaxcalculator.transactions.Action.BUY_TO_OPEN
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_CLOSE
import com.elchworks.tastyworkstaxcalculator.transactions.Transaction
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.cucumber.spring.CucumberContextConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.Month
import java.time.Year
import java.time.ZoneId

@CucumberContextConfiguration
@SpringBootTest
class StepDefinitions @Autowired constructor(
    // mocked to prevent it from running
    @MockitoBean private val application: ApplicationRunner,
    private val eventPublisher: ApplicationEventPublisher,
    private val portfolio: Portfolio,
    private val fiscalYearRepository: FiscalYearRepository,
    @MockitoBean private val exchangeRateRepository: ExchangeRateRepository,
    private val snapshotDeserializer: SnapshotDeserializer,
    private val snapshotFileService: SnapshotFileService,
    private val stateSnapshotManager: StateSnapshotManager
){
    private var currentSnapshot: StateSnapshot? = null
    private var lastTransactionDate: Instant? = null
    private val testTransactionsDir = File(System.getProperty("java.io.tmpdir"), "test-transactions")
    private val testSnapshotDir = File(testTransactionsDir, "snapshots")
    @Before
    fun before() {
        portfolio.reset()
        fiscalYearRepository.reset()
        stateSnapshotManager.reset()

        // Clean up test directory before each test
        if (testTransactionsDir.exists()) {
            testTransactionsDir.deleteRecursively()
        }
        testTransactionsDir.mkdirs()
    }

    @Given("Exchange rate on {string} is {string} USD to EUR")
    fun givenExchangeRate(date: String, rate: String) {

        whenever(exchangeRateRepository.dailyRateUsdToEur(date.toLocalDate()))
            .thenReturn(BigDecimal(rate))
    }

    @Given("Fixed exchange rate of {string} USD to EUR")
    fun givenExchangeRate(rate: String) {
        whenever(exchangeRateRepository.dailyRateUsdToEur(any()))
            .thenReturn(BigDecimal(rate))
    }

    @When("Sell option {string} on {string}")
    fun sellOption(optionDescription: String, date: String) {
        publishTx(optionStoTx(optionDescription).copy(date = date.toLocalDate().toInstant()))
    }

    @When("Sell option {string} on {string} quantity {int}")
    fun sellOption(optionDescription: String, date: String, quantity: Int) {
        publishTx(optionStoTx(optionDescription, quantity).copy(date = date.toLocalDate().toInstant()))
    }

    @When("Buy option {string} on {string}")
    fun buyOption(optionDescription: String, date: String) {
        publishTx(optionBtcTx(optionDescription).copy(date = date.toLocalDate().toInstant()))
    }

    @When("Buy option {string} on {string} quantity {int}")
    fun buyOption(optionDescription: String, date: String, quantity: Int) {
        publishTx(optionBtcTx(optionDescription, quantity).copy(date = date.toLocalDate().toInstant()))
    }

    @When("Option expires {string} on {string}")
    fun expireOption(optionDescription: String, date: String) {
        publishTx(optionExpiration(optionDescription).copy(date = date.toLocalDate().toInstant()))
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

    @When("Assigned put with premium {string} and strike price {string}")
    fun assignedPut(premium: String, strikePrice: String) {
        publishTx(defaultOptionStoTx().copy(
            callOrPut = "PUT",
            value = premium.toUsd(),
        ))

        publishTx(defaultAssignment().copy(
            callOrPut = "PUT",
            averagePrice = strikePrice.toUsd(),
        ))

        publishTx(defaultStockTrade().copy(
            action = BUY_TO_OPEN,
            averagePrice = strikePrice.toUsd().negate(),
        ))
    }

    @When("Reverse split original quantity {int} new quantity {int}")
    fun reverseSplit(originalQuantity: Int, newQuantity: Int) {
        val splitDate = randomDate(Year.of(2022), Month.FEBRUARY)
        publishTx(defaultReverseSplitTransaction().copy(
            date = splitDate,
            quantity = originalQuantity,
            averagePrice = randomUsdAmount(),
            action = SELL_TO_CLOSE
        ))
        publishTx(defaultReverseSplitTransaction().copy(
            date = splitDate,
            quantity = newQuantity,
            averagePrice = randomUsdAmount(),
            action = BUY_TO_OPEN
        ))
    }

    @When("Sell stock quantity {int} price {string}")
    fun sellStock(quantity: Int, price: String) {
        publishTx(defaultStockTrade().copy(
            action = SELL_TO_CLOSE,
            quantity = quantity,
            averagePrice = price.toUsd()
        ))
    }

    @Then("Portfolio should have a stock position for symbol {string} with quantity {int}")
    fun portfolioHasPosition(symbol: String, quantity: Int) {
        val positions = portfolio.getStockPositions(symbol)
        assertThat(positions).isNotNull
        assertThat(positions).hasSize(1)
        assertThat(positions!!.peek().quantity()).isEqualTo(quantity)
    }

    @Then("Portfolio should have an option position {string} with quantity {int}")
    fun portfolioHasOptionPosition(optionDescription: String, quantity: Int) {
        val attributes = optionDescription.split(" ")
        val optionPositions = portfolio.getOptionPositions(
            attributes.callOrPut(),
            attributes.symbol(),
            attributes.expirationDate(),
            attributes.strikePrice()
        )
        assertThat(optionPositions).isNotNull
        assertThat(optionPositions).hasSize(1)
        assertThat(optionPositions.peek().quantity()).isEqualTo(quantity)
    }

    @Then("Portfolio should have an option position {string} with quantity {int} sold on {string}")
    fun portfolioHasOptionPosition(optionDescription: String, quantity: Int, soldAt: String) {
        val attributes = optionDescription.split(" ")
        val optionPositions = portfolio.getOptionPositions(
            attributes.callOrPut(),
            attributes.symbol(),
            attributes.expirationDate(),
            attributes.strikePrice()
        )
        assertThat(optionPositions).isNotNull

        val position = optionPositions.find { it.soldAt().equals(soldAt.toLocalDate()) }
        assertThat(position).isNotNull
        assertThat(position!!.quantity()).isEqualTo(quantity)
    }

    // returns the local date of the sto transaction
    fun OptionShortPosition.soldAt() = this.stoTx.date.atZone(ZoneId.of("CET")).toLocalDate()

    @Then("Portfolio should have no option position {string}")
    fun portfolioHasNoOptionPosition(optionDescription: String) {
        val attributes = optionDescription.split(" ")
        val optionPositions = portfolio.getOptionPositions(
            attributes.callOrPut(),
            attributes.symbol(),
            attributes.expirationDate(),
            attributes.strikePrice()
        )
        assertThat(optionPositions).isNotNull
        assertThat(optionPositions).isEmpty()
    }

    @Then("Profits for fiscal year {int} should be options profits {double} losses {double} stocks {double}")
    fun thenProfitsAre(year: Integer, profitsFromOptions: Double, lossesFromOptions: Double, profitsFromStocks: Double) {
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
        lastTransactionDate = tx.date
    }

    @When("the snapshot files are deleted")
    fun theSnapshotFilesAreDeleted() {
        if (testSnapshotDir.exists()) {
            testSnapshotDir.deleteRecursively()
        }
    }

    @When("the application is run again")
    fun theApplicationIsRunAgain() {
        portfolio.reset()
        fiscalYearRepository.reset()
        stateSnapshotManager.reset()
        currentSnapshot = null
        lastTransactionDate = null
    }

    @When("a snapshot is created")
    fun aSnapshotIsCreated() {
        require(lastTransactionDate != null) { "No transactions published yet" }

        val snapshot = stateSnapshotManager.createSnapshot(lastTransactionDate!!)
        snapshotFileService.saveSnapshot(snapshot, testTransactionsDir.absolutePath)
        currentSnapshot = snapshot
    }

    @When("the snapshot is loaded from file")
    fun theSnapshotIsLoadedFromFile() {
        currentSnapshot = snapshotFileService.loadLatestSnapshot(testTransactionsDir.absolutePath)
        require(currentSnapshot != null) { "No snapshot file found" }
    }

    @When("the portfolio is restored from the snapshot")
    fun thePortfolioIsRestoredFromTheSnapshot() {
        require(currentSnapshot != null) { "No snapshot created yet" }

        portfolio.reset()
        fiscalYearRepository.reset()

        snapshotDeserializer.restoreState(
            snapshot = currentSnapshot!!,
            portfolio = portfolio,
            fiscalYearRepository = fiscalYearRepository
        )
    }

    @Then("a snapshot file should be created in {string} directory")
    fun aSnapshotFileShouldBeCreatedInDirectory(directory: String) {
        require(lastTransactionDate != null) { "No transactions published yet" }

        val snapshot = stateSnapshotManager.createSnapshot(lastTransactionDate!!)
        snapshotFileService.saveSnapshot(snapshot, testTransactionsDir.absolutePath)

        assertThat(testSnapshotDir.exists()).isTrue()
        assertThat(testSnapshotDir.isDirectory).isTrue()

        val snapshotFiles = testSnapshotDir.listFiles { file ->
            file.isFile && file.name.startsWith("snapshot-") && file.name.endsWith(".json")
        } ?: emptyArray()

        assertThat(snapshotFiles).isNotEmpty
    }

    @Then("a snapshot file should exist")
    fun aSnapshotFileShouldExist() {
        assertThat(testSnapshotDir.exists()).isTrue()
        assertThat(testSnapshotDir.isDirectory).isTrue()

        val snapshotFiles = testSnapshotDir.listFiles { file ->
            file.isFile && file.name.startsWith("snapshot-") && file.name.endsWith(".json")
        } ?: emptyArray()

        assertThat(snapshotFiles).isNotEmpty
    }

    @Then("the snapshot filename should match format {string}")
    fun theSnapshotFilenameShouldMatchFormat(format: String) {
        val snapshotFiles = testSnapshotDir.listFiles { file ->
            file.isFile && file.name.startsWith("snapshot-") && file.name.endsWith(".json")
        }

        assertThat(snapshotFiles).isNotEmpty
        val snapshotFile = snapshotFiles!!.first()

        val regexPattern = format
            .replace(".", "\\.")
            .replace("*", ".*")

        assertThat(snapshotFile.name).matches(regexPattern)
    }

    @Then("the snapshot last transaction date should be {string}")
    fun theSnapshotLastTransactionDateShouldBe(dateString: String) {
        require(currentSnapshot != null) { "No snapshot loaded" }

        val expectedDate = dateString.toLocalDate().toInstant()
        assertThat(currentSnapshot!!.metadata.lastTransactionDate).isEqualTo(expectedDate)
    }

    companion object {
    }
}