# Persistent State Snapshots Implementation Plan

## Overview

Implement persistent state snapshots to allow incremental transaction processing. Users can provide only new transactions instead of complete transaction history, with the application resuming from a saved snapshot of portfolio and fiscal year state.

## Current State Analysis

The application currently:
- Processes all transactions from scratch on every run (ApplicationRunner.kt:21-38)
- Maintains in-memory state in Portfolio (positions) and FiscalYearRepository (profits/losses)
- Uses event-driven architecture where Portfolio publishes events that FiscalYear listens to
- Sorts all transactions chronologically before processing (ApplicationRunner.kt:33)

### Key State to Persist:

**Portfolio State** (Portfolio.kt:26-27):
- `optionPositions: MutableMap<String, Queue<OptionShortPosition>>` - Open option positions with FIFO queues
- `stockPositions: MutableMap<String, Queue<StockPosition>>` - Open stock positions with FIFO queues

**FiscalYear State** (FiscalYear.kt:28-29):
- `profitAndLossFromOptions: ProfitAndLoss` - Option profits/losses per German tax law
- `profitAndLossFromStocks: MonetaryAmount` - Stock profits/losses

**FiscalYearRepository** (FiscalYearRepository.kt:11):
- `fiscalYears: MutableMap<Year, FiscalYear>` - All fiscal year instances

## Desired End State

After implementation:
1. Application saves snapshot after successful processing with filename: `snapshot-YYYY-MM-DD-HHmmss.json`
2. Application loads latest snapshot (if exists) before processing new transactions
3. Chronological validation ensures new transactions are strictly after snapshot's last transaction date
4. Clear error message displayed if validation fails
5. Users can delete snapshots to start fresh

### Verification:
- Run with complete transaction history → snapshot created
- Run again with only new transactions → snapshot loaded, only new transactions processed, results match
- Run with backdated transaction → clear error message, processing stops
- Delete snapshot → full processing resumes

## What We're NOT Doing

- Persisting exchange rate data (always loaded from CSV)
- Handling `splitTransactionCounterpart` edge case (extremely unlikely to snapshot mid-split)
- Snapshot versioning or migration (initial version only)
- Snapshot compression or optimization
- Multiple snapshot files (only latest is used)
- CLI arguments for snapshot location (convention-based: `{transactionsDir}/snapshots/`)

## Implementation Approach

Use JSON serialization with Jackson for human-readable snapshots that are easy to debug and version control. Implement snapshot save/load in dedicated service classes to keep concerns separated. Integrate snapshot lifecycle into ApplicationRunner's existing transaction processing flow.

---

## Phase 1: Snapshot Data Model & Serialization

### Overview
Create data classes for snapshot structure and implement JSON serialization/deserialization for all state types.

### Changes Required:

#### 1. Create Snapshot Data Classes

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/SnapshotModels.kt` (new file)

```kotlin
package com.elchworks.tastyworkstaxcalculator.snapshot

import com.elchworks.tastyworkstaxcalculator.portfolio.ProfitAndLoss
import java.time.Instant
import java.time.Year
import javax.money.MonetaryAmount

data class PortfolioSnapshot(
    val optionPositions: Map<String, List<OptionPositionSnapshot>>,
    val stockPositions: Map<String, List<StockPositionSnapshot>>
)

data class OptionPositionSnapshot(
    val stoTx: OptionTradeSnapshot,
    val quantityLeft: Int
)

data class StockPositionSnapshot(
    val btoTx: StockTransactionSnapshot,
    val quantityLeft: Int
)

data class OptionTradeSnapshot(
    val date: Instant,
    val symbol: String,
    val callOrPut: String,
    val expirationDate: String, // LocalDate as ISO string
    val strikePrice: MonetaryAmountSnapshot,
    val quantity: Int,
    val averagePrice: MonetaryAmountSnapshot,
    val description: String,
    val commissions: MonetaryAmountSnapshot,
    val fees: MonetaryAmountSnapshot
)

data class StockTransactionSnapshot(
    val date: Instant,
    val symbol: String,
    val type: String,
    val value: MonetaryAmountSnapshot,
    val quantity: Int,
    val averagePrice: MonetaryAmountSnapshot,
    val description: String,
    val commissions: MonetaryAmountSnapshot,
    val fees: MonetaryAmountSnapshot
)

data class MonetaryAmountSnapshot(
    val amount: Double,
    val currency: String
)

data class FiscalYearSnapshot(
    val year: Int,
    val profitAndLossFromOptions: ProfitAndLossSnapshot,
    val profitAndLossFromStocks: MonetaryAmountSnapshot
)

data class ProfitAndLossSnapshot(
    val profit: MonetaryAmountSnapshot,
    val loss: MonetaryAmountSnapshot
)

data class StateSnapshot(
    val metadata: SnapshotMetadata,
    val portfolio: PortfolioSnapshot,
    val fiscalYears: Map<Int, FiscalYearSnapshot>
)

data class SnapshotMetadata(
    val version: String = "1.0",
    val createdAt: Instant,
    val lastTransactionDate: Instant,
    val gitCommit: String? = null
)
```

#### 2. Create Snapshot Serialization Service

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/SnapshotSerializer.kt` (new file)

```kotlin
package com.elchworks.tastyworkstaxcalculator.snapshot

import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYear
import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYearRepository
import com.elchworks.tastyworkstaxcalculator.portfolio.Portfolio
import com.elchworks.tastyworkstaxcalculator.portfolio.ProfitAndLoss
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionShortPosition
import com.elchworks.tastyworkstaxcalculator.portfolio.stock.StockPosition
import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade
import com.elchworks.tastyworkstaxcalculator.transactions.StockTransaction
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.Year
import javax.money.MonetaryAmount

@Component
class SnapshotSerializer {
    private val log = LoggerFactory.getLogger(SnapshotSerializer::class.java)

    fun createSnapshot(
        portfolio: Portfolio,
        fiscalYearRepository: FiscalYearRepository,
        lastTransactionDate: Instant
    ): StateSnapshot {
        log.info("Creating snapshot with lastTransactionDate={}", lastTransactionDate)

        return StateSnapshot(
            metadata = SnapshotMetadata(
                createdAt = Instant.now(),
                lastTransactionDate = lastTransactionDate
            ),
            portfolio = serializePortfolio(portfolio),
            fiscalYears = serializeFiscalYears(fiscalYearRepository)
        )
    }

    private fun serializePortfolio(portfolio: Portfolio): PortfolioSnapshot {
        // Access portfolio state via reflection or add public accessors
        val optionPositions = portfolio.getOptionPositionsMap()
            .mapValues { (_, queue) ->
                queue.map { serializeOptionPosition(it) }
            }

        val stockPositions = portfolio.getStockPositionsMap()
            .mapValues { (_, queue) ->
                queue.map { serializeStockPosition(it) }
            }

        return PortfolioSnapshot(optionPositions, stockPositions)
    }

    private fun serializeOptionPosition(position: OptionShortPosition): OptionPositionSnapshot {
        return OptionPositionSnapshot(
            stoTx = serializeOptionTrade(position.stoTx),
            quantityLeft = position.quantityLeft
        )
    }

    private fun serializeStockPosition(position: StockPosition): StockPositionSnapshot {
        return StockPositionSnapshot(
            btoTx = serializeStockTransaction(position.btoTx),
            quantityLeft = position.quantityLeft
        )
    }

    private fun serializeOptionTrade(trade: OptionTrade): OptionTradeSnapshot {
        return OptionTradeSnapshot(
            date = trade.date,
            symbol = trade.symbol,
            callOrPut = trade.callOrPut,
            expirationDate = trade.expirationDate.toString(),
            strikePrice = serializeMonetaryAmount(trade.strikePrice),
            quantity = trade.quantity,
            averagePrice = serializeMonetaryAmount(trade.averagePrice),
            description = trade.description,
            commissions = serializeMonetaryAmount(trade.commissions),
            fees = serializeMonetaryAmount(trade.fees)
        )
    }

    private fun serializeStockTransaction(tx: StockTransaction): StockTransactionSnapshot {
        return StockTransactionSnapshot(
            date = tx.date,
            symbol = tx.symbol,
            type = tx.type,
            value = serializeMonetaryAmount(tx.value),
            quantity = tx.quantity,
            averagePrice = serializeMonetaryAmount(tx.averagePrice),
            description = tx.description,
            commissions = serializeMonetaryAmount(tx.commissions),
            fees = serializeMonetaryAmount(tx.fees)
        )
    }

    private fun serializeMonetaryAmount(amount: MonetaryAmount): MonetaryAmountSnapshot {
        return MonetaryAmountSnapshot(
            amount = amount.number.doubleValueExact(),
            currency = amount.currency.currencyCode
        )
    }

    private fun serializeFiscalYears(repository: FiscalYearRepository): Map<Int, FiscalYearSnapshot> {
        return repository.getAllSortedByYear()
            .associate { fiscalYear ->
                fiscalYear.fiscalYear.value to serializeFiscalYear(fiscalYear)
            }
    }

    private fun serializeFiscalYear(fiscalYear: FiscalYear): FiscalYearSnapshot {
        val profits = fiscalYear.profits()
        return FiscalYearSnapshot(
            year = fiscalYear.fiscalYear.value,
            profitAndLossFromOptions = ProfitAndLossSnapshot(
                profit = serializeMonetaryAmount(profits.profitsFromOptions),
                loss = serializeMonetaryAmount(profits.lossesFromOptions)
            ),
            profitAndLossFromStocks = serializeMonetaryAmount(profits.profitsFromStocks)
        )
    }
}
```

#### 3. Add Portfolio State Accessors

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/portfolio/Portfolio.kt`

**Changes**: Add internal methods to access private state (after line 65)

```kotlin
internal fun getOptionPositionsMap(): MutableMap<String, Queue<OptionShortPosition>> = optionPositions
internal fun getStockPositionsMap(): MutableMap<String, Queue<StockPosition>> = stockPositions
```

**Note**: Using `internal` visibility keeps these methods module-private, preventing them from being part of the public API while allowing snapshot serialization/deserialization within the same module.

#### 4. Add FiscalYear State Accessor

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/fiscalyear/FiscalYear.kt`

**Changes**: The `profits()` method already exists at line 32-36, which provides access to all needed state. No changes needed.

#### 5. Configure Jackson for JSON Serialization

**File**: `build.gradle.kts`

**Changes**: Add Jackson dependencies (if not already present)

```kotlin
dependencies {
    // ... existing dependencies ...
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}
```

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/JacksonConfig.kt` (new file)

```kotlin
package com.elchworks.tastyworkstaxcalculator.snapshot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfig {
    @Bean
    fun objectMapper(): ObjectMapper {
        return ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}
```

### Success Criteria:

#### Automated Verification:
- [x] Project compiles: `./gradlew compileKotlin`

#### Manual Verification:
- [ ] Create snapshot from test data and inspect JSON structure
- [ ] Verify all position data is present in snapshot
- [ ] Verify fiscal year data is complete

**Implementation Note**: After completing this phase, pause here for manual confirmation that the snapshot JSON structure looks correct before proceeding to the next phase.

---

## Phase 2: Snapshot Deserialization & State Restoration

### Overview
Implement deserialization from JSON and restoration of Portfolio and FiscalYear state from snapshots.

### Changes Required:

#### 1. Create Snapshot Deserializer

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/SnapshotDeserializer.kt` (new file)

```kotlin
package com.elchworks.tastyworkstaxcalculator.snapshot

import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYear
import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYearRepository
import com.elchworks.tastyworkstaxcalculator.portfolio.Portfolio
import com.elchworks.tastyworkstaxcalculator.portfolio.ProfitAndLoss
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionShortPosition
import com.elchworks.tastyworkstaxcalculator.portfolio.stock.StockPosition
import com.elchworks.tastyworkstaxcalculator.transactions.Action
import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade
import com.elchworks.tastyworkstaxcalculator.transactions.StockTrade
import org.javamoney.moneta.Money
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.Year
import java.util.*

@Component
class SnapshotDeserializer {
    private val log = LoggerFactory.getLogger(SnapshotDeserializer::class.java)

    fun restoreState(
        snapshot: StateSnapshot,
        portfolio: Portfolio,
        fiscalYearRepository: FiscalYearRepository
    ) {
        log.info("Restoring state from snapshot. lastTransactionDate={}", snapshot.metadata.lastTransactionDate)

        restorePortfolio(snapshot.portfolio, portfolio)
        restoreFiscalYears(snapshot.fiscalYears, fiscalYearRepository)

        log.info("State restoration complete")
    }

    private fun restorePortfolio(portfolioSnapshot: PortfolioSnapshot, portfolio: Portfolio) {
        portfolio.reset() // Clear any existing state

        // Restore option positions
        portfolioSnapshot.optionPositions.forEach { (key, positions) ->
            val queue: Queue<OptionShortPosition> = LinkedList()
            positions.forEach { posSnapshot ->
                queue.offer(deserializeOptionPosition(posSnapshot))
            }
            portfolio.getOptionPositionsMap()[key] = queue
        }

        // Restore stock positions
        portfolioSnapshot.stockPositions.forEach { (symbol, positions) ->
            val queue: Queue<StockPosition> = LinkedList()
            positions.forEach { posSnapshot ->
                queue.offer(deserializeStockPosition(posSnapshot))
            }
            portfolio.getStockPositionsMap()[symbol] = queue
        }

        log.debug("Restored {} option position keys and {} stock position keys",
            portfolioSnapshot.optionPositions.size, portfolioSnapshot.stockPositions.size)
    }

    private fun deserializeOptionPosition(snapshot: OptionPositionSnapshot): OptionShortPosition {
        return OptionShortPosition(
            stoTx = deserializeOptionTrade(snapshot.stoTx),
            quantityLeft = snapshot.quantityLeft
        )
    }

    private fun deserializeStockPosition(snapshot: StockPositionSnapshot): StockPosition {
        return StockPosition(
            btoTx = deserializeStockTransaction(snapshot.btoTx),
            quantityLeft = snapshot.quantityLeft
        )
    }

    private fun deserializeOptionTrade(snapshot: OptionTradeSnapshot): OptionTrade {
        return OptionTrade(
            date = snapshot.date,
            action = Action.SELL_TO_OPEN, // Always SELL_TO_OPEN for open positions
            symbol = snapshot.symbol,
            callOrPut = snapshot.callOrPut,
            expirationDate = LocalDate.parse(snapshot.expirationDate),
            strikePrice = deserializeMonetaryAmount(snapshot.strikePrice),
            quantity = snapshot.quantity,
            averagePrice = deserializeMonetaryAmount(snapshot.averagePrice),
            description = snapshot.description,
            commissions = deserializeMonetaryAmount(snapshot.commissions),
            fees = deserializeMonetaryAmount(snapshot.fees)
        )
    }

    private fun deserializeStockTransaction(snapshot: StockTransactionSnapshot): StockTrade {
        return StockTrade(
            date = snapshot.date,
            action = Action.BUY_TO_OPEN, // Always BUY_TO_OPEN for open positions
            symbol = snapshot.symbol,
            type = snapshot.type,
            value = deserializeMonetaryAmount(snapshot.value),
            quantity = snapshot.quantity,
            averagePrice = deserializeMonetaryAmount(snapshot.averagePrice),
            description = snapshot.description,
            commissions = deserializeMonetaryAmount(snapshot.commissions),
            fees = deserializeMonetaryAmount(snapshot.fees)
        )
    }

    private fun deserializeMonetaryAmount(snapshot: MonetaryAmountSnapshot): Money {
        return Money.of(snapshot.amount, snapshot.currency)
    }

    private fun restoreFiscalYears(
        fiscalYearsSnapshot: Map<Int, FiscalYearSnapshot>,
        repository: FiscalYearRepository
    ) {
        repository.reset() // Clear any existing state

        fiscalYearsSnapshot.forEach { (yearValue, snapshot) ->
            val fiscalYear = repository.getFiscalYear(Year.of(yearValue))
            restoreFiscalYear(snapshot, fiscalYear)
        }

        log.debug("Restored {} fiscal years", fiscalYearsSnapshot.size)
    }

    private fun restoreFiscalYear(snapshot: FiscalYearSnapshot, fiscalYear: FiscalYear) {
        // Use reflection or add setter methods to restore state
        fiscalYear.restoreState(
            profitAndLossFromOptions = ProfitAndLoss(
                profit = deserializeMonetaryAmount(snapshot.profitAndLossFromOptions.profit),
                loss = deserializeMonetaryAmount(snapshot.profitAndLossFromOptions.loss)
            ),
            profitAndLossFromStocks = deserializeMonetaryAmount(snapshot.profitAndLossFromStocks)
        )
    }
}
```

#### 2. Add FiscalYear State Restoration Method

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/fiscalyear/FiscalYear.kt`

**Changes**: Add method to restore state (after line 36)

```kotlin
fun restoreState(
    profitAndLossFromOptions: ProfitAndLoss,
    profitAndLossFromStocks: MonetaryAmount
) {
    this.profitAndLossFromOptions = profitAndLossFromOptions
    this.profitAndLossFromStocks = profitAndLossFromStocks
    log.debug("Restored fiscal year {} state: optionProfit={}, optionLoss={}, stockProfit={}",
        fiscalYear,
        format(profitAndLossFromOptions.profit),
        format(profitAndLossFromOptions.loss),
        format(profitAndLossFromStocks))
}
```

#### 3. Portfolio State Access

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/portfolio/Portfolio.kt`

**Changes**: Already added in Phase 1. The `internal` accessor methods return the mutable maps, allowing direct manipulation during restoration while keeping them module-private.

#### 4. Make OptionShortPosition Constructor Public

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/portfolio/option/OptionShortPosition.kt`

**Changes**: Verify constructor accepts `quantityLeft` parameter or add secondary constructor

```kotlin
// If needed, add secondary constructor:
constructor(stoTx: OptionTrade, quantityLeft: Int) : this(stoTx) {
    this.quantityLeft = quantityLeft
}
```

#### 5. Make StockPosition Constructor Public

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/portfolio/stock/StockPosition.kt`

**Changes**: Verify constructor accepts `quantityLeft` parameter or add secondary constructor

```kotlin
// If needed, add secondary constructor:
constructor(btoTx: StockTransaction, quantityLeft: Int) : this(btoTx) {
    this.quantityLeft = quantityLeft
}
```

### Success Criteria:

#### Automated Verification:
- [x] Project compiles: `./gradlew compileKotlin`

#### Manual Verification:
- [ ] Serialize test data, deserialize, and verify state matches
- [ ] Verify queue order is preserved (FIFO)
- [ ] Verify partial positions (quantityLeft) are restored correctly

**Implementation Note**: After completing this phase, pause here for manual confirmation that state restoration works correctly before proceeding to the next phase.

---

## Phase 3: Snapshot File Management

### Overview
Implement file I/O for snapshots including filename generation, directory management, and latest snapshot discovery.

### Changes Required:

#### 1. Create Snapshot File Service

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/SnapshotFileService.kt` (new file)

```kotlin
package com.elchworks.tastyworkstaxcalculator.snapshot

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
class SnapshotFileService(
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(SnapshotFileService::class.java)

    private val filenameDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
        .withZone(ZoneId.of("CET")) // Use CET to match transaction dates

    fun saveSnapshot(snapshot: StateSnapshot, transactionsDir: String) {
        val snapshotDir = getSnapshotDirectory(transactionsDir)
        snapshotDir.mkdirs()

        val filename = generateFilename(snapshot.metadata.lastTransactionDate)
        val file = File(snapshotDir, filename)

        log.info("Saving snapshot to {}", file.absolutePath)
        objectMapper.writeValue(file, snapshot)
        log.info("Snapshot saved successfully")
    }

    fun loadLatestSnapshot(transactionsDir: String): StateSnapshot? {
        val snapshotDir = getSnapshotDirectory(transactionsDir)

        if (!snapshotDir.exists() || !snapshotDir.isDirectory) {
            log.info("No snapshot directory found at {}", snapshotDir.absolutePath)
            return null
        }

        val latestFile = findLatestSnapshotFile(snapshotDir)
            ?: return null.also { log.info("No snapshot files found in {}", snapshotDir.absolutePath) }

        log.info("Loading snapshot from {}", latestFile.absolutePath)
        val snapshot = objectMapper.readValue(latestFile, StateSnapshot::class.java)
        log.info("Snapshot loaded successfully. lastTransactionDate={}", snapshot.metadata.lastTransactionDate)
        return snapshot
    }

    private fun getSnapshotDirectory(transactionsDir: String): File {
        return File(transactionsDir, "snapshots")
    }

    private fun generateFilename(lastTransactionDate: Instant): String {
        val timestamp = filenameDateFormatter.format(lastTransactionDate)
        return "snapshot-$timestamp.json"
    }

    private fun findLatestSnapshotFile(snapshotDir: File): File? {
        val snapshotFiles = snapshotDir.listFiles { file ->
            file.isFile && file.name.startsWith("snapshot-") && file.name.endsWith(".json")
        } ?: return null

        // Sort by filename (which includes timestamp) and return the latest
        return snapshotFiles.sortedByDescending { it.name }.firstOrNull()
    }
}
```

### Success Criteria:

#### Automated Verification:
- [x] Project compiles: `./gradlew compileKotlin`

#### Manual Verification:
- [ ] Verify snapshot directory is created correctly
- [ ] Verify filename format matches specification: `snapshot-YYYY-MM-DD-HHmmss.json`
- [ ] Verify latest snapshot is selected when multiple exist

**Implementation Note**: After completing this phase, pause here for manual confirmation that file operations work correctly before proceeding to the next phase.

---

## Phase 4: ApplicationRunner Integration

### Overview
Integrate snapshot lifecycle into ApplicationRunner: load before processing, validate chronological order, save after completion.

### Changes Required:

#### 1. Update ApplicationRunner

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/ApplicationRunner.kt`

**Changes**: Integrate snapshot loading, validation, and saving

```kotlin
package com.elchworks.tastyworkstaxcalculator

import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYearManager
import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYearRepository
import com.elchworks.tastyworkstaxcalculator.portfolio.NewTransactionEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.Portfolio
import com.elchworks.tastyworkstaxcalculator.snapshot.*
import com.elchworks.tastyworkstaxcalculator.transactions.Transaction
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.io.File
import java.time.Instant

@Component
class ApplicationRunner(
    private val transactionsCsvReader: TransactionCsvReader,
    private val eventPublisher: ApplicationEventPublisher,
    private val fiscalYearManager: FiscalYearManager,
    private val portfolio: Portfolio,
    private val fiscalYearRepository: FiscalYearRepository,
    private val snapshotFileService: SnapshotFileService,
    private val snapshotSerializer: SnapshotSerializer,
    private val snapshotDeserializer: SnapshotDeserializer
): ApplicationRunner {
    private val log = LoggerFactory.getLogger(TastyworksTaxCalculatorApplication::class.java)

    override fun run(args: ApplicationArguments) {
        val transactionsDir = args.getOptionValues("transactionsDir")[0]

        // Load snapshot if available
        val snapshot = snapshotFileService.loadLatestSnapshot(transactionsDir)
        if (snapshot != null) {
            snapshotDeserializer.restoreState(snapshot, portfolio, fiscalYearRepository)
            log.info("Resumed from snapshot. Last transaction: {}", snapshot.metadata.lastTransactionDate)
        } else {
            log.info("No snapshot found. Processing all transactions from scratch.")
        }

        // Read and sort all transactions
        val transactions = File(transactionsDir)
            .walk()
            .filter { it.isFile && !it.absolutePath.contains("/snapshots/") }
            .map {
                log.debug("reading $it")
                val tx = transactionsCsvReader.read(it)
                log.info("read $it")
                tx
            }
            .flatten()
            .sortedBy { it.date }
            .toList()

        log.debug("Total transactions loaded: {}", transactions.size)

        // Validate chronological order
        validateChronologicalOrder(snapshot, transactions)

        // Process transactions
        var lastTransactionDate: Instant? = snapshot?.metadata?.lastTransactionDate
        log.debug("Starting transaction processing. Initial lastTransactionDate: {}", lastTransactionDate)

        transactions.forEach { tx ->
            eventPublisher.publishEvent(NewTransactionEvent(tx))
            lastTransactionDate = tx.date
        }

        log.debug("Transaction processing complete. Final lastTransactionDate: {}", lastTransactionDate)

        // Generate reports
        fiscalYearManager.printReports()

        // Save snapshot if we processed any transactions
        if (lastTransactionDate != null) {
            log.debug("Creating new snapshot with lastTransactionDate: {}", lastTransactionDate)
            val newSnapshot = snapshotSerializer.createSnapshot(
                portfolio = portfolio,
                fiscalYearRepository = fiscalYearRepository,
                lastTransactionDate = lastTransactionDate!!
            )
            snapshotFileService.saveSnapshot(newSnapshot, transactionsDir)
        } else {
            log.debug("No transactions processed, snapshot not updated")
        }
    }

    private fun validateChronologicalOrder(snapshot: StateSnapshot?, transactions: List<Transaction>) {
        log.debug("validateChronologicalOrder snapshot={}, transactions.size={}",
            snapshot?.metadata?.lastTransactionDate, transactions.size)

        // Skip validation if no snapshot or no transactions
        if (snapshot == null || transactions.isEmpty()) {
            return
        }

        val firstNewTx = transactions.first()
        val snapshotLastDate = snapshot.metadata.lastTransactionDate

        log.debug("validateChronologicalOrder firstNewTx.date='{}', snapshotLastDate='{}'",
            firstNewTx.date, snapshotLastDate)

        require(firstNewTx.date > snapshotLastDate) {
            """
            |
            |ERROR: Chronological order violation detected
            |
            |  Snapshot last transaction: $snapshotLastDate
            |  First new transaction:     ${firstNewTx.date}
            |
            |  Cannot process transactions that occur before or at the same time as the snapshot date.
            |
            |  Please provide complete transaction history from the beginning,
            |  or delete the snapshot files in the snapshots/ directory to start fresh.
            |
            """.trimMargin()
        }
    }
}
```

### Success Criteria:

#### Automated Verification:
- [x] Project compiles: `./gradlew compileKotlin`
- [x] All existing tests still pass: `./gradlew test`

#### Manual Verification:
- [ ] Run with complete transaction history, verify snapshot is created
- [ ] Run again with no new transactions, verify snapshot is loaded and updated
- [ ] Add new transaction, verify incremental processing works
- [ ] Add backdated transaction, verify error message is clear and helpful
- [ ] Delete snapshots directory, verify full processing resumes

**Implementation Note**: After completing this phase, pause here for manual confirmation that the full workflow works end-to-end before proceeding to the next phase.

---

## Phase 5: Testing & Documentation

### Overview
Add BDD test coverage for user-facing workflows and targeted unit tests for edge cases. Update documentation for snapshot functionality.

### Changes Required:

#### 1. BDD Scenarios (Primary Testing Approach)

**File**: `src/test/resources/bdd/Snapshots.feature` (new file)

```gherkin
Feature: Persistent State Snapshots

  Scenario: Create snapshot after processing transactions
    Given Fixed exchange rate of "2.00" USD to EUR
    And a clean state with no snapshots
    When Sell option "CLF 15/01/24 Put 13.50 @ 0.32" on "10/01/24"
    And Buy option "CLF 15/01/24 Put 13.50 @ 0.32" on "15/01/24"
    Then a snapshot file should be created in "snapshots/" directory
    And the snapshot filename should match format "snapshot-2024-01-15-*.json"
    And Profits for fiscal year 2024 should be options profits 0.0 losses 0.0 stocks 0.0

  Scenario: Serialize and deserialize portfolio snapshot
    Given Fixed exchange rate of "2.00" USD to EUR
    When Sell option "CLF 15/01/24 Put 13.50 @ 0.32" on "10/01/24"
    And a snapshot is created
    And the portfolio is restored from the snapshot
    Then Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 0.0
    And Portfolio should have an option position "CLF 15/01/24 Put 13.50 @ 0.32" with quantity 1

  Scenario: Resume with open option positions from snapshot
    Given Fixed exchange rate of "2.00" USD to EUR
    When Sell option "CLF 20/02/24 Put 15.00 @ 0.10" on "10/01/24" quantity 2
    And a snapshot is created
    And the portfolio is restored from the snapshot
    Then Profits for fiscal year 2024 should be options profits 40.0 losses 0.0 stocks 0.0
    When Buy option "CLF 20/02/24 Put 15.00 @ 0.10" on "15/01/24" quantity 1
    Then Profits for fiscal year 2024 should be options profits 20.0 losses 0.0 stocks 0.0
    And Portfolio should have an option position "CLF 20/02/24 Put 15.00 @ 0.10" with quantity 1

  Scenario: Resume with stock positions from snapshot
    Given Fixed exchange rate of "2.00" USD to EUR
    When Sell option "CLF 15/01/24 Put 13.50 @ 0.32" on "10/01/24"
    And Assignment "CLF 15/01/24 Put 13.50 @ 0.32"
    And a snapshot is created
    And the portfolio is restored from the snapshot
    Then Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 0.0
    And Portfolio should have a stock position for symbol "CLF" with quantity 100
    When Sell stock 100 "CLF" on "20/01/24" average price: "14.50"
    Then Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 200.0

  Scenario: Resume across fiscal years
    Given Fixed exchange rate of "2.00" USD to EUR
    When Sell option "CLF 15/02/24 Put 13.50 @ 0.32" on "31/12/24"
    And a snapshot is created
    And the portfolio is restored from the snapshot
    Then Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 0.0
    When Buy option "CLF 15/02/24 Put 13.50 @ 0.32" on "01/01/25"
    Then Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 0.0
    And Profits for fiscal year 2025 should be options profits 0.0 losses 64.0 stocks 0.0

  Scenario: Snapshot file operations
    Given Fixed exchange rate of "2.00" USD to EUR
    And a clean snapshots directory
    When Sell option "CLF 15/01/24 Put 13.50 @ 0.32" on "10/01/24"
    And a snapshot is saved to file
    Then a snapshot file should exist
    And the snapshot filename should match format "snapshot-2024-01-10-*.json"
    When the snapshot is loaded from file
    Then the snapshot last transaction date should be "10/01/24"
```

#### 2. Step Definitions for BDD Scenarios

**File**: Add to existing `src/test/kotlin/com/elchworks/tastyworkstaxcalculator/test/StepDefinitions.kt`

```kotlin
// Add these fields to StepDefinitions class
@Autowired
private lateinit var snapshotSerializer: SnapshotSerializer

@Autowired
private lateinit var snapshotDeserializer: SnapshotDeserializer

@Autowired
private lateinit var snapshotFileService: SnapshotFileService

private var currentSnapshot: StateSnapshot? = null
private var lastTransactionDate: Instant? = null
private val testSnapshotDir = File(System.getProperty("java.io.tmpdir"), "test-snapshots")

// Modify publishTx to track last transaction
fun publishTx(tx: Transaction) {
    eventPublisher.publishEvent(NewTransactionEvent(tx))
    lastTransactionDate = tx.date
}

// Add new step definitions
@Given("a clean snapshots directory")
fun aCleanSnapshotsDirectory() {
    if (testSnapshotDir.exists()) {
        testSnapshotDir.deleteRecursively()
    }
    testSnapshotDir.mkdirs()
}

@When("a snapshot is created")
fun aSnapshotIsCreated() {
    requireNotNull(lastTransactionDate) { "No transactions published yet" }

    currentSnapshot = snapshotSerializer.createSnapshot(
        portfolio = portfolio,
        fiscalYearRepository = fiscalYearRepository,
        lastTransactionDate = lastTransactionDate!!
    )
}

@When("a snapshot is saved to file")
fun aSnapshotIsSavedToFile() {
    requireNotNull(lastTransactionDate) { "No transactions published yet" }

    val snapshot = snapshotSerializer.createSnapshot(
        portfolio = portfolio,
        fiscalYearRepository = fiscalYearRepository,
        lastTransactionDate = lastTransactionDate!!
    )

    snapshotFileService.saveSnapshot(snapshot, testSnapshotDir.absolutePath)
    currentSnapshot = snapshot
}

@When("the snapshot is loaded from file")
fun theSnapshotIsLoadedFromFile() {
    currentSnapshot = snapshotFileService.loadLatestSnapshot(testSnapshotDir.absolutePath)
    requireNotNull(currentSnapshot) { "No snapshot file found" }
}

@When("the portfolio is restored from the snapshot")
fun thePortfolioIsRestoredFromTheSnapshot() {
    requireNotNull(currentSnapshot) { "No snapshot created yet" }

    portfolio.reset()
    fiscalYearRepository.reset()

    snapshotDeserializer.restoreState(
        snapshot = currentSnapshot!!,
        portfolio = portfolio,
        fiscalYearRepository = fiscalYearRepository
    )
}

@Then("a snapshot file should exist")
fun aSnapshotFileShouldExist() {
    val snapshotFiles = testSnapshotDir.listFiles { file ->
        file.isFile && file.name.startsWith("snapshot-") && file.name.endsWith(".json")
    }

    assertThat(snapshotFiles).isNotEmpty()
}

@Then("the snapshot filename should match format {string}")
fun theSnapshotFilenameShouldMatchFormat(format: String) {
    val snapshotFiles = testSnapshotDir.listFiles { file ->
        file.isFile && file.name.startsWith("snapshot-") && file.name.endsWith(".json")
    }

    assertThat(snapshotFiles).isNotEmpty()
    val snapshotFile = snapshotFiles!!.first()

    val regexPattern = format
        .replace(".", "\\.")
        .replace("*", ".*")

    assertThat(snapshotFile.name).matches(regexPattern)
}

@Then("the snapshot last transaction date should be {string}")
fun theSnapshotLastTransactionDateShouldBe(dateString: String) {
    requireNotNull(currentSnapshot) { "No snapshot loaded" }

    val expectedDate = dateString.toLocalDate().toInstant()
    assertThat(currentSnapshot!!.metadata.lastTransactionDate).isEqualTo(expectedDate)
}
```

**Note**: These step definitions integrate directly into the existing `StepDefinitions.kt` class, following the same pattern as the existing E2E tests. They work by:
1. Tracking the last transaction date when events are published
2. Creating snapshots programmatically using the serializer
3. Restoring state using the deserializer
4. Testing file I/O with a temporary test directory

#### 3. Unit Tests for Edge Cases (Optional, Only Where Needed)

Add unit tests only for complex edge cases that are difficult to test via BDD:

**File**: `src/test/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/SnapshotFileServiceTest.kt` (new file)

```kotlin
package com.elchworks.tastyworkstaxcalculator.snapshot

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant

class SnapshotFileServiceTest {

    @Test
    fun `test filename generation with various timestamps`(@TempDir tempDir: File) {
        // Test edge cases: leap years, timezone boundaries, etc.
    }

    @Test
    fun `test latest snapshot selection with multiple files`(@TempDir tempDir: File) {
        // Test edge cases: same date different times, malformed filenames, etc.
    }
}
```

Only create this file if edge cases arise during implementation that warrant fine-grained testing.

#### 4. Update README

**File**: `README.md`

**Changes**: Add section about snapshot functionality

```markdown
## Persistent State Snapshots

The application automatically saves snapshots of portfolio and fiscal year state after processing transactions. This allows incremental processing where you only need to provide new transactions instead of complete history.

### How It Works

1. **First Run**: Process all transactions, create snapshot in `{transactionsDir}/snapshots/`
2. **Subsequent Runs**: Load latest snapshot, process only new transactions, update snapshot
3. **Fresh Start**: Delete files in `snapshots/` directory to process from scratch

### Snapshot Files

- Format: `snapshot-YYYY-MM-DD-HHmmss.json`
- Location: `{transactionsDir}/snapshots/`
- Latest snapshot is automatically used

### Chronological Order Validation

New transactions must be dated after the snapshot's last transaction. If validation fails, you'll see a clear error message. To fix:
- Provide complete transaction history from the beginning, or
- Delete snapshot files to start fresh

### Example Usage

```bash
# First run - process all 2023 data
./gradlew bootRun --args="--transactionsDir=/path/to/transactions"
# Creates: /path/to/transactions/snapshots/snapshot-2023-12-31-235959.json

# Second run - only provide 2024 data
# Application automatically loads snapshot and processes only new transactions
./gradlew bootRun --args="--transactionsDir=/path/to/transactions"
# Updates: /path/to/transactions/snapshots/snapshot-2024-12-31-235959.json
```
```

### Success Criteria:

#### Automated Verification:
- [x] All BDD scenarios pass: `./gradlew test`
- [x] No compilation errors: `./gradlew build`

#### Manual Verification:
- [ ] README instructions are clear and accurate
- [ ] All BDD test scenarios execute correctly
- [ ] Error messages are helpful for users
- [ ] Snapshot JSON files are human-readable

**Implementation Note**: This is the final phase. After all tests pass and documentation is complete, the feature is ready for use.

---

## Testing Strategy

### Primary: BDD Tests
All main functionality is tested through BDD scenarios covering:
- User-facing workflows for snapshot functionality
- Complete workflow: process → snapshot → resume → process
- Snapshot load → validate → reject backdated transaction
- Multiple fiscal years with snapshot
- Empty snapshot (no open positions) handling
- Error scenarios with clear messages
- Edge cases (no snapshot, multiple snapshots, etc.)

### Secondary: Unit Tests (Only for Complex Edge Cases)
Fine-grained unit tests are added only where needed for edge cases difficult to cover in BDD:
- Filename generation with unusual timestamps (leap years, timezone boundaries)
- Latest snapshot file discovery with malformed filenames
- Serialization edge cases (special characters, very large numbers, null values)

### Manual Testing Steps:
1. Run with complete 2023 transaction history
2. Verify snapshot created in `{transactionsDir}/snapshots/`
3. Verify snapshot filename format is correct
4. Run again with only 2024 transactions
5. Verify snapshot was loaded (check logs)
6. Verify only 2024 transactions processed (check logs for "Processing X new transactions")
7. Verify fiscal year reports match expected values
8. Add a backdated transaction (e.g., dated 2023-12-15)
9. Verify clear error message displayed
10. Delete snapshots directory
11. Verify full processing resumes from scratch

## Performance Considerations

- Snapshot files are JSON and human-readable (not optimized for size)
- Loading/saving snapshots adds minimal overhead compared to transaction processing
- File I/O is performed only at start and end of processing (not per-transaction)
- Memory usage unchanged - state was always in memory, now it's also persisted

## Migration Notes

No migration needed - this is a new feature. Existing installations will:
- Continue working without snapshots (backward compatible)
- Automatically create snapshots on first run after upgrade
- Start benefiting from incremental processing on second run

## References

- Specification: `spec/persistent-state.md`
- Research document: `thoughts/shared/research/2025-10-17-persistent-state-implementation.md`
- Project instructions: `CLAUDE.md`
- ApplicationRunner: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/ApplicationRunner.kt:21-38`
- Portfolio state: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/portfolio/Portfolio.kt:26-27`
- FiscalYear state: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/fiscalyear/FiscalYear.kt:28-29`
