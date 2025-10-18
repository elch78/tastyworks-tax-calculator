Current State

The application currently processes all transactions from the beginning of time on every run:

- ApplicationRunner.kt:23-36 - Reads all CSV files, sorts all transactions by date, and publishes events for every transaction
- Portfolio.kt:26-27 - Builds up position state from scratch (option and stock positions)
- FiscalYearManager.kt:18-28 - Accumulates profit/loss calculations from all position close events
- FiscalYear.kt:28-29 - Tracks cumulative profits/losses per year

What State Needs to be Persisted

Portfolio State (Primary)

From Portfolio.kt:
- optionPositions: Map<String, Queue<OptionShortPosition>> - All open option positions with their FIFO queues
- stockPositions: Map<String, Queue<StockPosition>> - All open stock positions with their FIFO queues
- For each position, the critical data is:
    - The original opening transaction (stoTx or btoTx)
    - quantityLeft - the remaining open quantity after partial closes

FiscalYear State (Can be Recalculated)

From FiscalYear.kt:28-29:
- profitAndLossFromOptions: ProfitAndLoss - Cumulative option P&L
- profitAndLossFromStocks: MonetaryAmount - Cumulative stock P&L

Note: According to the plan document (PORTFOLIO_PERSISTENCE_PLAN.md:79), fiscal year calculations would be recalculated from events when restoring from a snapshot, not persisted directly.

Components That Need to be Modified

1. Portfolio.kt

Add methods to:
- createSnapshot(lastProcessedDate: Instant): PortfolioSnapshot - Serialize current position state
- restoreFromSnapshot(snapshot: PortfolioSnapshot) - Rebuild position queues from snapshot data

2. ApplicationRunner.kt:21-38

Modify the run() method to:
- Accept --snapshotDir parameter (default: ./portfolio-snapshots)
- Accept --resetSnapshot flag to force full recalculation
- Load latest snapshot if available
- Filter transactions to only process those after snapshot.lastProcessedDate
- Validate that all transactions are chronologically after the snapshot date
- Save a new snapshot after processing completes

3. FiscalYearRepository.kt

Potentially add a reset() method (already exists at line 15-17) to clear fiscal year state when loading from snapshot.

New Components That Need to be Created

Based on the plan document, you need to create:

1. Snapshot Data Model

File: src/main/kotlin/.../snapshot/PortfolioSnapshot.kt
data class PortfolioSnapshot(
val lastProcessedDate: Instant,
val optionPositions: Map<String, List<OptionPositionSnapshot>>,
val stockPositions: Map<String, List<StockPositionSnapshot>>
)

Supporting classes:
- OptionPositionSnapshot - Serializable version of OptionShortPosition
- StockPositionSnapshot - Serializable version of StockPosition
- MonetaryAmountData - For currency serialization (MonetaryAmount is not directly serializable)

2. Snapshot Converter

File: src/main/kotlin/.../snapshot/SnapshotConverter.kt

Converts between domain models and snapshot models:
- Portfolio -> PortfolioSnapshot
- PortfolioSnapshot -> Portfolio state
- Handles MonetaryAmount serialization
- Preserves quantityLeft state for partial positions

3. Snapshot Repository

File: src/main/kotlin/.../snapshot/PortfolioSnapshotRepository.kt

Handles persistence:
- saveSnapshot(snapshot: PortfolioSnapshot, snapshotDir: String) - Writes JSON file with date in filename
- loadLatestSnapshot(snapshotDir: String): PortfolioSnapshot? - Reads all snapshot files and selects newest by lastProcessedDate
- hasSnapshot(snapshotDir: String): Boolean
- deleteAllSnapshots(snapshotDir: String) - For --resetSnapshot flag
- Uses Jackson with JavaTimeModule for JSON serialization

Key Architecture Decisions

From the plan document and my analysis:

1. Snapshot only Portfolio state, not FiscalYear calculations - Fiscal years are recalculated by replaying position close events
   - Rationale: FiscalYearManager listens to position events (OptionBuyToCloseEvent, StockSellToCloseEvent), so when Portfolio restores positions and processes new transactions, fiscal years will be rebuilt automatically
2. Date validation is critical - ApplicationRunner must validate all transactions are after lastProcessedDate
   - Prevents data corruption from out-of-order processing
3. FIFO queue order must be preserved - Snapshot must maintain position order for correct FIFO matching
4. Snapshot selection by content, not filename - Load all snapshots and pick newest by lastProcessedDate field, not filename
   - Makes the system robust against manual file renaming

Implementation Flow

1. On startup with snapshot:
   - ApplicationRunner loads snapshot → Portfolio.restoreFromSnapshot()
   - ApplicationRunner reads all CSV files
   - ApplicationRunner filters transactions > lastProcessedDate
   - ApplicationRunner publishes NewTransactionEvent only for new transactions
   - Portfolio updates positions via event listeners
   - FiscalYearManager recalculates profits via event listeners
   - ApplicationRunner creates and saves new snapshot
2. Error handling:
   - Gracefully handle missing/corrupted snapshots (fall back to full processing)
   - Throw error if transaction dates violate snapshot ordering

Summary of Changes

Modified files:
- ApplicationRunner.kt - Add snapshot loading, filtering, and saving
- Portfolio.kt - Add createSnapshot() and restoreFromSnapshot() methods

New files:
- snapshot/PortfolioSnapshot.kt - Data model
- snapshot/SnapshotConverter.kt - Domain ↔ Snapshot conversion
- snapshot/PortfolioSnapshotRepository.kt - File I/O

Dependencies to add (build.gradle.kts):
- Jackson for JSON serialization (com.fasterxml.jackson.module:jackson-module-kotlin)
- Jackson JavaTimeModule (com.fasterxml.jackson.datatype:jackson-datatype-jsr310)

The good news is you already have a detailed implementation plan in PORTFOLIO_PERSISTENCE_PLAN.md that outlines all of this!