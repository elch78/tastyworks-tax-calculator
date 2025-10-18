---
date: 2025-10-17T10:20:55+00:00
researcher: Claude
git_commit: eacec453e94576ba13b23d51d0cf69ec24c892bc
branch: master
repository: tastyworks-tax-calculator
topic: "Components Relevant for Persistent State Implementation"
tags: [research, codebase, persistent-state, portfolio, fiscal-year, transactions]
status: complete
last_updated: 2025-10-17
last_updated_by: Claude
---

# Research: Components Relevant for Persistent State Implementation

**Date**: 2025-10-17T10:20:55+00:00
**Researcher**: Claude
**Git Commit**: eacec453e94576ba13b23d51d0cf69ec24c892bc
**Branch**: master
**Repository**: tastyworks-tax-calculator

## Research Question

What parts of the codebase are relevant for implementing the persistent state specification in `spec/persistent-state.md`?

The specification requires:
1. Ability to persist portfolio state snapshot at the end of a calculation
2. Ability to resume from a snapshot and process only new transactions
3. Chronological order validation to ensure no transactions exist before the snapshot's last transaction
4. Optional persistence of prior fiscal year data for change detection

## Summary

The persistent state feature requires modifications to five key areas:

1. **Portfolio State** (src/main/kotlin/.../portfolio/Portfolio.kt) - Contains all open position data that must be serialized
2. **FiscalYear State** (src/main/kotlin/.../fiscalyear/FiscalYear.kt) - Contains profit/loss accumulations for tax years
3. **Transaction Processing** (src/main/kotlin/.../ApplicationRunner.kt) - Needs snapshot save/load logic and chronological validation
4. **Transaction Ordering** (src/main/kotlin/.../ApplicationRunner.kt:33) - Already sorts transactions, needs validation for backward dates
5. **Event-Driven Architecture** - Must handle both initial state restoration and incremental event processing

The current architecture processes all transactions from scratch each run. Implementing persistent state will allow incremental processing by saving snapshots after each run and resuming from the last known state.

## Detailed Findings

### 1. Portfolio State Management

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/portfolio/Portfolio.kt`

**Current State Fields**:
- **Line 26**: `optionPositions: MutableMap<String, Queue<OptionShortPosition>>` - All open option positions
- **Line 27**: `stockPositions: MutableMap<String, Queue<StockPosition>>` - All open stock positions
- **Line 33**: `splitTransactionCounterpart: StockTransaction?` - Temporary pairing state for reverse splits

**What needs to be persisted**:
1. All queues in `optionPositions` map with their complete `OptionShortPosition` objects
2. All queues in `stockPositions` map with their complete `StockPosition` objects
3. The `splitTransactionCounterpart` if a split is in-progress (edge case)

**OptionShortPosition** (src/main/kotlin/.../portfolio/option/OptionShortPosition.kt):
- **Line 9**: `stoTx: OptionTrade` - The original sell-to-open transaction (immutable)
- **Line 10**: `quantityLeft: Int` - Remaining unclosed quantity (mutable state)

**StockPosition** (src/main/kotlin/.../portfolio/stock/StockPosition.kt):
- **Line 9**: `btoTx: StockTransaction` - The original buy-to-open transaction (immutable)
- **Line 11**: `quantityLeft: Int` - Remaining unsold quantity (mutable state)

**Key Implementation Detail**: Portfolio maintains FIFO queues (LinkedList) for position matching. The snapshot must preserve:
- Queue order (first in, first out)
- Partial position states (quantityLeft values)
- Position keys (option attributes or stock symbols)

### 2. FiscalYear State

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/fiscalyear/FiscalYear.kt`

**Current State Fields** (lines 24-30):
- **Line 26**: `fiscalYear: Year` - The tax year being tracked
- **Line 28**: `profitAndLossFromOptions: ProfitAndLoss` - Option profits and losses
- **Line 29**: `profitAndLossFromStocks: MonetaryAmount` - Stock profits/losses

**ProfitAndLoss Structure** (src/main/kotlin/.../portfolio/ProfitAndLoss.kt:7-12):
- `profit: MonetaryAmount` - Accumulated option profits
- `loss: MonetaryAmount` - Accumulated option losses (separate per German tax law)

**FiscalYearRepository** (src/main/kotlin/.../fiscalyear/FiscalYearRepository.kt):
- **Line 11**: `fiscalYears: MutableMap<Year, FiscalYear>` - All fiscal year instances

**What needs to be persisted**:
1. All FiscalYear instances by year
2. For each fiscal year:
   - Option profits (MonetaryAmount in EUR)
   - Option losses (MonetaryAmount in EUR)
   - Stock profits (MonetaryAmount in EUR)

**Note**: The specification mentions persisting prior fiscal year data "to notice unexpected changes" - this maps directly to the fiscalYears map.

### 3. Transaction Processing and Snapshot Logic

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/ApplicationRunner.kt`

**Current Processing Flow** (lines 21-38):
1. Reads all CSV files from transactionsDir (lines 23-31)
2. Flattens all transactions into single list (line 32)
3. Sorts by date chronologically (line 33)
4. Publishes NewTransactionEvent for each (lines 34-36)
5. Generates final reports (line 37)

**Required Modifications**:

**A. Snapshot Loading** (before transaction processing):
- Check if snapshot file exists
- If exists, deserialize Portfolio and FiscalYearRepository state
- Extract the last transaction date from snapshot
- Log snapshot restoration details

**B. Chronological Validation** (after line 33, before line 34):
- If snapshot loaded, validate `transactions.first().date >= snapshotLastTransactionDate`
- If validation fails, throw clear error message as specified:
  - "Error: Found transaction dated {date} which is before the snapshot's last transaction date {snapshotDate}"
  - "Cannot process transactions that occur before the snapshot. Please provide complete transaction history."

**C. Snapshot Saving** (after line 37):
- Serialize current Portfolio state
- Serialize current FiscalYearRepository state
- Include metadata:
  - Last transaction date processed
  - Snapshot creation timestamp
  - Git commit hash (optional)
- Write to snapshot file (format TBD: JSON, Protocol Buffers, Java serialization, etc.)

**D. Reset Capability**:
- Portfolio already has `reset()` method (Portfolio.kt:63-66)
- FiscalYearRepository already has `reset()` method (FiscalYearRepository.kt:15-17)
- These are currently used in testing and could be used to start fresh without snapshot

### 4. Chronological Ordering Enforcement

**Current Implementation** (ApplicationRunner.kt:33):
```kotlin
.sortedBy { it.date }
```

**How it works**:
- All transactions sorted by `date: Instant` field (Transaction.kt:81)
- Date parsing from CSV uses ISO format with timezone (TransactionCsvReader.kt:179-192)
- FIFO queue processing in Portfolio relies on chronological order

**What exists today**:
- Sorting ensures correct order regardless of file order
- No validation that new transactions aren't backdated

**What needs to be added**:
- **After sorting**: Check if any transaction date < snapshot's last transaction date
- **Error handling**: Throw exception with clear message per specification
- **Edge case**: Handle same-timestamp transactions (reverse splits already have special handling at Portfolio.kt:111-155)

**Validation Location**:
The specification requirement states: "If that happens the system should fail and give a clear error message."

Suggested implementation at ApplicationRunner.kt after line 33:
```kotlin
.sortedBy { it.date }
.also { transactions ->
    if (snapshot != null && transactions.isNotEmpty()) {
        val firstTxDate = transactions.first().date
        if (firstTxDate < snapshot.lastTransactionDate) {
            throw ChronologicalOrderException(
                "Transaction dated $firstTxDate is before snapshot's last transaction ${snapshot.lastTransactionDate}"
            )
        }
    }
}
```

### 5. Event-Driven Architecture Implications

**Current Event Flow** (src/main/kotlin/.../portfolio/NewTransactionEvent.kt):

1. **NewTransactionEvent** published by ApplicationRunner (ApplicationRunner.kt:35)
2. **Portfolio** listens and updates position state (Portfolio.kt:35-45)
3. **Portfolio** publishes position events:
   - OptionSellToOpenEvent (Portfolio.kt:200)
   - OptionBuyToCloseEvent (Portfolio.kt:187)
   - StockSellToCloseEvent (Portfolio.kt:85)
4. **FiscalYearManager** listens to position events (FiscalYearManager.kt:18-28)
5. **FiscalYear** instances update profit/loss state (FiscalYear.kt:38-101)

**Snapshot Restoration Impact**:

When loading from snapshot:
1. **No events are published** for historical state
2. Portfolio's position maps are directly populated from serialized data
3. FiscalYear instances are directly populated with accumulated profits/losses
4. Event processing begins only for NEW transactions

**Why this works**:
- Spring's event system is synchronous (same thread)
- Events only trigger calculations, not state storage
- Restored state is equivalent to having processed all historical transactions
- New transactions trigger events normally and continue updating state

**No architectural changes needed** - the event-driven design naturally supports snapshot restoration because:
- State is maintained in-memory in Portfolio and FiscalYear objects
- Events are just the mechanism for updating state
- Restoring state directly is equivalent to replaying all historical events

## Code References

### State to Serialize
- `Portfolio.optionPositions` - Portfolio.kt:26
- `Portfolio.stockPositions` - Portfolio.kt:27
- `OptionShortPosition.stoTx` - OptionShortPosition.kt:9
- `OptionShortPosition.quantityLeft` - OptionShortPosition.kt:10
- `StockPosition.btoTx` - StockPosition.kt:9
- `StockPosition.quantityLeft` - StockPosition.kt:11
- `FiscalYearRepository.fiscalYears` - FiscalYearRepository.kt:11
- `FiscalYear.profitAndLossFromOptions` - FiscalYear.kt:28
- `FiscalYear.profitAndLossFromStocks` - FiscalYear.kt:29

### Processing Flow
- CSV reading - TransactionCsvReader.kt:22-45
- Transaction aggregation - ApplicationRunner.kt:32
- Chronological sorting - ApplicationRunner.kt:33
- Event publishing - ApplicationRunner.kt:35
- Report generation - ApplicationRunner.kt:37

### Position Management
- Option position opening - Portfolio.kt:195-201
- Option position closing - Portfolio.kt:178-192
- Stock position opening - Portfolio.kt:92-97
- Stock position closing - Portfolio.kt:76-90
- FIFO queue usage - Portfolio.kt:81, 183

### Fiscal Year Tracking
- Option open handling - FiscalYear.kt:38-43
- Option close handling - FiscalYear.kt:45-86
- Stock close handling - FiscalYear.kt:88-101
- Year-based routing - FiscalYearManager.kt:44-45

## Architecture Patterns

### Current Architecture Patterns

**1. Event-Driven State Updates**
- Portfolio publishes domain events when positions change
- FiscalYearManager listens and routes to appropriate year
- Decouples position tracking from tax calculations

**2. FIFO Position Matching**
- LinkedList queues maintain first-in-first-out order
- Supports partial closes across multiple positions
- Chronological order preservation is critical

**3. Lazy Initialization**
- FiscalYear instances created on-demand via computeIfAbsent
- Position queues created on-demand
- Works well with snapshot restoration (pre-populate maps)

**4. Separation of Concerns**
- ApplicationRunner: Orchestration
- Portfolio: Position state management
- FiscalYearManager: Event routing
- FiscalYear: Tax calculations
- Each has clear persistence responsibility

### Patterns for Persistent State

**1. Snapshot Pattern**
- Save complete state at end of processing
- Load state at start of next processing
- Continue from where left off

**2. Validation-First Pattern**
- Load snapshot first
- Validate new data against snapshot constraints
- Fail fast if constraints violated
- Process only if validation passes

**3. State Restoration Pattern**
- Deserialize snapshot data
- Directly populate Portfolio maps
- Directly populate FiscalYearRepository maps
- Skip event publishing for historical state
- Resume normal event processing for new transactions

## Serialization Considerations

### Data Types to Serialize

**Complex Types**:
- `java.time.Instant` - Transaction timestamps
- `java.time.LocalDate` - Option expiration dates
- `javax.money.MonetaryAmount` - All financial values
- `java.time.Year` - Fiscal year keys
- `java.util.Queue<T>` - Position queues (LinkedList implementation)

**Enum Types**:
- `Action` (Action.kt:3-8) - SELL_TO_OPEN, BUY_TO_CLOSE, etc.

**Transaction Types** (all implement Transaction interface):
- `OptionTrade` (OptionTrade.kt:11-32)
- `StockTrade` (OptionTrade.kt:34-48)

**State Types**:
- `OptionShortPosition` (OptionShortPosition.kt:8-17)
- `StockPosition` (StockPosition.kt:8-31)
- `ProfitAndLoss` (ProfitAndLoss.kt:7-12)
- `FiscalYear` (FiscalYear.kt:24-30)

### Format Options

**JSON** (human-readable, debuggable):
- Pros: Easy to inspect, version control friendly, language-agnostic
- Cons: Requires custom serializers for MonetaryAmount, Instant, etc.
- Libraries: Jackson with Kotlin module + JavaMoney module

**Protocol Buffers** (compact, versioned):
- Pros: Efficient, schema evolution support, type safety
- Cons: Requires .proto definitions, less human-readable
- Libraries: protobuf-kotlin

**Java Serialization** (built-in):
- Pros: No additional dependencies, works with existing types
- Cons: Not human-readable, Java-specific, version fragility
- Requires: Serializable markers on data classes

**Recommendation**: JSON with Jackson
- Best for debugging and version control
- Can store in git alongside code for testing
- Easy to manually edit if needed
- Clear schema from Kotlin data classes

### Snapshot File Structure (Suggested)

**Filename**: `snapshot-2024-12-31-235959.json` (format: `snapshot-YYYY-MM-DD-HHmmss.json`)

```json
{
  "metadata": {
    "version": "1.0",
    "createdAt": "2025-10-17T10:20:55+00:00",
    "lastTransactionDate": "2024-12-31T23:59:59+00:00",
    "gitCommit": "eacec453e94576ba13b23d51d0cf69ec24c892bc"
  },
  "portfolio": {
    "optionPositions": {
      "PUT-CLF-2025-02-20-15.00": [
        {
          "stoTx": { /* OptionTrade object */ },
          "quantityLeft": 1
        }
      ]
    },
    "stockPositions": {
      "AAPL": [
        {
          "btoTx": { /* StockTransaction object */ },
          "quantityLeft": 100
        }
      ]
    }
  },
  "fiscalYears": {
    "2024": {
      "profitAndLossFromOptions": {
        "profit": {"amount": 1000.50, "currency": "EUR"},
        "loss": {"amount": 200.00, "currency": "EUR"}
      },
      "profitAndLossFromStocks": {"amount": 500.00, "currency": "EUR"}
    }
  }
}
```

## Implementation Considerations

### 1. Snapshot File Location
Per specification: Filename should include last transaction timestamp
- Format: `snapshot-YYYY-MM-DD-HHmmss.json`
- Example: `snapshot-2024-12-31-235959.json` for a snapshot with last transaction on Dec 31, 2024 at 23:59:59
- Location options:
  - Same `--transactionsDir` directory
  - Or new `--snapshotDir` argument
  - Or convention: `{transactionsDir}/snapshots/`
- Multiple snapshots can coexist (different dates)

### 2. Incremental vs. Complete Processing
**Current**: Always processes complete history
**With Snapshot**: Two modes needed
- **Fresh start**: No snapshot exists, process all transactions, save snapshot
- **Incremental**: Snapshot exists, load it, validate new transactions, process only new ones, update snapshot

### 3. Fiscal Year Boundary Handling
Specification says: "only the last fiscal year is calculated"

**Current behavior**: Calculates all fiscal years found in transactions

**With snapshot**: Could optimize by:
- Only persisting current and previous fiscal year
- But specification says "might be good to persist it as well to notice unexpected changes"
- Recommendation: Persist all fiscal years for data integrity verification

### 4. Error Messages
Per specification: "If that happens the system should fail and give a clear error message"

Example error for chronological violation:
```
ERROR: Chronological order violation detected
  Snapshot last transaction: 2024-12-31 23:59:59 (AAPL stock sale)
  First new transaction:     2024-12-15 10:30:00 (CLF option trade)

  Cannot process transactions that occur before the snapshot date.
  Please provide complete transaction history from the beginning,
  or remove the snapshot file to start fresh.
```

### 5. Testing Strategy
**Unit tests needed for**:
- Snapshot serialization/deserialization
- Chronological validation logic
- State restoration accuracy

**Integration tests needed for**:
- Complete workflow: process → snapshot → resume
- Multi-year scenario with snapshot
- Error handling for backdated transactions
- Fiscal year comparison for change detection

**BDD scenarios** (src/test/resources/bdd/E2E.feature) could add:
```gherkin
Scenario: Resume from snapshot with new transactions
  Given a snapshot exists with last transaction on "31/12/24"
  When processing new transactions from "01/01/25"
  Then the system should resume from the snapshot
  And calculate only the new fiscal year

Scenario: Reject backdated transactions
  Given a snapshot exists with last transaction on "31/12/24"
  When processing transactions from "15/12/24"
  Then the system should fail with chronological error
```

## Open Questions

1. **Snapshot file format**: JSON, Protocol Buffers, or Java Serialization?
   - Specification indicates JSON format (`.json` extension)
2. **Filename convention** (RESOLVED): Per specification: `snapshot-YYYY-MM-DD-HHmmss.json` with last transaction timestamp
3. **File location**: New CLI argument (`--snapshotDir`) or convention-based path (`{transactionsDir}/snapshots/`)?
4. **Snapshot selection**: If multiple snapshots exist, which one to use? (latest by filename timestamp?)
5. **Backward compatibility**: How to handle snapshot version changes?
6. **Validation strictness**: Should we allow "close enough" dates or be strictly chronological?
7. **Currency exchange rates**: Should snapshot include used exchange rates, or always reload from CSV?
8. **Partial year processing**: If snapshot is mid-year, how to handle that fiscal year's state?
9. **Snapshot invalidation**: Should snapshots expire or have validation checksums?

## Related Documentation

- Specification: `spec/persistent-state.md`
- Project instructions: `CLAUDE.md`
- BDD tests: `src/test/resources/bdd/E2E.feature`
- Transaction CSV format: Tastyworks export (21-column format)

## Next Steps

To implement this feature, the following work is needed:

1. **Design serialization format** - JSON format per specification with schema design
2. **Create snapshot classes** - PortfolioSnapshot, FiscalYearSnapshot, Metadata
3. **Implement serialization** - Add save/load methods to Portfolio and FiscalYearRepository
4. **Implement filename generation** - Generate `snapshot-YYYY-MM-DD-HHmmss.json` from last transaction date
5. **Add chronological validation** - Modify ApplicationRunner with validation logic
6. **Update CLI arguments** - Add `--snapshotDir` or use convention-based path
7. **Implement snapshot discovery** - Find latest snapshot file in directory by filename pattern
8. **Write tests** - Unit tests for serialization, integration tests for workflow
9. **Update documentation** - Document snapshot usage in README
10. **Consider edge cases** - Reverse splits, same-timestamp transactions, timezone handling
