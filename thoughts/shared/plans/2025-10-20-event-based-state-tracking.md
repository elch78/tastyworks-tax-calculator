# Event-Based State Tracking for Portfolio Persistence

## Overview

Refactor the portfolio and fiscal year persistence implementation from a "pull" model (serializer reads internal state via `internal` getters) to an "event-based" model where dedicated state tracker components listen to domain events and maintain snapshot-ready state. This eliminates the need for Portfolio and FiscalYear to expose internal state for persistence purposes.

## Current State Analysis

### Existing Event-Driven Architecture

The application already uses Spring's event publishing mechanism extensively:

**Portfolio Events** (Portfolio.kt):
- `OptionSellToOpenEvent` - Published when option position opened (line 216)
- `OptionBuyToCloseEvent` - Published when option position closed (line 203)
- `StockSellToCloseEvent` - Published when stock position closed (line 94)
- **NO event for stock position opening** - Stock positions created silently at line 103

**Event Listeners**:
- `FiscalYearManager` already demonstrates event-based pattern (FiscalYearManager.kt:18-28)
- Listens to all three position events for tax calculation
- Has **no direct coupling** to Portfolio's internal state

### Current Persistence Approach

**Breaking Encapsulation**:
- Portfolio.kt:68-69 - `internal` getters expose position maps for serialization
- SnapshotSerializer.kt:38,43 - Pulls state via `getOptionPositionsMap()`, `getStockPositionsMap()`
- SnapshotDeserializer.kt:45,54 - Directly assigns to internal maps after restoration

**Dependencies**:
```
ApplicationRunner → Snapshot Package → Domain Models (Portfolio, FiscalYear)
```
- Domain models are independent (no persistence dependencies)
- But encapsulation is violated via `internal` visibility

### What Events Track

**Option Position Lifecycle**:
1. `OptionSellToOpenEvent(stoTx)` - Position opened
2. `OptionBuyToCloseEvent(stoTx, btcTx, quantitySold)` - Position fully/partially closed
3. `OptionRemoval` events (ASSIGNED, EXPIRED) - Position removed without explicit close

**Stock Position Lifecycle**:
1. **NO EVENT** - Position opened (via `OptionAssignment` or `StockTrade`)
2. `StockSellToCloseEvent(btoTx, stcTx, quantitySold)` - Position fully/partially closed

## Desired End State

### Perfect Encapsulation

**Portfolio.kt**:
- Remove lines 68-69: `internal fun getOptionPositionsMap()`, `internal fun getStockPositionsMap()`
- Keep `reset()` method for state restoration (line 63-66)
- Keep public getters for business logic (lines 47-61)
- Continue publishing events as currently implemented

**FiscalYear.kt**:
- No changes needed - already has clean interface via `profits()` and `restoreState()`

### Event-Based State Trackers

**New Components**:
- `PortfolioStateTracker` - Listens to Portfolio events, maintains PortfolioSnapshot
- `FiscalYearStateTracker` - Listens to position events, maintains fiscal year snapshots

**Modified Components**:
- `SnapshotSerializer` - Uses trackers instead of domain getters
- `SnapshotDeserializer` - Restores both domain AND tracker state

### Verification

**Automated Verification**:
- [ ] All existing BDD tests pass: `./gradlew test`
- [ ] Snapshot files contain same data structure as before
- [ ] State restoration produces identical Portfolio and FiscalYear state

**Manual Verification**:
- [ ] Run application with existing transaction files
- [ ] Compare output reports before and after refactoring
- [ ] Verify snapshots are created correctly
- [ ] Verify application can resume from snapshots

## What We're NOT Doing

- NOT changing the snapshot JSON format or file structure
- NOT modifying the domain model's business logic
- NOT changing how events are published (except adding StockBuyToOpenEvent)
- NOT touching the BDD test scenarios (implementation tests may need updates)
- NOT creating a migration tool (old snapshots will remain compatible)

## Implementation Approach

This refactoring follows the **Strangler Fig Pattern** - we'll introduce new event-based trackers alongside the existing implementation, validate they produce identical results, then remove the old approach. This allows incremental migration with continuous validation.

**Key Strategy**:
1. Add event for stock position opening (required for complete tracking)
2. Create state tracker components that listen to events
3. Validate trackers produce identical state to current approach
4. Switch serializer to use trackers
5. Remove `internal` getters from Portfolio

## Phase 1: Add Missing Stock Position Event

### Overview
Stock positions are currently created silently without publishing events. We need to add `StockBuyToOpenEvent` to enable event-based tracking.

### Changes Required

#### 1. Create StockBuyToOpenEvent

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/portfolio/stock/StockBuyToOpenEvent.kt` (NEW)

```kotlin
package com.elchworks.tastyworkstaxcalculator.portfolio.stock

import com.elchworks.tastyworkstaxcalculator.transactions.StockTransaction

data class StockBuyToOpenEvent(
    val btoTx: StockTransaction,
)
```

#### 2. Publish Event When Stock Positions Open

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/portfolio/Portfolio.kt`

**Changes**: Modify `openStockPosition` method

```kotlin
private fun openStockPosition(btoTx: StockTransaction) {
    stockPositions.computeIfAbsent(btoTx.symbol) {LinkedList()}
        .offer(StockPosition(btoTx, btoTx.quantity))
    eventPublisher.publishEvent(StockBuyToOpenEvent(btoTx))  // NEW LINE
    log.info("Stock BTO symbol='{}' stcTx date {} quantity {} price {} description {}",
        btoTx.symbol, btoTx.date, btoTx.quantity, btoTx.averagePrice, btoTx.description)
}
```

### Success Criteria

#### Automated Verification:
- [ ] All existing tests pass: `./gradlew test`
- [ ] New event class compiles without errors: `./gradlew compileKotlin`

#### Manual Verification:
- [ ] Application runs successfully with test transaction files
- [ ] Log output shows stock BTO events being published
- [ ] No behavioral changes in Portfolio or FiscalYearManager

**Implementation Note**: After completing this phase and all automated verification passes, confirm the manual testing was successful before proceeding to Phase 2.

---

## Phase 2: Create PortfolioStateTracker

### Overview
Implement the `PortfolioStateTracker` component that listens to all Portfolio events and maintains a snapshot-ready representation of portfolio state. This tracker will eventually replace the need for Portfolio's `internal` getters.

### Changes Required

#### 1. Create PortfolioStateTracker Component

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/PortfolioStateTracker.kt` (NEW)

```kotlin
package com.elchworks.tastyworkstaxcalculator.snapshot

import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionBuyToCloseEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionSellToOpenEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionShortPosition
import com.elchworks.tastyworkstaxcalculator.portfolio.stock.StockBuyToOpenEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.stock.StockPosition
import com.elchworks.tastyworkstaxcalculator.portfolio.stock.StockSellToCloseEvent
import com.elchworks.tastyworkstaxcalculator.transactions.optionKey
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class PortfolioStateTracker(
    private val snapshotSerializer: SnapshotSerializer
) {
    private val log = LoggerFactory.getLogger(PortfolioStateTracker::class.java)

    // Maps of position key -> list of position snapshots (FIFO order)
    private val optionPositions = mutableMapOf<String, MutableList<OptionPositionSnapshot>>()
    private val stockPositions = mutableMapOf<String, MutableList<StockPositionSnapshot>>()

    @EventListener(OptionSellToOpenEvent::class)
    fun onOptionOpened(event: OptionSellToOpenEvent) {
        val stoTx = event.stoTx
        val key = stoTx.key()

        val snapshot = OptionPositionSnapshot(
            stoTx = snapshotSerializer.serializeOptionTrade(stoTx),
            quantityLeft = stoTx.quantity
        )

        optionPositions.computeIfAbsent(key) { mutableListOf() }
            .add(snapshot)

        log.debug("Tracked option position opened: key={}, quantity={}", key, stoTx.quantity)
    }

    @EventListener(OptionBuyToCloseEvent::class)
    fun onOptionClosed(event: OptionBuyToCloseEvent) {
        val stoTx = event.stoTx
        val key = stoTx.key()
        val quantityClosed = event.quantitySold

        val positions = optionPositions[key]
            ?: throw IllegalStateException("No tracked position for option close: $key")

        // Update first position's quantity (FIFO)
        val position = positions.first()
        val newQuantity = position.quantityLeft - quantityClosed

        if (newQuantity == 0) {
            positions.removeAt(0)
            if (positions.isEmpty()) {
                optionPositions.remove(key)
                log.debug("Removed option position key: {}", key)
            }
        } else {
            positions[0] = position.copy(quantityLeft = newQuantity)
        }

        log.debug("Tracked option position closed: key={}, quantityClosed={}, remaining={}",
            key, quantityClosed, newQuantity)
    }

    @EventListener(StockBuyToOpenEvent::class)
    fun onStockOpened(event: StockBuyToOpenEvent) {
        val btoTx = event.btoTx
        val symbol = btoTx.symbol

        val snapshot = StockPositionSnapshot(
            btoTx = snapshotSerializer.serializeStockTransaction(btoTx),
            quantityLeft = btoTx.quantity
        )

        stockPositions.computeIfAbsent(symbol) { mutableListOf() }
            .add(snapshot)

        log.debug("Tracked stock position opened: symbol={}, quantity={}", symbol, btoTx.quantity)
    }

    @EventListener(StockSellToCloseEvent::class)
    fun onStockClosed(event: StockSellToCloseEvent) {
        val btoTx = event.btoTx
        val symbol = btoTx.symbol
        val quantityClosed = event.quantitySold

        val positions = stockPositions[symbol]
            ?: throw IllegalStateException("No tracked position for stock close: $symbol")

        // Update first position's quantity (FIFO)
        val position = positions.first()
        val newQuantity = position.quantityLeft - quantityClosed

        if (newQuantity == 0) {
            positions.removeAt(0)
            if (positions.isEmpty()) {
                stockPositions.remove(symbol)
                log.debug("Removed stock position symbol: {}", symbol)
            }
        } else {
            positions[0] = position.copy(quantityLeft = newQuantity)
        }

        log.debug("Tracked stock position closed: symbol={}, quantityClosed={}, remaining={}",
            symbol, quantityClosed, newQuantity)
    }

    fun getPortfolioSnapshot(): PortfolioSnapshot {
        log.debug("Creating portfolio snapshot from tracker: optionKeys={}, stockSymbols={}",
            optionPositions.size, stockPositions.size)
        return PortfolioSnapshot(
            optionPositions = optionPositions.mapValues { it.value.toList() },
            stockPositions = stockPositions.mapValues { it.value.toList() }
        )
    }

    fun reset() {
        log.debug("Resetting portfolio state tracker")
        optionPositions.clear()
        stockPositions.clear()
    }

    fun restoreFrom(snapshot: PortfolioSnapshot) {
        reset()

        snapshot.optionPositions.forEach { (key, list) ->
            optionPositions[key] = list.toMutableList()
        }
        snapshot.stockPositions.forEach { (symbol, list) ->
            stockPositions[symbol] = list.toMutableList()
        }

        log.debug("Restored portfolio state tracker: optionKeys={}, stockSymbols={}",
            optionPositions.size, stockPositions.size)
    }
}
```

#### 2. Make Serialization Methods Public

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/SnapshotSerializer.kt`

**Changes**: Change visibility of helper methods from `private` to `internal` so PortfolioStateTracker can use them

```kotlin
// Change line 53: private fun serializeOptionPosition
internal fun serializeOptionPosition(position: OptionShortPosition): OptionPositionSnapshot {
    // ... existing implementation
}

// Change line 60: private fun serializeStockPosition
internal fun serializeStockPosition(position: StockPosition): StockPositionSnapshot {
    // ... existing implementation
}

// Change line 67: private fun serializeOptionTrade
internal fun serializeOptionTrade(trade: OptionTrade): OptionTradeSnapshot {
    // ... existing implementation
}

// Change line 82: private fun serializeStockTransaction
internal fun serializeStockTransaction(tx: StockTransaction): StockTransactionSnapshot {
    // ... existing implementation
}

// Change line 108: private fun serializeMonetaryAmount
internal fun serializeMonetaryAmount(amount: MonetaryAmount): MonetaryAmountSnapshot {
    // ... existing implementation
}
```

#### 3. Add Helper Extension Function

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/transactions/TransactionExtensions.kt` (NEW or add to existing file)

```kotlin
package com.elchworks.tastyworkstaxcalculator.transactions

import java.time.LocalDate
import javax.money.MonetaryAmount

fun OptionTransaction.key(): String =
    "${this.callOrPut}-${this.symbol}-${this.expirationDate}-${this.strikePrice}"

private fun optionKey(
    callOrPut: String,
    symbol: String,
    expirationDate: LocalDate,
    strikePrice: MonetaryAmount
) = "${callOrPut}-${symbol}-${expirationDate}-${strikePrice}"
```

**Note**: This extracts the `key()` logic currently in Portfolio.kt:230 to make it available to the state tracker.

### Success Criteria

#### Automated Verification:
- [ ] All tests pass: `./gradlew test`
- [ ] Type checking passes: `./gradlew compileKotlin`
- [ ] PortfolioStateTracker compiles without errors

#### Manual Verification:
- [ ] Application runs successfully
- [ ] Debug logs show state tracker receiving events
- [ ] State tracker maintains position counts correctly
- [ ] No impact on existing functionality

**Implementation Note**: After completing this phase and all automated verification passes, confirm the manual testing was successful before proceeding to Phase 3.

---

## Phase 3: Add FiscalYear Events and State Tracker

### Overview
FiscalYear should emit events when it updates profit/loss state, allowing FiscalYearStateTracker to listen and record those changes. This avoids duplicating complex business logic (German tax law calculations, cross-year position handling, etc.) in the tracker.

### Changes Required

#### 1. Create FiscalYear Events

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/fiscalyear/FiscalYearEvents.kt` (NEW)

```kotlin
package com.elchworks.tastyworkstaxcalculator.fiscalyear

import com.elchworks.tastyworkstaxcalculator.portfolio.ProfitAndLoss
import java.time.Year
import javax.money.MonetaryAmount

/**
 * Published by FiscalYear when option profit/loss is updated.
 * Contains both the delta (change) and the new total state.
 */
data class OptionProfitLossUpdatedEvent(
    val year: Year,
    val profitLossDelta: ProfitAndLoss,
    val totalProfitAndLoss: ProfitAndLoss
)

/**
 * Published by FiscalYear when stock profit/loss is updated.
 * Contains both the delta (change) and the new total state.
 */
data class StockProfitLossUpdatedEvent(
    val year: Year,
    val profitLossDelta: MonetaryAmount,
    val totalProfitAndLoss: MonetaryAmount
)
```

#### 2. Inject EventPublisher into FiscalYear

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/fiscalyear/FiscalYear.kt`

**Changes**: Add EventPublisher dependency (constructor parameter)

```kotlin
class FiscalYear(
    private val currencyExchange: CurrencyExchange,
    val fiscalYear: Year,
    private val eventPublisher: ApplicationEventPublisher  // NEW
) {
    private var profitAndLossFromOptions = ProfitAndLoss()
    private var profitAndLossFromStocks = eur(0)
    private val log = LoggerFactory.getLogger(FiscalYear::class.java)

    // ... existing methods ...
}
```

#### 3. Publish Events When State Changes

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/fiscalyear/FiscalYear.kt`

**Changes**: Add event publishing after state updates in option and stock handling methods

**In `onOptionPositionOpened` method (after line 54)**:
```kotlin
fun onOptionPositionOpened(stoEvent: OptionSellToOpenEvent) {
    val stoTx = stoEvent.stoTx
    val premium = currencyExchange.usdToEur(stoTx.value(), stoTx.date)
    val delta = ProfitAndLoss(premium, Money.of(0, "EUR"))
    profitAndLossFromOptions += delta

    // NEW: Publish event with both delta and total
    eventPublisher.publishEvent(
        OptionProfitLossUpdatedEvent(
            year = fiscalYear,
            profitLossDelta = delta,
            totalProfitAndLoss = profitAndLossFromOptions
        )
    )

    log.info("{} Option STO: {} premium {}", fiscalYear, stoTx.symbol, format(premium))
}
```

**In `onOptionPositionClosed` method (after line 93)**:
```kotlin
fun onOptionPositionClosed(btcEvent: OptionBuyToCloseEvent) {
    val btcTx = btcEvent.btcTx
    val stoTx = btcEvent.stoTx
    val quantitySold = btcEvent.quantitySold
    log.debug("onOptionPositionClosed stoTx.averagePrice='{}', stoTx.quantity='{}', btcTx.averagePrice='{}', btcTx.quantity='{}', quantitySold='{}'",
        format(stoTx.averagePrice), stoTx.quantity, format(btcTx.averagePrice), btcTx.quantity, quantitySold)

    // ... existing calculation logic (lines 65-92) ...

    profitAndLossFromOptions += profitAndLoss

    // NEW: Publish event with both delta and total
    eventPublisher.publishEvent(
        OptionProfitLossUpdatedEvent(
            year = fiscalYear,
            profitLossDelta = profitAndLoss,
            totalProfitAndLoss = profitAndLossFromOptions
        )
    )

    log.info(
        "{} Option BTC: {} profitAndLoss='{}', profitAndLossFromOptions='{}'",
        fiscalYear,
        stoTx.optionDescription(), profitAndLoss, format(profitAndLossFromOptions.profit)
    )
}
```

**In `onStockPositionClosed` method (after line 106)**:
```kotlin
fun onStockPositionClosed(event: StockSellToCloseEvent) {
    val btoTx = event.btoTx
    val stcTx = event.stcTx
    val quantity = event.quantitySold
    val netProfit = netProfit(btoTx, stcTx, quantity)
    profitAndLossFromStocks += netProfit

    // NEW: Publish event with both delta and total
    eventPublisher.publishEvent(
        StockProfitLossUpdatedEvent(
            year = fiscalYear,
            profitLossDelta = netProfit,
            totalProfitAndLoss = profitAndLossFromStocks
        )
    )

    log.info(
        "{} Stock STC: {} profitAndLoss='{}', profitFromStocks='{}'",
        fiscalYear,
        stcTx.description,
        format(netProfit),
        format(profitAndLossFromStocks)
    )
}
```

#### 4. Update FiscalYearRepository to Pass EventPublisher

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/fiscalyear/FiscalYearRepository.kt`

**Changes**: Inject and pass EventPublisher when creating FiscalYear instances

```kotlin
@Component
class FiscalYearRepository(
    private val currencyExchange: CurrencyExchange,
    private val eventPublisher: ApplicationEventPublisher  // NEW
) {
    private val fiscalYears = mutableMapOf<Year, FiscalYear>()

    fun getFiscalYear(year: Year) =
        fiscalYears.computeIfAbsent(year) {
            FiscalYear(currencyExchange, year, eventPublisher)  // MODIFIED: add eventPublisher
        }

    fun getAllSortedByYear() =
        fiscalYears.values.sortedBy { it.fiscalYear.value }

    fun reset() {
        fiscalYears.clear()
    }
}
```

#### 5. Create FiscalYearStateTracker Component

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/FiscalYearStateTracker.kt` (NEW)

```kotlin
package com.elchworks.tastyworkstaxcalculator.snapshot

import com.elchworks.tastyworkstaxcalculator.eur
import com.elchworks.tastyworkstaxcalculator.fiscalyear.OptionProfitLossUpdatedEvent
import com.elchworks.tastyworkstaxcalculator.fiscalyear.StockProfitLossUpdatedEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.ProfitAndLoss
import com.elchworks.tastyworkstaxcalculator.portfolio.plus
import org.javamoney.moneta.Money
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import javax.money.MonetaryAmount

@Component
class FiscalYearStateTracker(
    private val snapshotSerializer: SnapshotSerializer
) {
    private val log = LoggerFactory.getLogger(FiscalYearStateTracker::class.java)

    // Map of year -> fiscal year state
    private val fiscalYears = mutableMapOf<Int, FiscalYearState>()

    @EventListener(OptionProfitLossUpdatedEvent::class)
    fun onOptionProfitLossUpdated(event: OptionProfitLossUpdatedEvent) {
        val year = event.year.value
        val state = getOrCreateState(year)

        // Record the total from the event (no need to track deltas!)
        state.profitAndLossFromOptions = event.totalProfitAndLoss

        log.debug("Tracked option P/L for year {}: delta={}, total={}",
            year, event.profitLossDelta, event.totalProfitAndLoss)
    }

    @EventListener(StockProfitLossUpdatedEvent::class)
    fun onStockProfitLossUpdated(event: StockProfitLossUpdatedEvent) {
        val year = event.year.value
        val state = getOrCreateState(year)

        // Record the total from the event (no need to track deltas!)
        state.profitAndLossFromStocks = event.totalProfitAndLoss

        log.debug("Tracked stock P/L for year {}: delta={}, total={}",
            year, event.profitLossDelta, event.totalProfitAndLoss)
    }

    fun getFiscalYearsSnapshot(): Map<Int, FiscalYearSnapshot> {
        log.debug("Creating fiscal years snapshot from tracker: yearCount={}", fiscalYears.size)
        return fiscalYears.mapValues { (year, state) ->
            FiscalYearSnapshot(
                year = year,
                profitAndLossFromOptions = ProfitAndLossSnapshot(
                    profit = snapshotSerializer.serializeMonetaryAmount(state.profitAndLossFromOptions.profit),
                    loss = snapshotSerializer.serializeMonetaryAmount(state.profitAndLossFromOptions.loss)
                ),
                profitAndLossFromStocks = snapshotSerializer.serializeMonetaryAmount(state.profitAndLossFromStocks)
            )
        }
    }

    fun reset() {
        log.debug("Resetting fiscal year state tracker")
        fiscalYears.clear()
    }

    fun restoreFrom(snapshot: Map<Int, FiscalYearSnapshot>) {
        reset()

        snapshot.forEach { (year, fiscalYearSnapshot) ->
            val state = FiscalYearState(
                profitAndLossFromOptions = ProfitAndLoss(
                    profit = Money.of(
                        fiscalYearSnapshot.profitAndLossFromOptions.profit.amount,
                        fiscalYearSnapshot.profitAndLossFromOptions.profit.currency
                    ),
                    loss = Money.of(
                        fiscalYearSnapshot.profitAndLossFromOptions.loss.amount,
                        fiscalYearSnapshot.profitAndLossFromOptions.loss.currency
                    )
                ),
                profitAndLossFromStocks = Money.of(
                    fiscalYearSnapshot.profitAndLossFromStocks.amount,
                    fiscalYearSnapshot.profitAndLossFromStocks.currency
                )
            )
            fiscalYears[year] = state
        }

        log.debug("Restored fiscal year state tracker: yearCount={}", fiscalYears.size)
    }

    private fun getOrCreateState(year: Int): FiscalYearState {
        return fiscalYears.computeIfAbsent(year) {
            FiscalYearState(
                profitAndLossFromOptions = ProfitAndLoss(eur(0), eur(0)),
                profitAndLossFromStocks = eur(0)
            )
        }
    }

    private data class FiscalYearState(
        var profitAndLossFromOptions: ProfitAndLoss,
        var profitAndLossFromStocks: MonetaryAmount
    )
}
```

### Success Criteria

#### Automated Verification:
- [ ] All tests pass: `./gradlew test`
- [ ] Type checking passes: `./gradlew compileKotlin`
- [ ] FiscalYearStateTracker compiles without errors

#### Manual Verification:
- [ ] Application runs successfully
- [ ] Debug logs show fiscal year tracker receiving events
- [ ] Tracker calculates profit/loss correctly
- [ ] No impact on existing tax reports

**Implementation Note**: After completing this phase and all automated verification passes, confirm the manual testing was successful before proceeding to Phase 4.

---

## Phase 4: Validate State Trackers Produce Identical State

### Overview
Before switching to the new event-based approach, we need to validate that the state trackers produce exactly the same snapshot data as the current implementation. This phase adds validation logic but doesn't change the production behavior yet.

### Changes Required

#### 1. Add Validation to SnapshotSerializer

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/SnapshotSerializer.kt`

**Changes**: Modify `createSnapshot` method to validate trackers against current approach

```kotlin
fun createSnapshot(
    portfolio: Portfolio,
    fiscalYearRepository: FiscalYearRepository,
    lastTransactionDate: Instant,
    portfolioStateTracker: PortfolioStateTracker? = null,  // NEW: Optional for validation
    fiscalYearStateTracker: FiscalYearStateTracker? = null  // NEW: Optional for validation
): StateSnapshot {
    log.info("Creating snapshot with lastTransactionDate={}", lastTransactionDate)

    val portfolioSnapshot = serializePortfolio(portfolio)
    val fiscalYearsSnapshot = serializeFiscalYears(fiscalYearRepository)

    // NEW: Validation logic
    if (portfolioStateTracker != null && fiscalYearStateTracker != null) {
        val trackerPortfolioSnapshot = portfolioStateTracker.getPortfolioSnapshot()
        val trackerFiscalYearsSnapshot = fiscalYearStateTracker.getFiscalYearsSnapshot()

        validatePortfolioSnapshots(portfolioSnapshot, trackerPortfolioSnapshot)
        validateFiscalYearSnapshots(fiscalYearsSnapshot, trackerFiscalYearsSnapshot)

        log.info("✓ State tracker validation passed - snapshots are identical")
    }

    return StateSnapshot(
        metadata = SnapshotMetadata(
            createdAt = Instant.now(),
            lastTransactionDate = lastTransactionDate
        ),
        portfolio = portfolioSnapshot,
        fiscalYears = fiscalYearsSnapshot
    )
}

// NEW: Validation methods
private fun validatePortfolioSnapshots(
    current: PortfolioSnapshot,
    tracker: PortfolioSnapshot
) {
    if (current != tracker) {
        log.error("Portfolio snapshots differ!")
        log.error("Current option keys: {}", current.optionPositions.keys)
        log.error("Tracker option keys: {}", tracker.optionPositions.keys)
        log.error("Current stock keys: {}", current.stockPositions.keys)
        log.error("Tracker stock keys: {}", tracker.stockPositions.keys)
        throw IllegalStateException("Portfolio state tracker validation failed - snapshots differ")
    }
}

private fun validateFiscalYearSnapshots(
    current: Map<Int, FiscalYearSnapshot>,
    tracker: Map<Int, FiscalYearSnapshot>
) {
    if (current != tracker) {
        log.error("Fiscal year snapshots differ!")
        log.error("Current years: {}", current.keys)
        log.error("Tracker years: {}", tracker.keys)
        current.forEach { (year, snapshot) ->
            val trackerSnapshot = tracker[year]
            if (snapshot != trackerSnapshot) {
                log.error("Year {} differs:", year)
                log.error("  Current: {}", snapshot)
                log.error("  Tracker: {}", trackerSnapshot)
            }
        }
        throw IllegalStateException("Fiscal year state tracker validation failed - snapshots differ")
    }
}
```

#### 2. Update ApplicationRunner to Pass Trackers for Validation

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/ApplicationRunner.kt`

**Changes**: Inject state trackers and pass them to createSnapshot for validation

```kotlin
@Component
class ApplicationRunner(
    // ... existing dependencies ...
    private val snapshotFileService: SnapshotFileService,
    private val snapshotSerializer: SnapshotSerializer,
    private val snapshotDeserializer: SnapshotDeserializer,
    private val portfolioStateTracker: PortfolioStateTracker,  // NEW
    private val fiscalYearStateTracker: FiscalYearStateTracker  // NEW
) : CommandLineRunner {

    override fun run(vararg args: String) {
        // ... existing code for loading snapshot ...

        if (snapshot != null) {
            snapshotDeserializer.restoreState(snapshot, portfolio, fiscalYearRepository)
            // NEW: Also restore state trackers
            portfolioStateTracker.restoreFrom(snapshot.portfolio)
            fiscalYearStateTracker.restoreFrom(snapshot.fiscalYears)
            log.info("Resumed from snapshot. Last transaction: {}", snapshot.metadata.lastTransactionDate)
        } else {
            log.info("No snapshot found. Processing all transactions from scratch.")
        }

        // ... existing transaction processing code ...

        // MODIFIED: Pass trackers for validation
        if (lastTransactionDate != null) {
            log.debug("Creating new snapshot with lastTransactionDate: {}", lastTransactionDate)
            val newSnapshot = snapshotSerializer.createSnapshot(
                portfolio = portfolio,
                fiscalYearRepository = fiscalYearRepository,
                lastTransactionDate = lastTransactionDate!!,
                portfolioStateTracker = portfolioStateTracker,  // NEW
                fiscalYearStateTracker = fiscalYearStateTracker  // NEW
            )
            snapshotFileService.saveSnapshot(newSnapshot, transactionsDir)
        }
    }
}
```

### Success Criteria

#### Automated Verification:
- [ ] All tests pass: `./gradlew test`
- [ ] Application compiles: `./gradlew compileKotlin`

#### Manual Verification:
- [ ] Run application with various transaction files
- [ ] Verify log shows "✓ State tracker validation passed"
- [ ] No validation errors thrown
- [ ] Snapshot files created successfully

**Implementation Note**: This is a critical validation phase. If validation fails, it indicates the event trackers have bugs that must be fixed before proceeding. Do NOT proceed to Phase 5 until validation consistently passes for all test scenarios.

---

## Phase 5: Switch to Event-Based Serialization

### Overview
Now that we've validated the state trackers produce identical snapshots, we can switch the serializer to use the trackers as the source of truth instead of reading from Portfolio's internal state.

### Changes Required

#### 1. Modify SnapshotSerializer to Use Trackers

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/SnapshotSerializer.kt`

**Changes**: Change `createSnapshot` to use trackers instead of domain objects

```kotlin
fun createSnapshot(
    portfolio: Portfolio,  // Keep for backward compatibility during migration
    fiscalYearRepository: FiscalYearRepository,  // Keep for backward compatibility
    lastTransactionDate: Instant,
    portfolioStateTracker: PortfolioStateTracker,  // Now REQUIRED
    fiscalYearStateTracker: FiscalYearStateTracker  // Now REQUIRED
): StateSnapshot {
    log.info("Creating snapshot with lastTransactionDate={}", lastTransactionDate)

    // NEW: Use trackers as source of truth
    val portfolioSnapshot = portfolioStateTracker.getPortfolioSnapshot()
    val fiscalYearsSnapshot = fiscalYearStateTracker.getFiscalYearsSnapshot()

    return StateSnapshot(
        metadata = SnapshotMetadata(
            createdAt = Instant.now(),
            lastTransactionDate = lastTransactionDate
        ),
        portfolio = portfolioSnapshot,
        fiscalYears = fiscalYearsSnapshot
    )
}

// REMOVE or DEPRECATE: serializePortfolio, serializeFiscalYears methods
// (Keep them if needed for tests, mark as @Deprecated)
```

#### 2. Update Method Signatures in ApplicationRunner

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/ApplicationRunner.kt`

**Changes**: Remove optional parameters from createSnapshot call (now required)

```kotlin
val newSnapshot = snapshotSerializer.createSnapshot(
    portfolio = portfolio,
    fiscalYearRepository = fiscalYearRepository,
    lastTransactionDate = lastTransactionDate!!,
    portfolioStateTracker = portfolioStateTracker,
    fiscalYearStateTracker = fiscalYearStateTracker
)
```

### Success Criteria

#### Automated Verification:
- [ ] All tests pass: `./gradlew test`
- [ ] Application compiles: `./gradlew compileKotlin`

#### Manual Verification:
- [ ] Application runs successfully
- [ ] Snapshots are created correctly
- [ ] Application can resume from snapshots
- [ ] Tax reports remain identical to previous runs

**Implementation Note**: After completing this phase and all automated verification passes, confirm the manual testing was successful before proceeding to Phase 6.

---

## Phase 6: Add Restoration Events

### Overview
Replace the current restoration approach (which uses `internal` getters) with restoration events. Portfolio and FiscalYear will handle their own restoration internally and publish events so state trackers can restore automatically. This achieves perfect encapsulation - no `internal` getters needed.

### Changes Required

#### 1. Create Restoration Events

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/RestorationEvents.kt` (NEW)

```kotlin
package com.elchworks.tastyworkstaxcalculator.snapshot

/**
 * Published by Portfolio when its state is restored from a snapshot.
 * State trackers listen to this event to restore their own state.
 */
data class PortfolioStateRestoredEvent(
    val portfolioSnapshot: PortfolioSnapshot
)

/**
 * Published by FiscalYear when its state is restored from a snapshot.
 * State trackers listen to this event to restore their own state.
 */
data class FiscalYearStateRestoredEvent(
    val fiscalYearSnapshot: FiscalYearSnapshot
)
```

#### 2. Add Portfolio.restoreFrom() Method

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/portfolio/Portfolio.kt`

**Changes**: Add restoration method that publishes event

```kotlin
fun restoreFrom(snapshot: PortfolioSnapshot) {
    // Sanity check: restoration should only happen at startup
    if (optionPositions.isNotEmpty() || stockPositions.isNotEmpty()) {
        throw IllegalStateException(
            "Portfolio restoration attempted with non-empty positions! " +
            "Restoration should only happen at application startup before processing transactions. " +
            "Current state: ${optionPositions.size} option position keys, ${stockPositions.size} stock position keys"
        )
    }

    log.info("Restoring portfolio from snapshot: {} option position keys, {} stock position keys",
        snapshot.optionPositions.size, snapshot.stockPositions.size)

    // Restore option positions (accessing private field directly - no getters needed!)
    snapshot.optionPositions.forEach { (key, positions) ->
        val queue: Queue<OptionShortPosition> = LinkedList()
        positions.forEach { posSnapshot ->
            queue.offer(deserializeOptionPosition(posSnapshot))
        }
        optionPositions[key] = queue
    }

    // Restore stock positions
    snapshot.stockPositions.forEach { (symbol, positions) ->
        val queue: Queue<StockPosition> = LinkedList()
        positions.forEach { posSnapshot ->
            queue.offer(deserializeStockPosition(posSnapshot))
        }
        stockPositions[symbol] = queue
    }

    // Publish event so state trackers can restore
    eventPublisher.publishEvent(PortfolioStateRestoredEvent(snapshot))

    log.debug("Portfolio restoration complete")
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
        action = Action.SELL_TO_OPEN,
        symbol = snapshot.symbol,
        callOrPut = snapshot.callOrPut,
        expirationDate = LocalDate.parse(snapshot.expirationDate),
        strikePrice = Money.of(snapshot.strikePrice.amount, snapshot.strikePrice.currency),
        quantity = snapshot.quantity,
        averagePrice = Money.of(snapshot.averagePrice.amount, snapshot.averagePrice.currency),
        description = snapshot.description,
        commissions = Money.of(snapshot.commissions.amount, snapshot.commissions.currency),
        fees = Money.of(snapshot.fees.amount, snapshot.fees.currency),
        instrumentType = "Equity Option",
        value = Money.of(snapshot.averagePrice.amount, snapshot.averagePrice.currency)
            .multiply(snapshot.quantity).multiply(100),
        multiplier = 100,
        underlyingSymbol = snapshot.symbol,
        orderNr = 0
    )
}

private fun deserializeStockTransaction(snapshot: StockTransactionSnapshot): StockTrade {
    return StockTrade(
        date = snapshot.date,
        action = Action.BUY_TO_OPEN,
        symbol = snapshot.symbol,
        type = snapshot.type,
        value = Money.of(snapshot.value.amount, snapshot.value.currency),
        quantity = snapshot.quantity,
        averagePrice = Money.of(snapshot.averagePrice.amount, snapshot.averagePrice.currency),
        description = snapshot.description,
        commissions = Money.of(snapshot.commissions.amount, snapshot.commissions.currency),
        fees = Money.of(snapshot.fees.amount, snapshot.fees.currency)
    )
}
```

**Note**: Need to add imports for `OptionPositionSnapshot`, `StockPositionSnapshot`, `OptionTradeSnapshot`, `StockTransactionSnapshot`, `Action`, `OptionTrade`, `StockTrade`, `Money`, `LocalDate`.

#### 3. Update FiscalYear.restoreState() to Publish Event

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/fiscalyear/FiscalYear.kt`

**Changes**: Modify existing `restoreState` method to publish event

```kotlin
fun restoreState(
    profitAndLossFromOptions: ProfitAndLoss,
    profitAndLossFromStocks: MonetaryAmount
) {
    this.profitAndLossFromOptions = profitAndLossFromOptions
    this.profitAndLossFromStocks = profitAndLossFromStocks

    // Publish event so state trackers can restore
    eventPublisher.publishEvent(
        FiscalYearStateRestoredEvent(
            fiscalYearSnapshot = FiscalYearSnapshot(
                year = fiscalYear.value,
                profitAndLossFromOptions = ProfitAndLossSnapshot(
                    profit = MonetaryAmountSnapshot(
                        amount = profitAndLossFromOptions.profit.number.doubleValueExact(),
                        currency = profitAndLossFromOptions.profit.currency.currencyCode
                    ),
                    loss = MonetaryAmountSnapshot(
                        amount = profitAndLossFromOptions.loss.number.doubleValueExact(),
                        currency = profitAndLossFromOptions.loss.currency.currencyCode
                    )
                ),
                profitAndLossFromStocks = MonetaryAmountSnapshot(
                    amount = profitAndLossFromStocks.number.doubleValueExact(),
                    currency = profitAndLossFromStocks.currency.currencyCode
                )
            )
        )
    )

    log.debug("Restored fiscal year {} state: optionProfit={}, optionLoss={}, stockProfit={}",
        fiscalYear,
        format(profitAndLossFromOptions.profit),
        format(profitAndLossFromOptions.loss),
        format(profitAndLossFromStocks))
}
```

**Note**: Need to add imports for snapshot classes.

#### 4. Add Event Listeners to State Trackers

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/PortfolioStateTracker.kt`

**Changes**: Add listener for restoration event

```kotlin
@EventListener(PortfolioStateRestoredEvent::class)
fun onPortfolioRestored(event: PortfolioStateRestoredEvent) {
    log.info("Portfolio state restored event received, restoring tracker state")
    restoreFrom(event.portfolioSnapshot)
}
```

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/FiscalYearStateTracker.kt`

**Changes**: Add listener for restoration event

```kotlin
@EventListener(FiscalYearStateRestoredEvent::class)
fun onFiscalYearRestored(event: FiscalYearStateRestoredEvent) {
    val snapshot = event.fiscalYearSnapshot
    log.debug("Fiscal year state restored event received for year {}", snapshot.year)

    val state = FiscalYearState(
        profitAndLossFromOptions = ProfitAndLoss(
            profit = Money.of(
                snapshot.profitAndLossFromOptions.profit.amount,
                snapshot.profitAndLossFromOptions.profit.currency
            ),
            loss = Money.of(
                snapshot.profitAndLossFromOptions.loss.amount,
                snapshot.profitAndLossFromOptions.loss.currency
            )
        ),
        profitAndLossFromStocks = Money.of(
            snapshot.profitAndLossFromStocks.amount,
            snapshot.profitAndLossFromStocks.currency
        )
    )
    fiscalYears[snapshot.year] = state
}
```

#### 5. Simplify SnapshotDeserializer

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/SnapshotDeserializer.kt`

**Changes**: Delegate to domain objects instead of directly manipulating internal state

```kotlin
fun restoreState(
    snapshot: StateSnapshot,
    portfolio: Portfolio,
    fiscalYearRepository: FiscalYearRepository
) {
    log.info("Restoring state from snapshot. lastTransactionDate={}", snapshot.metadata.lastTransactionDate)

    // Restore portfolio - it will publish event for trackers
    portfolio.restoreFrom(snapshot.portfolio)

    // Restore fiscal years - they will publish events for trackers
    restoreFiscalYears(snapshot.fiscalYears, fiscalYearRepository)

    log.info("State restoration complete")
}

private fun restoreFiscalYears(
    fiscalYearsSnapshot: Map<Int, FiscalYearSnapshot>,
    repository: FiscalYearRepository
) {
    repository.reset() // Clear any existing state

    fiscalYearsSnapshot.forEach { (yearValue, snapshot) ->
        val fiscalYear = repository.getFiscalYear(Year.of(yearValue))
        fiscalYear.restoreState(
            profitAndLossFromOptions = ProfitAndLoss(
                profit = Money.of(
                    snapshot.profitAndLossFromOptions.profit.amount,
                    snapshot.profitAndLossFromOptions.profit.currency
                ),
                loss = Money.of(
                    snapshot.profitAndLossFromOptions.loss.amount,
                    snapshot.profitAndLossFromOptions.loss.currency
                )
            ),
            profitAndLossFromStocks = Money.of(
                snapshot.profitAndLossFromStocks.amount,
                snapshot.profitAndLossFromStocks.currency
            )
        )
    }

    log.debug("Restored {} fiscal years", fiscalYearsSnapshot.size)
}

// REMOVE: All the deserialize* methods - now in Portfolio
```

#### 6. Simplify ApplicationRunner

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/ApplicationRunner.kt`

**Changes**: Remove manual tracker restoration - happens automatically via events

```kotlin
if (snapshot != null) {
    snapshotDeserializer.restoreState(snapshot, portfolio, fiscalYearRepository)
    // REMOVE: portfolioStateTracker.restoreFrom() - happens via event now
    // REMOVE: fiscalYearStateTracker.restoreFrom() - happens via event now
    log.info("Resumed from snapshot. Last transaction: {}", snapshot.metadata.lastTransactionDate)
} else {
    log.info("No snapshot found. Processing all transactions from scratch.")
}
```

#### 7. Remove Internal Getters from Portfolio

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/portfolio/Portfolio.kt`

**Changes**: Delete lines 68-69

```kotlin
// DELETE THESE LINES (no longer needed!):
internal fun getOptionPositionsMap(): MutableMap<String, Queue<OptionShortPosition>> = optionPositions
internal fun getStockPositionsMap(): MutableMap<String, Queue<StockPosition>> = stockPositions
```

### Success Criteria

#### Automated Verification:
- [ ] All tests pass: `./gradlew test`
- [ ] Linting passes: `./gradlew build`
- [ ] No compilation errors: `./gradlew compileKotlin`
- [ ] Sanity check works: Attempting restoration with non-empty Portfolio throws IllegalStateException

#### Manual Verification:
- [ ] Application runs successfully
- [ ] Snapshot restoration works correctly at startup
- [ ] Restoration events published and trackers updated automatically
- [ ] Full end-to-end test with real transaction files
- [ ] Snapshots created and restored correctly
- [ ] Tax reports match baseline from before refactoring
- [ ] Code review confirms Portfolio has no `internal` getters
- [ ] Code review confirms perfect encapsulation achieved

**Implementation Note**: This completes the refactoring! After all verification passes, the implementation is complete. Portfolio and FiscalYear now have zero persistence dependencies, and all state tracking happens via events.

---

## Testing Strategy

### Unit Tests

**New Test Files**:
- `PortfolioStateTrackerTest.kt` - Test event handling and state maintenance
- `FiscalYearStateTrackerTest.kt` - Test profit/loss calculations

**Test Scenarios**:
- Opening positions (option STO, stock BTO)
- Closing positions fully (single transaction)
- Closing positions partially (multiple transactions)
- Position removal (assignment, expiration)
- FIFO order preservation
- State reset and restoration

**Example Test Structure**:
```kotlin
@Test
fun `option position opened event updates tracker state`() {
    // Given
    val tracker = PortfolioStateTracker(snapshotSerializer)
    val stoTx = randomOptionSto()

    // When
    tracker.onOptionOpened(OptionSellToOpenEvent(stoTx))

    // Then
    val snapshot = tracker.getPortfolioSnapshot()
    assertThat(snapshot.optionPositions).hasSize(1)
    assertThat(snapshot.optionPositions.values.first().first().quantityLeft)
        .isEqualTo(stoTx.quantity)
}
```

### Integration Tests

**Existing BDD Tests** (`src/test/resources/bdd/E2E.feature`):
- Should continue to pass without modification
- Validates end-to-end behavior remains unchanged

**New BDD Scenarios** (if needed):
- Snapshot validation across different transaction sequences
- State restoration after application restart

### Manual Testing Steps

1. **Baseline Capture** (before refactoring):
   - Run application with production transaction files
   - Save output reports
   - Save generated snapshot files

2. **Incremental Validation** (after each phase):
   - Run application with same transaction files
   - Compare output reports to baseline
   - Compare snapshot file contents

3. **State Restoration Test**:
   - Run application to create snapshot
   - Delete domain state
   - Restart application
   - Verify it resumes correctly from snapshot

4. **Edge Cases**:
   - Empty transaction files
   - Single transaction
   - Large transaction files (1000+ transactions)
   - Positions that span multiple fiscal years

## Performance Considerations

### Memory Overhead

**Current Approach**:
- Domain objects: Portfolio maintains position maps
- Persistence: Serializer reads maps on-demand

**Event-Based Approach**:
- Domain objects: Portfolio maintains position maps (unchanged)
- State trackers: Maintain snapshot-ready copies of positions
- **Additional memory**: ~2x position data (domain + tracker)

**Impact Assessment**:
- For typical usage (100-1000 positions): Negligible (~few KB additional memory)
- Position data easily fits in memory for this application
- No performance degradation expected

### Event Processing Overhead

**Additional Work Per Event**:
- PortfolioStateTracker receives and processes event
- FiscalYearStateTracker receives and processes event
- Updates in-memory snapshot data structures

**Mitigation**:
- Event processing is synchronous and fast (in-memory updates)
- No I/O or complex calculations
- FiscalYearManager already demonstrates this pattern works well

## Migration Notes

### Backward Compatibility

**Snapshot File Format**:
- No changes to JSON structure
- Existing snapshot files remain valid
- Old and new implementations produce identical snapshots

**Domain Model**:
- Portfolio business logic unchanged
- FiscalYear business logic unchanged
- All events remain the same (except new StockBuyToOpenEvent)

### Rollback Strategy

If issues arise during migration:

**After Phase 1-3**: Simply don't use the state trackers yet. Existing code still works.

**After Phase 4**: Validation failures will prevent proceeding. Fix trackers before continuing.

**After Phase 5**: If problems detected, can temporarily revert:
1. Restore `internal` getters in Portfolio.kt
2. Revert SnapshotSerializer to use old methods
3. State trackers can remain (not actively used)

**After Phase 6**: Full rollback requires:
1. Restore deleted `internal` getters
2. Restore old serialization methods
3. Update ApplicationRunner to old signature

## References

- Original research: `thoughts/shared/research/2025-10-20-portfolio-fiscal-year-persistence.md`
- Portfolio implementation: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/portfolio/Portfolio.kt`
- Current serializer: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/SnapshotSerializer.kt`
- Current deserializer: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/SnapshotDeserializer.kt`
- FiscalYearManager pattern: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/fiscalyear/FiscalYearManager.kt`
- Snapshot models: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/SnapshotModels.kt`
