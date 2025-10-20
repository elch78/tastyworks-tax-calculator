---
date: 2025-10-20T16:20:35+00:00
researcher: Claude
git_commit: 60997a9b4b244bfe8a499f6b5177ded65b226e11
branch: master
repository: tastyworks-tax-calculator
topic: "Portfolio and FiscalYear State Persistence Implementation"
tags: [research, codebase, persistence, snapshot, portfolio, fiscal-year, serialization]
status: complete
last_updated: 2025-10-20
last_updated_by: Claude
---

# Research: Portfolio and FiscalYear State Persistence Implementation

**Date**: 2025-10-20T16:20:35+00:00
**Researcher**: Claude
**Git Commit**: 60997a9b4b244bfe8a499f6b5177ded65b226e11
**Branch**: master
**Repository**: tastyworks-tax-calculator

## Research Question

How is the recently added portfolio and fiscal year state persistence feature currently implemented, and how are responsibilities distributed across the codebase?

## Summary

The portfolio and fiscal year state persistence is implemented through a dedicated `snapshot` package that encapsulates serialization, deserialization, and file I/O operations. The implementation follows a clean separation of concerns where:

- **Domain objects** (Portfolio, FiscalYear) have minimal persistence responsibilities - they only expose state extraction methods (`profits()`) and state restoration methods (`restoreState()`)
- **Snapshot package** contains all persistence logic in three specialized components: `SnapshotSerializer`, `SnapshotDeserializer`, and `SnapshotFileService`
- **ApplicationRunner** orchestrates persistence by loading snapshots at startup and saving them after transaction processing
- **Snapshot models** (`SnapshotModels.kt`) define immutable data transfer objects separate from domain models

The persistence mechanism saves complete application state to JSON files in `{transactionsDir}/snapshots/` with filenames like `snapshot-2024-01-15-143022.json`. This enables resuming processing from the last processed transaction.

## Detailed Findings

### Component Responsibilities

#### Portfolio Domain (`src/main/kotlin/com/elchworks/tastyworkstaxcalculator/portfolio/`)

The Portfolio class has minimal direct persistence responsibilities:

**State Exposure** (`Portfolio.kt:68-69`):
```kotlin
internal fun getOptionPositionsMap(): MutableMap<String, Queue<OptionShortPosition>> = optionPositions
internal fun getStockPositionsMap(): MutableMap<String, Queue<StockPosition>> = stockPositions
```
- Provides internal accessor methods to expose position maps for serialization
- Called by SnapshotSerializer at lines 38 and 43

**State Reset** (`Portfolio.kt:63-66`):
```kotlin
fun reset() {
    optionPositions.clear()
    stockPositions.clear()
}
```
- Clears internal state before restoration
- Called by SnapshotDeserializer at line 37

#### FiscalYear Domain (`src/main/kotlin/com/elchworks/tastyworkstaxcalculator/fiscalyear/`)

**FiscalYearManager** (`FiscalYearManager.kt`):
- Has **no direct persistence responsibilities**
- Operates as stateless event listener and report generator
- Delegates to FiscalYearRepository for fiscal year instances

**FiscalYearRepository** (`FiscalYearRepository.kt:8-18`):
- Maintains runtime map of fiscal year instances
- Provides `getAllSortedByYear()` at line 14 for serialization
- Provides `reset()` at lines 15-17 for clearing state during restoration
- Creates FiscalYear instances lazily via `computeIfAbsent` at line 13

**FiscalYear** (`FiscalYear.kt`):
- Maintains private state fields at lines 28-29: `profitAndLossFromOptions` and `profitAndLossFromStocks`
- Exposes state via `profits()` method at lines 32-36 returning `ProfitsSummary`
- Restores state via `restoreState()` method at lines 38-49 accepting profit/loss data
- Logs restored values at debug level at lines 44-48

#### Snapshot Package (`src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/`)

**SnapshotModels.kt** - Defines immutable data classes for serialization:
- `StateSnapshot` (lines 61-65) - Root container with metadata, portfolio, and fiscal years
- `PortfolioSnapshot` (lines 5-8) - Maps of option and stock position snapshots
- `OptionPositionSnapshot` (lines 10-13) - Option position state with STO transaction and quantity
- `StockPositionSnapshot` (lines 15-18) - Stock position state with BTO transaction and quantity
- `FiscalYearSnapshot` (lines 50-54) - Year value with profit/loss data
- `ProfitAndLossSnapshot` (lines 56-59) - Profit and loss amounts
- `MonetaryAmountSnapshot` (lines 45-48) - Simple amount + currency representation
- `SnapshotMetadata` (lines 67-72) - Version, timestamps, git commit

**SnapshotSerializer.kt** - Converts domain objects to snapshots:
- `createSnapshot()` (lines 19-34) - Entry point creating complete StateSnapshot
- `serializePortfolio()` (lines 36-51) - Accesses Portfolio maps and transforms Queues to Lists
- `serializeFiscalYears()` (lines 115-122) - Iterates all fiscal years from repository
- `serializeFiscalYear()` (lines 124-134) - Calls `fiscalYear.profits()` to extract state
- Various helper methods for serializing positions, trades, and monetary amounts

**SnapshotDeserializer.kt** - Restores domain objects from snapshots:
- `restoreState()` (lines 23-34) - Entry point orchestrating restoration
- `restorePortfolio()` (lines 36-59) - Clears Portfolio, creates LinkedList queues, assigns to maps
- `restoreFiscalYears()` (lines 116-128) - Clears repository, creates/restores fiscal years
- `restoreFiscalYear()` (lines 130-138) - Calls `fiscalYear.restoreState()` with deserialized data
- Various helper methods for deserializing positions, trades, and monetary amounts

**SnapshotFileService.kt** - Handles file I/O:
- `saveSnapshot()` (lines 20-30) - Writes JSON to `snapshots/` directory with timestamp filename
- `loadLatestSnapshot()` (lines 32-47) - Finds and loads most recent snapshot file
- `generateFilename()` (lines 53-56) - Creates filename: `snapshot-{yyyy-MM-dd-HHmmss}.json`
- `findLatestSnapshotFile()` (lines 58-65) - Sorts snapshot files by name descending
- Uses Jackson ObjectMapper for JSON serialization/deserialization

**JacksonConfig.kt** - Configures JSON serialization:
- Creates ObjectMapper bean (lines 13-19)
- Registers Kotlin module for data class support
- Registers JavaTimeModule for Instant/LocalDate
- Enables indented output
- Uses ISO-8601 string format for dates

#### ApplicationRunner Integration (`src/main/kotlin/com/elchworks/tastyworkstaxcalculator/ApplicationRunner.kt`)

**Dependency Injection** (lines 24-26):
```kotlin
private val snapshotFileService: SnapshotFileService,
private val snapshotSerializer: SnapshotSerializer,
private val snapshotDeserializer: SnapshotDeserializer
```

**Snapshot Loading at Startup** (lines 34-40):
```kotlin
val snapshot = snapshotFileService.loadLatestSnapshot(transactionsDir)
if (snapshot != null) {
    snapshotDeserializer.restoreState(snapshot, portfolio, fiscalYearRepository)
    log.info("Resumed from snapshot. Last transaction: {}", snapshot.metadata.lastTransactionDate)
} else {
    log.info("No snapshot found. Processing all transactions from scratch.")
}
```

**Transaction Filtering** (line 45):
- Excludes files in `/snapshots/` directory when reading transaction CSVs

**Chronological Validation** (lines 59, 89-119):
- Ensures first new transaction is after snapshot's last transaction date
- Throws `IllegalArgumentException` with detailed error message if violated
- Suggests deleting snapshot files to start fresh

**Snapshot Saving After Processing** (lines 78-86):
```kotlin
if (lastTransactionDate != null) {
    log.debug("Creating new snapshot with lastTransactionDate: {}", lastTransactionDate)
    val newSnapshot = snapshotSerializer.createSnapshot(
        portfolio = portfolio,
        fiscalYearRepository = fiscalYearRepository,
        lastTransactionDate = lastTransactionDate!!
    )
    snapshotFileService.saveSnapshot(newSnapshot, transactionsDir)
}
```

### Data Flow

#### Persistence Flow (Saving State):

1. `ApplicationRunner.run()` processes all transactions (lines 62-68)
2. Tracks `lastTransactionDate` for each transaction (line 67)
3. After report generation, checks if transactions were processed (line 78)
4. Calls `snapshotSerializer.createSnapshot()` with portfolio, repository, and date (lines 79-83)
5. SnapshotSerializer accesses Portfolio maps via `getOptionPositionsMap()` and `getStockPositionsMap()`
6. Transforms `Queue<Position>` to `List<PositionSnapshot>` for each position key
7. Calls `fiscalYearRepository.getAllSortedByYear()` to get all fiscal years (line 116)
8. For each fiscal year, calls `fiscalYear.profits()` to extract state (line 125)
9. Creates `StateSnapshot` with metadata including last transaction date
10. Calls `snapshotFileService.saveSnapshot()` which writes JSON to filesystem (line 83)
11. File saved to `{transactionsDir}/snapshots/snapshot-{timestamp}.json`

#### Restoration Flow (Loading State):

1. `ApplicationRunner.run()` starts execution (line 30)
2. Calls `snapshotFileService.loadLatestSnapshot()` (line 34)
3. SnapshotFileService finds latest file in snapshots directory by sorting names
4. Jackson ObjectMapper deserializes JSON to `StateSnapshot`
5. If snapshot exists, calls `snapshotDeserializer.restoreState()` (line 36)
6. Calls `portfolio.reset()` to clear existing state (line 37)
7. For each position key in snapshot, creates new `LinkedList<Position>` queue
8. Deserializes positions and adds to queues
9. Directly assigns queues to Portfolio's internal maps (lines 45, 54)
10. Calls `fiscalYearRepository.reset()` to clear state (line 120)
11. For each fiscal year snapshot, gets/creates FiscalYear via repository (line 123)
12. Calls `fiscalYear.restoreState()` to populate private fields (line 124)
13. Logs restoration complete with last transaction date
14. Transaction processing continues with chronological validation

### Data Being Persisted

**Portfolio State:**
- Option positions: `Map<String, Queue<OptionShortPosition>>`
  - Key format: "{callOrPut}-{symbol}-{expirationDate}-{strikePrice}" (e.g., "Put-CLF-2024-01-15-13.50 USD")
  - Per position: original STO transaction, remaining quantity
- Stock positions: `Map<String, Queue<StockPosition>>`
  - Key: stock symbol
  - Per position: original BTO transaction, remaining quantity

**Option Position Data:**
- Transaction date (Instant as ISO-8601 string)
- Symbol, call/put indicator
- Expiration date (as ISO string)
- Strike price (amount + currency)
- Original quantity, quantity left
- Average price (amount + currency)
- Description, commissions, fees

**Stock Position Data:**
- Transaction date (Instant as ISO-8601 string)
- Symbol, transaction type
- Original quantity, quantity left
- Average price (amount + currency)
- Transaction value (amount + currency)
- Description, commissions, fees

**Fiscal Year Data:**
- Year value (integer)
- Profit/Loss from options (profit amount + loss amount, both in EUR)
- Profit/Loss from stocks (single amount in EUR)

**Metadata:**
- Snapshot format version ("1.0")
- Creation timestamp (Instant)
- Last processed transaction date (Instant)
- Optional git commit hash

### Architecture Patterns

**Separation of Concerns:**
- Domain logic completely separated from persistence logic
- Domain objects expose minimal interface for persistence (state extraction and restoration)
- Snapshot package encapsulates all serialization/deserialization logic
- ApplicationRunner coordinates lifecycle but doesn't implement persistence

**Data Transfer Object (DTO) Pattern:**
- Snapshot models are pure data classes separate from domain models
- Simple types (primitives, strings, lists, maps) suitable for JSON
- Domain can evolve independently from persistence format

**Serializer/Deserializer Pattern:**
- Dedicated components for bidirectional transformation
- SnapshotSerializer converts domain → DTOs
- SnapshotDeserializer converts DTOs → domain
- Symmetrical operations with logging at each level

**Repository Pattern:**
- FiscalYearRepository provides `reset()` for clearing state
- Repository provides `getAllSortedByYear()` for iteration during serialization
- Repository provides `getFiscalYear()` for lazy creation during deserialization
- Maintains encapsulation of fiscal year collection

**Facade Pattern:**
- SnapshotFileService wraps Jackson ObjectMapper
- Provides high-level operations (save, load)
- Hides file naming conventions and directory structure
- Handles file system concerns

**Inversion of Control:**
- Portfolio exposes internal state through accessor methods
- Persistence components depend on Portfolio's interface
- Portfolio doesn't depend on persistence components
- Maintains domain model independence

**Snapshot Pattern:**
- Immutable snapshots represent state at a point in time
- Metadata includes version for future format evolution
- Chronological validation ensures consistency
- Multiple snapshots kept for historical reference

## Code References

### Core Implementation Files:
- `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/SnapshotModels.kt` - Snapshot data structures (lines 5-72)
- `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/SnapshotSerializer.kt` - Domain to snapshot conversion (lines 19-134)
- `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/SnapshotDeserializer.kt` - Snapshot to domain conversion (lines 23-138)
- `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/SnapshotFileService.kt` - File I/O operations (lines 20-65)
- `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/snapshot/JacksonConfig.kt` - JSON configuration (lines 13-19)

### Integration Points:
- `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/ApplicationRunner.kt:24-26` - Dependency injection
- `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/ApplicationRunner.kt:34-40` - Snapshot loading at startup
- `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/ApplicationRunner.kt:78-86` - Snapshot saving after processing
- `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/ApplicationRunner.kt:89-119` - Chronological validation

### Domain Object State Management:
- `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/portfolio/Portfolio.kt:63-66` - State reset
- `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/portfolio/Portfolio.kt:68-69` - State exposure
- `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/fiscalyear/FiscalYear.kt:32-36` - State extraction via `profits()`
- `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/fiscalyear/FiscalYear.kt:38-49` - State restoration via `restoreState()`
- `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/fiscalyear/FiscalYearRepository.kt:13-18` - Repository state management

### Test Files:
- `src/test/kotlin/com/elchworks/tastyworkstaxcalculator/test/StepDefinitions.kt:231-323` - BDD test step definitions
- `src/test/resources/bdd/Snapshots.feature` - BDD feature scenarios

## Architecture Documentation

### Current Patterns and Design

**Package Structure:**
```
com.elchworks.tastyworkstaxcalculator/
├── snapshot/                          # Persistence layer
│   ├── SnapshotModels.kt             # DTOs for serialization
│   ├── SnapshotSerializer.kt         # Domain → DTO conversion
│   ├── SnapshotDeserializer.kt       # DTO → Domain conversion
│   ├── SnapshotFileService.kt        # File I/O operations
│   └── JacksonConfig.kt              # JSON configuration
├── portfolio/                         # Domain model
│   ├── Portfolio.kt                  # Minimal persistence interface
│   └── ...
├── fiscalyear/                        # Domain model
│   ├── FiscalYear.kt                 # State extraction/restoration
│   ├── FiscalYearRepository.kt       # Repository with reset capability
│   └── FiscalYearManager.kt          # No persistence responsibility
└── ApplicationRunner.kt               # Lifecycle orchestration
```

**Dependency Direction:**
```
ApplicationRunner
    ↓
Snapshot Package (SnapshotFileService, Serializer, Deserializer)
    ↓
Domain Models (Portfolio, FiscalYear, FiscalYearRepository)
```
Domain models do not depend on snapshot package.

**State Management:**
- Portfolio maintains position maps as mutable internal state
- FiscalYear maintains profit/loss as private fields
- FiscalYearRepository maintains fiscal year map as internal state
- All provide reset capability for restoration
- All provide state extraction for serialization

**File System Layout:**
```
{transactionsDir}/
├── transaction-2024-01-15.csv
├── transaction-2024-02-20.csv
└── snapshots/
    ├── snapshot-2024-01-15-120000.json
    ├── snapshot-2024-02-20-143000.json
    └── snapshot-2024-03-10-095500.json
```

**Serialization Details:**
- Monetary amounts: Converted to amount + currency pairs using `doubleValueExact()` for precision
- Dates: Instant values as ISO-8601 strings via JavaTimeModule, LocalDate as explicit strings
- Collections: Queues flattened to Lists, order preserved, reconstructed as LinkedList
- Omitted data: Only opening transactions persisted (STO for options, BTO for stocks), closing transactions not included

**Chronological Validation:**
- ApplicationRunner validates first new transaction is after snapshot's last transaction date
- Prevents processing transactions out of order
- Throws exception with detailed error message if violated
- Ensures data consistency across sessions

**Graceful Degradation:**
- Returns null if no snapshot directory exists
- Returns null if no snapshot files found
- Processes all transactions from scratch if no snapshot
- Logs appropriate messages for each scenario

## Historical Context (from thoughts/)

No existing research documents found in thoughts/ directory for this specific implementation. This appears to be a recently added feature based on the user's statement.

## Related Research

No prior research documents found in `thoughts/shared/research/` for this topic.

## Alternative Architecture: Domain Writes to Persistence API

### The Encapsulation Trade-off

The current implementation requires breaking domain encapsulation to enable persistence:

**Portfolio.kt:68-69** - Internal state exposed for serialization:
```kotlin
internal fun getOptionPositionsMap(): MutableMap<String, Queue<OptionShortPosition>> = optionPositions
internal fun getStockPositionsMap(): MutableMap<String, Queue<StockPosition>> = stockPositions
```

This violates the principle that objects should encapsulate their internal state. An alternative approach would invert the dependency: domain objects would **write to** a persistence API instead of having persistence **read from** them.

### Alternative Approach: Visitor/Writer Pattern

Instead of exposing internal maps, domain objects could accept a writer/visitor:

**Conceptual API:**
```kotlin
interface SnapshotWriter {
    fun writeOptionPosition(key: String, position: OptionShortPosition)
    fun writeStockPosition(key: String, position: StockPosition)
    fun writeFiscalYear(year: Int, profits: ProfitsSummary)
}

// Portfolio.kt
class Portfolio {
    private val optionPositions = mutableMapOf<String, Queue<OptionShortPosition>>()
    private val stockPositions = mutableMapOf<String, Queue<StockPosition>>()

    // No internal getters needed!

    fun writeTo(writer: SnapshotWriter) {
        optionPositions.forEach { (key, queue) ->
            queue.forEach { position ->
                writer.writeOptionPosition(key, position)
            }
        }
        stockPositions.forEach { (key, queue) ->
            queue.forEach { position ->
                writer.writeStockPosition(key, position)
            }
        }
    }
}

// FiscalYear.kt
class FiscalYear {
    private var profitAndLossFromOptions = ProfitAndLoss()
    private var profitAndLossFromStocks = eur(0)

    // profits() method still needed for reporting, but not for persistence

    fun writeTo(writer: SnapshotWriter) {
        writer.writeFiscalYear(
            year = fiscalYear.value,
            profits = ProfitsSummary(
                profitsFromOptions = profitAndLossFromOptions.profit,
                lossesFromOptions = profitAndLossFromOptions.loss,
                profitsFromStocks = profitAndLossFromStocks
            )
        )
    }
}

// SnapshotSerializer.kt implementation
class SnapshotWriterImpl : SnapshotWriter {
    private val optionPositions = mutableMapOf<String, MutableList<OptionPositionSnapshot>>()
    private val stockPositions = mutableMapOf<String, MutableList<StockPositionSnapshot>>()
    private val fiscalYears = mutableMapOf<Int, FiscalYearSnapshot>()

    override fun writeOptionPosition(key: String, position: OptionShortPosition) {
        optionPositions.computeIfAbsent(key) { mutableListOf() }
            .add(serializeOptionPosition(position))
    }

    override fun writeStockPosition(key: String, position: StockPosition) {
        stockPositions.computeIfAbsent(key) { mutableListOf() }
            .add(serializeStockPosition(position))
    }

    override fun writeFiscalYear(year: Int, profits: ProfitsSummary) {
        fiscalYears[year] = serializeFiscalYear(year, profits)
    }

    fun toPortfolioSnapshot(): PortfolioSnapshot =
        PortfolioSnapshot(optionPositions, stockPositions)
}

// Usage in ApplicationRunner
val writer = SnapshotWriterImpl()
portfolio.writeTo(writer)
fiscalYearRepository.getAllSortedByYear().forEach { it.writeTo(writer) }
val snapshot = StateSnapshot(metadata, writer.toPortfolioSnapshot(), writer.fiscalYears)
```

### Trade-offs Analysis

#### Current Approach: Persistence Reads from Domain

**Advantages:**
1. **Simplicity**: Straightforward pull model - serializer pulls data when needed
2. **Centralized logic**: All serialization logic in one place (SnapshotSerializer)
3. **No domain coupling**: Domain objects don't know about persistence at all
4. **Easy to understand**: Linear flow from domain → serializer → file
5. **Testability**: Serializer can be tested independently with mock domain objects
6. **Stateless serialization**: Serializer doesn't maintain state during serialization

**Disadvantages:**
1. **Broken encapsulation**: Domain must expose internal state via `getOptionPositionsMap()`, `getStockPositionsMap()`
2. **Internal visibility**: Uses Kotlin's `internal` modifier to restrict access, but still exposes implementation details
3. **Tight coupling to structure**: Serializer knows exact internal structure (maps of queues)
4. **Fragile**: If Portfolio changes internal structure (e.g., different collection type), serializer must change
5. **Violation of Tell, Don't Ask**: Serializer asks domain for data instead of telling domain to do something

#### Alternative Approach: Domain Writes to Persistence API

**Advantages:**
1. **Preserved encapsulation**: No need to expose internal maps or implementation details
2. **Domain control**: Domain object decides what and how to write
3. **Flexible internals**: Portfolio can change internal structure without affecting persistence API
4. **Tell, Don't Ask**: Follows command pattern - domain is told to write itself
5. **Single Responsibility**: Each domain object responsible for its own persistence representation
6. **Interface segregation**: Writer interface can be minimal and focused

**Disadvantages:**
1. **Domain coupling**: Domain objects now depend on persistence abstractions (SnapshotWriter interface)
2. **Distributed logic**: Serialization logic split between domain objects and writer implementation
3. **More complex**: Requires visitor/writer pattern implementation
4. **Domain responsibility**: Domain objects now have persistence responsibility
5. **Testing complexity**: Must test domain objects with mock writers
6. **Stateful serialization**: Writer accumulates state during traversal
7. **Order dependency**: Writer must be called in correct order for proper structure

#### Hybrid Approach: Keep Both

A middle ground could maintain both approaches:

```kotlin
class Portfolio {
    private val optionPositions = mutableMapOf<String, Queue<OptionShortPosition>>()

    // For serialization that needs internal access
    internal fun getOptionPositionsMap() = optionPositions

    // For better encapsulation when possible
    fun forEachOptionPosition(action: (String, OptionShortPosition) -> Unit) {
        optionPositions.forEach { (key, queue) ->
            queue.forEach { position ->
                action(key, position)
            }
        }
    }
}

// SnapshotSerializer can then use:
portfolio.forEachOptionPosition { key, position ->
    writer.writeOptionPosition(key, position)
}
```

This provides iteration without exposing the mutable map, but still violates encapsulation by revealing the key structure.

### Design Philosophy Considerations

**Current Approach Aligns With:**
- **Separation of Concerns**: Domain has zero knowledge of persistence
- **Dependency Inversion**: Persistence depends on domain, not vice versa
- **Framework Independence**: Domain is pure business logic
- **Pragmatism**: Simple solution that works, even if slightly imperfect

**Alternative Approach Aligns With:**
- **Encapsulation**: Internal state remains truly private
- **Tell, Don't Ask**: Objects are told to do something rather than queried
- **Single Responsibility**: Each object responsible for its own representation
- **Object-Oriented Purity**: Objects control their own data

### Recommendation

For this application, the **current approach (persistence reads from domain)** is likely the better choice because:

1. **Domain Independence**: Portfolio and FiscalYear remain pure domain models with no persistence concerns
2. **Spring Boot Architecture**: The application already uses Spring dependency injection and event publishing - keeping domain objects framework-agnostic is valuable
3. **Testing**: Domain objects can be tested without any persistence concerns
4. **Simplicity**: The serialization flow is easy to understand and maintain
5. **Pragmatic Trade-off**: The `internal` visibility modifier provides reasonable protection - only code in the same module can access these methods

The encapsulation violation is **minor and acceptable** because:
- The exposure is limited to `internal` visibility (module-level only)
- The domain logic doesn't change to accommodate persistence
- The domain objects don't depend on any persistence abstractions
- The benefit of keeping domain objects persistence-agnostic outweighs the cost of exposing some internal state

If stronger encapsulation were required, the visitor/writer pattern would be worth the added complexity. But for this application, the current design strikes a good balance between encapsulation and simplicity.

### Related Patterns in the Codebase

The application already uses similar "query" patterns elsewhere:
- `Portfolio.getStockPositions(symbol)` - Public getter for business logic (Portfolio.kt:47)
- `Portfolio.getOptionPositions(...)` - Public getter for business logic (Portfolio.kt:48-61)
- `FiscalYear.profits()` - Public getter for reporting (FiscalYear.kt:32-36)

The `internal` getters for persistence are consistent with this design approach - expose what's needed, but use visibility modifiers to control access scope.

## Third Alternative: Event-Based State Tracking

### The Event-Driven Opportunity

The application **already uses an event-driven architecture** extensively. Portfolio publishes events whenever positions change:

**Existing Events** (Portfolio.kt):
- `OptionSellToOpenEvent` - Published when option position opened (line 216)
- `OptionBuyToCloseEvent` - Published when option position closed (line 203)
- `StockSellToCloseEvent` - Published when stock position closed (line 94)

**Existing Event Listener** (FiscalYearManager.kt):
- FiscalYearManager already listens to these events (lines 18-28)
- Updates fiscal year profit/loss calculations in response to events
- **No direct coupling** to Portfolio or its internal state

This pattern could be extended to state persistence: a dedicated `StateTracker` component could listen to these same events and maintain a snapshot-ready representation of the current state.

### Event-Based Persistence Architecture

**Conceptual Design:**

```kotlin
// New persistence events - emitted by Portfolio when state changes
data class PositionOpenedEvent(
    val positionKey: String,
    val position: OptionShortPosition  // or StockPosition
)

data class PositionClosedEvent(
    val positionKey: String,
    val wasFullyClosed: Boolean
)

// State tracker that maintains snapshot-ready state
@Component
class PortfolioStateTracker {
    private val optionPositions = mutableMapOf<String, MutableList<OptionPositionSnapshot>>()
    private val stockPositions = mutableMapOf<String, MutableList<StockPositionSnapshot>>()

    @EventListener(OptionSellToOpenEvent::class)
    fun onOptionOpened(event: OptionSellToOpenEvent) {
        val key = event.stoTx.key()
        val snapshot = OptionPositionSnapshot(
            stoTx = serializeOptionTrade(event.stoTx),
            quantityLeft = event.stoTx.quantity
        )
        optionPositions.computeIfAbsent(key) { mutableListOf() }
            .add(snapshot)
        log.debug("Tracked option position opened: {}", key)
    }

    @EventListener(OptionBuyToCloseEvent::class)
    fun onOptionClosed(event: OptionBuyToCloseEvent) {
        val key = event.stoTx.key()
        val positions = optionPositions[key]!!
        // Update first position's quantityLeft (FIFO)
        val position = positions.first()
        val newQuantity = position.quantityLeft - event.quantitySold

        if (newQuantity == 0) {
            positions.removeAt(0)
            if (positions.isEmpty()) {
                optionPositions.remove(key)
            }
        } else {
            positions[0] = position.copy(quantityLeft = newQuantity)
        }
        log.debug("Tracked option position closed: {} quantity: {}", key, event.quantitySold)
    }

    @EventListener(StockSellToCloseEvent::class)
    fun onStockClosed(event: StockSellToCloseEvent) {
        val symbol = event.btoTx.symbol
        val positions = stockPositions[symbol]!!
        val position = positions.first()
        val newQuantity = position.quantityLeft - event.quantitySold

        if (newQuantity == 0) {
            positions.removeAt(0)
            if (positions.isEmpty()) {
                stockPositions.remove(symbol)
            }
        } else {
            positions[0] = position.copy(quantityLeft = newQuantity)
        }
        log.debug("Tracked stock position closed: {} quantity: {}", symbol, event.quantitySold)
    }

    fun getPortfolioSnapshot(): PortfolioSnapshot {
        return PortfolioSnapshot(
            optionPositions = optionPositions.toMap(),
            stockPositions = stockPositions.toMap()
        )
    }

    fun reset() {
        optionPositions.clear()
        stockPositions.clear()
    }

    fun restoreFrom(snapshot: PortfolioSnapshot) {
        reset()
        snapshot.optionPositions.forEach { (key, list) ->
            optionPositions[key] = list.toMutableList()
        }
        snapshot.stockPositions.forEach { (key, list) ->
            stockPositions[key] = list.toMutableList()
        }
    }
}

// Similar tracker for FiscalYear state
@Component
class FiscalYearStateTracker {
    private val fiscalYears = mutableMapOf<Int, FiscalYearSnapshot>()

    @EventListener(OptionSellToOpenEvent::class)
    fun onOptionOpened(event: OptionSellToOpenEvent) {
        val year = event.stoTx.year().value
        val snapshot = fiscalYears.computeIfAbsent(year) {
            FiscalYearSnapshot(year, ProfitAndLossSnapshot.empty(), MonetaryAmountSnapshot.zero())
        }
        // Update profits...
        fiscalYears[year] = snapshot.copy(
            profitAndLossFromOptions = snapshot.profitAndLossFromOptions.addProfit(...)
        )
    }

    @EventListener(OptionBuyToCloseEvent::class)
    fun onOptionClosed(event: OptionBuyToCloseEvent) {
        // Update profits/losses based on close
    }

    @EventListener(StockSellToCloseEvent::class)
    fun onStockClosed(event: StockSellToCloseEvent) {
        // Update stock profits
    }

    fun getFiscalYearsSnapshot(): Map<Int, FiscalYearSnapshot> {
        return fiscalYears.toMap()
    }

    fun reset() {
        fiscalYears.clear()
    }

    fun restoreFrom(snapshot: Map<Int, FiscalYearSnapshot>) {
        reset()
        fiscalYears.putAll(snapshot)
    }
}

// Modified SnapshotSerializer
@Component
class SnapshotSerializer(
    private val portfolioStateTracker: PortfolioStateTracker,
    private val fiscalYearStateTracker: FiscalYearStateTracker
) {
    fun createSnapshot(lastTransactionDate: Instant): StateSnapshot {
        return StateSnapshot(
            metadata = SnapshotMetadata(
                createdAt = Instant.now(),
                lastTransactionDate = lastTransactionDate
            ),
            portfolio = portfolioStateTracker.getPortfolioSnapshot(),
            fiscalYears = fiscalYearStateTracker.getFiscalYearsSnapshot()
        )
    }
}

// Portfolio.kt - NO CHANGES NEEDED!
// Portfolio continues to publish events as it does now
// No internal getters required
```

### Trade-offs Analysis

#### Event-Based State Tracking Approach

**Advantages:**
1. **Perfect encapsulation**: Portfolio and FiscalYear expose NOTHING for persistence
2. **Existing infrastructure**: Leverages events already being published
3. **Zero domain coupling**: Domain objects completely unaware of persistence
4. **Separation of concerns**: State tracking is a separate, focused responsibility
5. **Real-time state**: State is maintained incrementally, not reconstructed on-demand
6. **Consistent with architecture**: FiscalYearManager already uses this pattern
7. **Testability**: State trackers can be tested independently
8. **No structural knowledge**: Trackers don't need to know about Portfolio's internal maps/queues
9. **Audit trail**: Events provide a natural audit log of state changes

**Disadvantages:**
1. **Consistency risk**: State tracker must stay perfectly synchronized with domain
2. **Event dependency**: Requires complete and correct events for all state changes
3. **Complex initialization**: Restoration must populate both domain AND trackers
4. **Event ordering**: Must handle events in correct order during restoration
5. **Missing events**: If Portfolio changes without events, trackers become stale
6. **More components**: Additional StateTracker components to maintain

**Practical Considerations:**
- **Memory overhead**: Negligible for this application - position data easily fits in memory
- **Stock BTO events**: Currently not published, but stock trading is out of scope for this application, so this is not a blocker

### Comparison of All Three Approaches

#### 1. Current Approach: Persistence Reads from Domain

**Summary**: Serializer pulls state using `internal` getters

| Aspect | Rating | Notes |
|--------|--------|-------|
| Encapsulation | ⭐⭐⭐ | Uses `internal` visibility - acceptable compromise |
| Domain Independence | ⭐⭐⭐⭐⭐ | Domain has no persistence dependencies |
| Simplicity | ⭐⭐⭐⭐⭐ | Straightforward pull model |
| Consistency | ⭐⭐⭐⭐⭐ | State read directly from source of truth |
| Maintainability | ⭐⭐⭐⭐ | Central serialization logic |
| Testability | ⭐⭐⭐⭐⭐ | Easy to test independently |
| **Total** | **29/30** | |

**Best for**: Simple applications where pragmatic trade-offs are acceptable

#### 2. Visitor/Writer Pattern: Domain Writes to Persistence

**Summary**: Domain objects write to persistence API via visitor pattern

| Aspect | Rating | Notes |
|--------|--------|-------|
| Encapsulation | ⭐⭐⭐⭐⭐ | Perfect - no internal exposure |
| Domain Independence | ⭐⭐ | Domain depends on persistence abstractions |
| Simplicity | ⭐⭐ | Requires visitor pattern implementation |
| Consistency | ⭐⭐⭐⭐⭐ | State read directly from source of truth |
| Maintainability | ⭐⭐⭐ | Logic split between domain and writer |
| Testability | ⭐⭐⭐ | Must test with mock writers |
| **Total** | **22/30** | |

**Best for**: Applications requiring strict encapsulation where domain coupling is acceptable

#### 3. Event-Based State Tracking: Separate Component Listens to Events

**Summary**: State tracker maintains snapshot-ready state by listening to domain events

| Aspect | Rating | Notes |
|--------|--------|-------|
| Encapsulation | ⭐⭐⭐⭐⭐ | Perfect - no internal exposure |
| Domain Independence | ⭐⭐⭐⭐⭐ | Domain has no persistence knowledge |
| Simplicity | ⭐⭐⭐ | Moderate - requires event handlers |
| Consistency | ⭐⭐⭐⭐ | Events already reliable (FiscalYearManager depends on them) |
| Maintainability | ⭐⭐⭐⭐ | Clear separation, but more components |
| Testability | ⭐⭐⭐⭐ | Trackers testable independently |
| **Total** | **28/30** | |

**Best for**: Event-driven architectures where real-time state tracking is valuable

**Note**: Memory usage is not a concern for this application as position data easily fits in memory. Stock BTO events are currently not published, but stock trading is out of scope.

### Recommendation Based on Context

For **this application**, considering:
- Already uses event-driven architecture extensively
- FiscalYearManager already demonstrates this pattern
- Events are already being published for all critical state changes
- Spring Boot provides excellent event infrastructure

The **event-based state tracking approach** is highly compelling because:

1. **Architectural consistency**: FiscalYearManager already uses this exact pattern
2. **Zero domain changes**: Portfolio continues publishing events as it does now
3. **Perfect encapsulation**: No `internal` getters needed
4. **Framework alignment**: Leverages Spring's event system naturally
5. **Negligible overhead**: Memory usage is not a concern for this data volume

**Note on Stock BTO Events**: Currently, Portfolio does not publish events when stock positions are opened (BUY_TO_OPEN). However, stock trading is out of scope for this application, so this is not a blocker for implementing event-based state tracking.

#### Implementation Path

**Minimal changes required:**

1. Create `PortfolioStateTracker` component with event listeners for option events
2. Create `FiscalYearStateTracker` component with event listeners
3. Modify `SnapshotSerializer` to use trackers instead of domain getters
4. Remove `internal` getters from Portfolio and FiscalYear
5. Update `SnapshotDeserializer` to restore both domain objects AND state trackers

**Migration strategy:**
- Both approaches could coexist temporarily during migration
- State trackers could be validated against direct reads initially
- Once validated, remove `internal` getters

The consistency risk (state tracker diverging from domain) is mitigated by:
- Events are already being published reliably (FiscalYearManager depends on them)
- State trackers are the ONLY consumers that need perfect accuracy
- BDD tests would catch any synchronization issues immediately

### Final Comparison Table

| Approach | Encapsulation | Domain Independence | Architectural Fit | Complexity | Overall |
|----------|---------------|---------------------|-------------------|------------|---------|
| **Current (Pull)** | Good | Excellent | Good | Low | ⭐⭐⭐⭐ |
| **Visitor (Push)** | Excellent | Poor | Fair | High | ⭐⭐⭐ |
| **Event-Based** | Excellent | Excellent | **Excellent** | Medium | ⭐⭐⭐⭐⭐ |

For this specific codebase with its existing event-driven architecture, the **event-based approach is the best fit**. The consistency risks are minimal (events are already published reliably), and memory overhead is negligible for this data volume.

## Open Questions

None - the current implementation demonstrates clear separation of concerns with persistence logic fully encapsulated in the `snapshot` package.

Three alternative approaches have been analyzed:
1. **Current approach (persistence reads)**: Pragmatic trade-off with `internal` getters
2. **Visitor/writer pattern**: Stronger encapsulation but couples domain to persistence
3. **Event-based state tracking**: Best architectural fit for this event-driven codebase, providing perfect encapsulation while maintaining domain independence
