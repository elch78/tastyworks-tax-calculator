---
date: 2025-11-28
author: nils
status: draft
related_research: thoughts/shared/research/2025-11-28-csv-format-2023-vs-2024.md
---

# Implementation Plan: Add Support for 2023 Tastyworks CSV Format

## Context

The application currently only supports the 2024 Tastyworks CSV format (21 columns). We need to add support for the 2023 format (18 columns) while maintaining compatibility with 2024 files. The key difference is that the 2024 format introduced a "Sub Type" column at index 2, shifting all subsequent columns by +1.

**Format Differences:**
- **2023 Format**: 18 columns (Date, Type, Action, Symbol, ...)
- **2024 Format**: 21 columns (Date, Type, **Sub Type**, Action, Symbol, ..., **Total**, **Currency**)

**Related Research:** `thoughts/shared/research/2025-11-28-csv-format-2023-vs-2024.md`

## Requirements

1. Support both 2023 and 2024 CSV formats dynamically
2. Detect format based on column count (18 vs 21)
3. Add nullable `subType: String?` field to all transaction models
4. Fix semantic issue: `type` field should contain CSV "Type" column, not "Sub Type"
5. Update reverse split detection to use `subType` instead of `type`
6. Maintain backward compatibility with existing code

## Design Approach

### Core Strategy

Use a simple delegation pattern with a format-aware column accessor:

1. **Format Detection**: `fun Array<String>.isPre2024Format() = this.size == 18`
2. **Column Accessor**: Helper function that adjusts indices based on format
3. **Delegation**: All existing extension functions delegate to the helper

### Column Index Mapping

```
Column Name         | 2023 Index | 2024 Index | Helper Logic
--------------------|------------|------------|----------------------------------
Date                | 0          | 0          | Same for both (index <= 1)
Type                | 1          | 1          | Same for both (index <= 1)
Sub Type            | N/A        | 2          | Special case - return null for 2023
Action              | 2          | 3          | 2024 index, adjusted for 2023 (index - 1)
Symbol              | 3          | 4          | 2024 index, adjusted for 2023 (index - 1)
Instrument Type     | 4          | 5          | 2024 index, adjusted for 2023 (index - 1)
...                 | ...        | ...        | All shifted by -1 for 2023
Order #             | 17         | 18         | 2024 index, adjusted for 2023 (index - 1)
Total               | N/A        | 19         | Only in 2024 (not currently used)
Currency            | N/A        | 20         | Only in 2024 (not currently used)
```

**Key Insight**: Columns 0-1 (Date, Type) are the same in both formats. Column 2 onwards need adjustment for 2023 format (subtract 1 from the 2024 index).

## Implementation Steps

### Phase 1: Add Format Detection and Column Accessor Helper

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/transactions/TransactionCsvReader.kt`

**Changes:**

1. Add format detection function:
```kotlin
/**
 * Determines if this CSV row is in the pre-2024 format (18 columns).
 *
 * The 2023 format has 18 columns, while the 2024 format has 21 columns.
 * The 2024 format introduced:
 * - "Sub Type" column at index 2 (shifting all subsequent columns by +1)
 * - "Total" column at index 19
 * - "Currency" column at index 20
 *
 * @return true if this row has 18 columns (2023 format), false if 21 columns (2024 format)
 */
private fun Array<String>.isPre2024Format() = this.size == 18
```

2. Add column accessor helper function:
```kotlin
/**
 * Retrieves the value at the specified column index, accounting for format differences.
 *
 * This function handles the column index offset between 2023 and 2024 CSV formats:
 * - Columns 0-1 (Date, Type): Same index in both formats
 * - Columns 2+ (Action onwards): Index in 2024 format, adjusted by -1 for 2023 format
 *
 * All extension functions (action(), symbol(), etc.) should delegate to this function
 * to ensure correct column access regardless of CSV format.
 *
 * @param index The column index in 2024 format (0-based)
 * @return The string value at the appropriate column for the detected format
 *
 * Example:
 * - getColumn(3) returns this[3] for 2024 format (Action)
 * - getColumn(3) returns this[2] for 2023 format (Action, adjusted for missing Sub Type)
 */
private fun Array<String>.getColumn(index: Int): String {
    return if (isPre2024Format()) {
        // 2023 format: columns 0-1 are the same, columns 2+ are shifted by -1
        if (index <= 1) this[index] else this[index - 1]
    } else {
        // 2024 format: use index as-is
        this[index]
    }
}
```

**Rationale**:
- `isPre2024Format()` provides a clear, testable format detection mechanism
- `getColumn()` centralizes all column offset logic in one place
- Well-documented to explain the WHY behind the offset calculation

**Location**: Add these functions near the existing extension functions (around line 203)

---

### Phase 2: Update Extension Functions to Use Delegation

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/transactions/TransactionCsvReader.kt`

**Changes:**

Update all extension functions to delegate to `getColumn()`:

```kotlin
// Lines 204-222: Update existing extension functions

// These remain unchanged (index 0-1 work for both formats)
fun Array<String>.date() = this[0]
fun Array<String>.type() = this[1]

// NEW: Special case - returns null for 2023 format
/**
 * Returns the Sub Type column value (2024 format only).
 *
 * The Sub Type column was introduced in 2024 format at index 2. It contains values like:
 * - "Sell to Open", "Buy to Close" for trades
 * - "Assignment", "Expiration" for option removals
 * - "Reverse Split" for reverse split events
 *
 * For 2023 format files, this column does not exist and returns null.
 *
 * @return Sub Type value for 2024 format, null for 2023 format
 */
fun Array<String>.subType(): String? = if (isPre2024Format()) null else this[2]

// All other functions delegate to getColumn() with 2024 indices
fun Array<String>.action() = this.getColumn(3)
fun Array<String>.symbol() = this.getColumn(4)
fun Array<String>.instrumentType() = this.getColumn(5)
fun Array<String>.description() = this.getColumn(6)
fun Array<String>.value() = this.getColumn(7).replace(",", "").toFloat().toMonetaryAmountUsd()
fun Array<String>.quantity() = this.getColumn(8).toInt()
fun Array<String>.averagePrice() = this.getColumn(9).toFloat().toMonetaryAmountUsd()
fun Array<String>.commissions() = this.getColumn(10).toFloat().toMonetaryAmountUsd()
fun Array<String>.fees() = this.getColumn(11).toFloat().toMonetaryAmountUsd()
fun Array<String>.multiplier() = this.getColumn(12)
fun Array<String>.rootSymbol() = this.getColumn(13)
fun Array<String>.underlyingSymbol() = this.getColumn(14)
fun Array<String>.expirationDate() = this.getColumn(15)
fun Array<String>.strikePrice() = this.getColumn(16).toFloat().toMonetaryAmountUsd()
fun Array<String>.callOrPut() = this.getColumn(17)
fun Array<String>.orderNr() = this.getColumn(18)
```

**Rationale**:
- Minimal changes to existing functions - just replace `this[index]` with `this.getColumn(index)`
- `subType()` is special-cased to return `null` for 2023 format
- All type conversion logic (`.toInt()`, `.toMonetaryAmountUsd()`) remains unchanged

---

### Phase 3: Add subType Field to Transaction Models

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/transactions/OptionTrade.kt`

**Changes:**

Add `subType: String?` field to all transaction data classes:

1. **OptionTrade** (lines 11-32):
```kotlin
data class OptionTrade(
    override val date: Instant,
    override val symbol: String,
    override val expirationDate: LocalDate,
    override val strikePrice: MonetaryAmount,
    override val callOrPut: String,
    override val quantity: Int,
    override val averagePrice: MonetaryAmount,
    override val description: String,
    val action: Action,
    val instrumentType: String,
    val value: MonetaryAmount,
    val commissions: MonetaryAmount,
    val fees: MonetaryAmount,
    val multiplier: Int,
    val underlyingSymbol: String,
    val orderNr: Int,
    val subType: String?  // NEW: 2024 format only, null for 2023
    // FIXME: Transaction unnecessary
): Transaction, OptionTransaction {
    override fun value(): MonetaryAmount = value(quantity)
    override fun value(quantity: Int): MonetaryAmount = averagePrice.multiply(quantity).multiply(multiplier)
}
```

2. **StockTrade** (lines 34-48):
```kotlin
data class StockTrade(
    override val date: Instant,
    override val symbol: String,
    override val action: Action,
    override val type: String,
    override val value: MonetaryAmount,
    override val quantity: Int,
    override val averagePrice: MonetaryAmount,
    override val description: String,
    val commissions: MonetaryAmount,
    val fees: MonetaryAmount,
    val subType: String?  // NEW: 2024 format only, null for 2023
): StockTransaction {
    override fun value(): MonetaryAmount = value(quantity)
    override fun value(quantity: Int): MonetaryAmount = averagePrice.multiply(quantity)
}
```

3. **OptionRemoval** (lines 50-63):
```kotlin
data class OptionRemoval(
    override val date: Instant,
    override val symbol: String,
    override val expirationDate: LocalDate,
    override val strikePrice: MonetaryAmount,
    override val callOrPut: String,
    override val quantity: Int,
    override val averagePrice: MonetaryAmount,
    override val description: String,
    val status: OptionPositionStatus,
    val subType: String?  // NEW: 2024 format only, null for 2023
): OptionTransaction {
    override fun value(): MonetaryAmount = value(quantity)
    override fun value(quantity: Int): MonetaryAmount = averagePrice.multiply(quantity)
}
```

4. **OptionAssignment** (lines 65-78):
```kotlin
data class OptionAssignment(
    override val date: Instant,
    override val action: Action,
    override val type: String,
    override val symbol: String,
    override val value: MonetaryAmount,
    override val quantity: Int,
    override val averagePrice: MonetaryAmount,
    override val description: String,
    val fees: MonetaryAmount,
    val subType: String?  // NEW: 2024 format only, null for 2023
): StockTransaction {
    override fun value(): MonetaryAmount = value(quantity)
    override fun value(quantity: Int): MonetaryAmount = averagePrice.multiply(quantity)
}
```

**Rationale**:
- Nullable `String?` allows 2023 format (null) and 2024 format (actual value) to coexist
- Added to all transaction types for consistency (as per user requirement)
- Minimal breaking change - existing code that doesn't use this field continues to work

---

### Phase 4: Update Transaction Creation Methods

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/transactions/TransactionCsvReader.kt`

**Changes:**

1. **optionTrade()** (lines 157-174) - Add subType parameter:
```kotlin
private fun optionTrade(columns: Array<String>) = OptionTrade(
    date = parseDate(columns.date()),
    action = Action.valueOf(columns.action()),
    symbol = columns.rootSymbol(),
    instrumentType = columns.instrumentType(),
    description = columns.description(),
    value = columns.value(),
    quantity = columns.quantity(),
    averagePrice = columns.averagePrice(),
    commissions = columns.commissions(),
    fees = columns.fees(),
    multiplier = columns.multiplier().toInt(),
    underlyingSymbol = columns.underlyingSymbol(),
    expirationDate = parsLocalDate(columns.expirationDate()),
    strikePrice = columns.strikePrice(),
    callOrPut = columns.callOrPut(),
    orderNr = columns.orderNr().toInt(),
    subType = columns.subType()  // NEW: null for 2023, actual value for 2024
)
```

2. **stockTrade()** (lines 118-142) - Fix type field and add subType:
```kotlin
private fun stockTrade(columns: Array<String>): StockTrade {
    val action = Action.valueOf(columns.action())
    var date = parseDate(columns.date())

    // CHANGED: Use subType instead of type for reverse split detection
    val isReverseSplit = isReverseSplit(columns)
    val commissions = if (isReverseSplit) usd(0.0) else columns.commissions()

    return StockTrade(
        date = date,
        symbol = columns.symbol(),
        action = action,
        type = columns.type(),     // FIXED: Use CSV "Type" column instead of "Sub Type"
        value = columns.value(),
        description = columns.description(),
        quantity = columns.quantity(),
        averagePrice = columns.averagePrice(),
        commissions = commissions,
        fees = columns.fees(),
        subType = columns.subType()  // NEW: null for 2023, actual value for 2024
    )
}
```

3. **optionRemoval()** (lines 144-155) - Add subType parameter:
```kotlin
private fun optionRemoval(columns: Array<String>) =
    OptionRemoval(
        date = parseDate(columns.date()),
        status = if (columns.description().contains("assignment")) ASSIGNED else EXPIRED,
        symbol = columns.rootSymbol(),
        expirationDate = parsLocalDate(columns.expirationDate()),
        strikePrice = columns.strikePrice(),
        callOrPut = columns.callOrPut(),
        quantity = columns.quantity(),
        averagePrice = columns.averagePrice(),
        description = columns.description(),
        subType = columns.subType()  // NEW: null for 2023, actual value for 2024
    )
```

4. **optionAssignment()** (lines 60-71) - Fix type field and add subType:
```kotlin
private fun optionAssignment(columns: Array<String>) =
    OptionAssignment(
        date = parseDate(columns.date()),
        action = Action.valueOf(columns.action()),
        type = columns.type(),       // FIXED: Use CSV "Type" column instead of "Sub Type"
        symbol = columns.symbol(),
        value = columns.value(),
        quantity = columns.quantity(),
        averagePrice = columns.averagePrice(),
        fees = columns.fees(),
        description = columns.description(),
        subType = columns.subType()  // NEW: null for 2023, actual value for 2024
    )
```

**Key Changes**:
- Add `subType = columns.subType()` to all transaction creation methods
- **FIX**: Change `type = columns.subType()` to `type = columns.type()` in `stockTrade()` and `optionAssignment()`
- This fixes the semantic issue where `type` was incorrectly populated with "Sub Type" data

---

### Phase 5: Update Reverse Split Detection

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/transactions/TransactionCsvReader.kt`

**Changes:**

Update `isReverseSplit()` to use `subType()` instead of `type`:

```kotlin
// Line 89-95: Update reverse split detection
private fun isReverseSplit(columns: Array<String>): Boolean {
    val type = columns.type()
    val instrumentType = columns.instrumentType()
    val subType = columns.subType()
    val isReverseSplit = type == "Receive Deliver" && subType == "Reverse Split" && instrumentType == "Equity"
    log.debug("isReverseSplit type='{}', subType='{}', instrumentType='{}', isReverseSplit='{}'", type, subType, instrumentType, isReverseSplit)
    return isReverseSplit
}
```

**Rationale**:
- Only detects reverse splits in 2024 format using `subType == "Reverse Split"`
- For 2023 format, `subType == null`, so this check returns false
- **Known limitation**: Reverse splits in 2023 format cannot be detected without examples and will be processed as regular stock trades
- If user encounters 2023 reverse splits, they should provide sample CSV for proper implementation
/
**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/portfolio/Portfolio.kt`

**Changes:**

Update reverse split detection to use `subType` instead of `type`:

```kotlin
// Line 137: Update reverse split detection in Portfolio
private fun stockTrade(stockTransaction: StockTransaction) {
    if(stockTransaction.subType == "Reverse Split") {  // CHANGED: Use subType instead of type
        reverseSplit(stockTransaction)
    } else {
        when(stockTransaction.action) {
            // ... rest of the code
        }
    }
}
```

**Rationale**:
- Aligns with the semantic fix: `type` now contains "Type" column, `subType` contains "Sub Type" column
- Reverse Split is identified by `subType == "Reverse Split"` in 2024 format
- For 2023 format transactions, `subType == null`, so this check returns false

---

### Phase 6: Add Format Detection Logging

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/transactions/TransactionCsvReader.kt`

**Changes:**

Add logging to the `read()` method to log detected format:

```kotlin
// Line 22-45: Update read() method
fun read(file: File): List<Transaction> {
    val csvReader = CSVReaderBuilder(FileReader(file.absoluteFile)).build()
    val allRows = csvReader.readAll()

    // NEW: Log detected format
    if (allRows.isNotEmpty()) {
        val firstRow = allRows[0]
        val format = if (firstRow.isPre2024Format()) "2023 (18 columns)" else "2024 (21 columns)"
        log.info("Detected CSV format: {} for file {}", format, file.name)
    }

    return allRows
        .filter {
            val type = it[1]
            val action = it[2]  // FIXME: unused variable
            type == "Trade" ||
            (
                type.contains("Receive Deliver")
            )
        }
        .filter { !isHeaderLine(it) }
        .map {
            try {
                parseTransaction(it)
            } catch (e: Exception) {
                throw RuntimeException("Failed to parse ${it.joinToString { " " }}", e)
            }
        }
}
```

**Rationale**:
- Helps with debugging and verification that format detection works correctly
- Logs at INFO level so it's visible in normal operation
- Only logs once per file (using the first row)

**Optional Fix**: Remove or use the unused `action` variable at line 30 (marked with FIXME)

---

### Phase 7: Add Test Coverage for 2023 Format

**File**: `src/test/resources/optionTrade2023.csv` (NEW FILE)

Create 2023 format test CSV file (18 columns, no Sub Type):

```csv
Date,Type,Action,Symbol,Instrument Type,Description,Value,Quantity,Average Price,Commissions,Fees,Multiplier,Root Symbol,Underlying Symbol,Expiration Date,Strike Price,Call or Put,Order #
2022-12-21T16:31:40+0100,Trade,SELL_TO_OPEN,PACB  221230C00010500,Equity Option,Sold 2 PACB 12/30/22 Call 10.50 @ 0.10,20.00,2,10.00,-2.00,-0.254,100,PACB,PACB,12/30/22,10.5,CALL,247462519
2022-12-28T16:14:29+0100,Trade,BUY_TO_CLOSE,PACB  221230C00010500,Equity Option,Bought 2 PACB 12/30/22 Call 10.50 @ 0.04,8.00,-2,4.00,-2.00,-0.14,100,PACB,PACB,12/30/22,10.5,CALL,248328103
```

**File**: `src/test/resources/stockTrade2023.csv` (NEW FILE)

Create 2023 format test CSV for stock trades:

```csv
Date,Type,Action,Symbol,Instrument Type,Description,Value,Quantity,Average Price,Commissions,Fees,Multiplier,Root Symbol,Underlying Symbol,Expiration Date,Strike Price,Call or Put,Order #
2024-06-21T23:00:00+0200,Receive Deliver,SELL_TO_CLOSE,HIMX,Equity,Sell to Close 100 HIMX @ 7.00,700.00,100,7.00,--,-5.04,,,,,,,,
```

**File**: `src/test/resources/optionRemoval2023.csv` (NEW FILE)

Create 2023 format test CSV for option removals:

```csv
Date,Type,Action,Symbol,Instrument Type,Description,Value,Quantity,Average Price,Commissions,Fees,Multiplier,Root Symbol,Underlying Symbol,Expiration Date,Strike Price,Call or Put,Order #
2024-06-21T22:00:00+0200,Receive Deliver,BUY_TO_CLOSE,MVST  240621C00001000,Equity Option,Removal of 1.0 MVST 06/21/24 Call 1.00 due to expiration.,0.00,1,0.00,--,0.00,100,MVST,MVST,6/21/24,1.0,CALL,
```

**File**: `src/test/resources/optionAssignment2023.csv` (NEW FILE)

Create 2023 format test CSV for option assignments:

```csv
Date,Type,Action,Symbol,Instrument Type,Description,Value,Quantity,Average Price,Commissions,Fees,Multiplier,Root Symbol,Underlying Symbol,Expiration Date,Strike Price,Call or Put,Order #
2024-06-21T23:00:00+0200,Receive Deliver,Assignment,,HIMX  240621C00007000,Equity Option,Removal of option due to assignment,0.00,1,0.00,--,0.00,100,HIMX,HIMX,6/21/24,7.0,CALL,
```

---

**File**: `src/test/kotlin/com/elchworks/tastyworkstaxcalculator/transactions/TransactionCsvReaderTest.kt`

**Changes:**

Add test cases for 2023 format:

```kotlin
@Test
fun optionTrade2023Format() {
    assertThatList(readFile("optionTrade2023.csv"))
        .isEqualTo(listOf(
            OptionTrade(
                date = Instant.parse("2022-12-21T15:31:40Z"),
                symbol = "PACB",
                expirationDate = LocalDate.parse("2022-12-30"),
                strikePrice = usd(BigDecimal("10.5")),
                callOrPut = "CALL",
                action = SELL_TO_OPEN,
                instrumentType = "Equity Option",
                description = "Sold 2 PACB 12/30/22 Call 10.50 @ 0.10",
                value = usd(20.0f),
                quantity = 2,
                averagePrice = usd(10.0f),
                commissions = usd(-2.0f),
                fees = usd(-0.254f),
                multiplier = 100,
                underlyingSymbol = "PACB",
                orderNr = 247462519,
                subType = null  // NEW: null for 2023 format
            ),
            OptionTrade(
                date = Instant.parse("2022-12-28T15:14:29Z"),
                symbol = "PACB",
                expirationDate = LocalDate.parse("2022-12-30"),
                strikePrice = usd(BigDecimal("10.5")),
                callOrPut = "CALL",
                action = BUY_TO_CLOSE,
                instrumentType = "Equity Option",
                description = "Bought 2 PACB 12/30/22 Call 10.50 @ 0.04",
                value = usd(8.0f),
                quantity = -2,
                averagePrice = usd(4.0f),
                commissions = usd(-2.0f),
                fees = usd(-0.14f),
                multiplier = 100,
                underlyingSymbol = "PACB",
                orderNr = 248328103,
                subType = null  // NEW: null for 2023 format
            )
        ))
}

@Test
fun stockTrade2023Format() {
    assertThatList(readFile("stockTrade2023.csv"))
        .isEqualTo(listOf(
            StockTrade(
                date = Instant.parse("2024-06-21T21:00:00Z"),
                symbol = "HIMX",
                action = SELL_TO_CLOSE,
                type = "Receive Deliver",  // FIXED: Uses CSV "Type" column
                value = usd(700.0f),
                description = "Sell to Close 100 HIMX @ 7.00",
                quantity = 100,
                averagePrice = usd(7.0f),
                commissions = usd(0.0f),
                fees = usd(-5.04f),
                subType = null  // NEW: null for 2023 format
            )
        ))
}

@Test
fun optionRemoval2023Format() {
    assertThatList(readFile("optionRemoval2023.csv"))
        .isEqualTo(listOf(
            OptionRemoval(
                date = Instant.parse("2024-06-21T20:00:00Z"),
                status = EXPIRED,
                symbol = "MVST",
                expirationDate = LocalDate.parse("2024-06-21"),
                strikePrice = usd(1.0f),
                callOrPut = "CALL",
                quantity = 1,
                averagePrice = usd(0.0f),
                description = "Removal of 1.0 MVST 06/21/24 Call 1.00 due to expiration.",
                subType = null  // NEW: null for 2023 format
            )
        ))
}

@Test
fun optionAssignment2023Format() {
    assertThatList(readFile("optionAssignment2023.csv"))
        .isEqualTo(listOf(
            OptionAssignment(
                date = Instant.parse("2024-06-21T21:00:00Z"),
                action = Assignment,
                type = "Receive Deliver",  // FIXED: Uses CSV "Type" column
                symbol = "HIMX",
                value = usd(0.0f),
                quantity = 1,
                averagePrice = usd(0.0f),
                description = "Removal of option due to assignment",
                fees = usd(0.0f),
                subType = null  // NEW: null for 2023 format
            )
        ))
}
```

**Update existing 2024 format tests** to verify `subType` is populated:

```kotlin
@Test
fun optionTrade() {
    assertThatList(readFile("optionTrade.csv"))
        .isEqualTo(listOf(
            OptionTrade(
                // ... existing fields ...
                subType = "Sell to Open"  // NEW: Verify 2024 format populates subType
            ),
            // ... more examples with subType values
        ))
}

// Similar updates for stockTrade(), optionRemoval(), optionAssignment() tests
```

**Rationale**:
- Tests both formats side-by-side to ensure compatibility
- Verifies `subType` is null for 2023, populated for 2024
- Verifies `type` field now contains correct CSV "Type" column value
- Ensures format detection and column offset logic work correctly

---

### Phase 8: Update BDD Tests

**File**: `src/test/resources/bdd/E2E.feature`

**Changes:**

Add scenario for 2023 format processing (if applicable). Review existing scenarios to ensure they work with both formats.

**Note**: May not require changes if BDD tests use the test CSV files already created.

---

### Phase 9: Improve Error Handling

**File**: `src/main/kotlin/com/elchworks/tastyworkstaxcalculator/transactions/TransactionCsvReader.kt`

**Changes:**

1. Add validation for unexpected column counts:

```kotlin
fun read(file: File): List<Transaction> {
    val csvReader = CSVReaderBuilder(FileReader(file.absoluteFile)).build()
    val allRows = csvReader.readAll()

    // NEW: Validate CSV format
    if (allRows.isNotEmpty()) {
        val firstRow = allRows[0]
        val columnCount = firstRow.size

        if (columnCount != 18 && columnCount != 21) {
            log.error("Unsupported CSV format in file {}: {} columns (expected 18 or 21)", file.name, columnCount)
            throw IllegalArgumentException(
                "Unsupported CSV format in file ${file.name}: $columnCount columns. " +
                "Expected 18 columns (2023 format) or 21 columns (2024 format)."
            )
        }

        val format = if (firstRow.isPre2024Format()) "2023 (18 columns)" else "2024 (21 columns)"
        log.info("Detected CSV format: {} for file {}", format, file.name)
    }

    return allRows
        .filter {
            val type = it[1]
            type == "Trade" || type.contains("Receive Deliver")
        }
        .filter { !isHeaderLine(it) }
        .map {
            try {
                parseTransaction(it)
            } catch (e: Exception) {
                // Improved error message with format info
                val format = if (it.isPre2024Format()) "2023" else "2024"
                throw RuntimeException(
                    "Failed to parse $format format CSV row from ${file.name}: ${it.joinToString(",")}",
                    e
                )
            }
        }
}
```

2. Optional: Remove unused `action` variable in filter (line 30)

**Rationale**:
- Early validation prevents cryptic errors from malformed CSV files
- Improved error messages include format information for debugging
- Clear exception message guides users to correct their CSV files

---

### Phase 10: Update Documentation

**File**: `CLAUDE.md`

**Changes:**

Add section documenting CSV format support:

```markdown
### CSV Format Support

The application supports both 2023 and 2024 Tastyworks CSV export formats:

**2023 Format (18 columns):**
- Date, Type, Action, Symbol, Instrument Type, Description, Value, Quantity, Average Price, Commissions, Fees, Multiplier, Root Symbol, Underlying Symbol, Expiration Date, Strike Price, Call or Put, Order #

**2024 Format (21 columns):**
- Date, Type, **Sub Type**, Action, Symbol, Instrument Type, Description, Value, Quantity, Average Price, Commissions, Fees, Multiplier, Root Symbol, Underlying Symbol, Expiration Date, Strike Price, Call or Put, Order #, **Total**, **Currency**

**Key Differences:**
- 2024 format adds "Sub Type" column at index 2, which provides transaction detail (e.g., "Sell to Open", "Assignment", "Reverse Split")
- 2024 format adds "Total" and "Currency" columns at the end (currently not used by the application)
- Format is automatically detected based on column count

**Transaction Model Changes:**
- All transaction models include a `subType: String?` field
- For 2023 format files, `subType` is `null`
- For 2024 format files, `subType` contains the "Sub Type" column value
- The `type` field contains the CSV "Type" column (e.g., "Trade", "Receive Deliver")

**Known Limitations:**
- Reverse splits in 2023 format are not detected (no "Sub Type" column available)
- If encountered, 2023 reverse splits will be processed as regular stock trades
- User should provide sample 2023 CSV with reverse split for proper implementation

**Implementation Details:**
- See `TransactionCsvReader.kt` for format detection logic
- Extension functions use `getColumn()` helper to handle column index offsets between formats
```

**File**: `thoughts/shared/research/2025-11-28-csv-format-2023-vs-2024.md`

**Changes:**

Update status to "resolved" and add link to implementation plan:

```markdown
---
status: resolved
resolution: thoughts/shared/plans/2025-11-28-add-2023-csv-format-support.md
---
```

---

## Implementation Order

1. **Phase 1**: Add format detection and column accessor helper
2. **Phase 2**: Update extension functions to delegate to helper
3. **Phase 3**: Add `subType` field to transaction models
4. **Phase 4**: Update transaction creation methods
5. **Phase 5**: Update reverse split detection
6. **Phase 6**: Add format detection logging
7. **Phase 7**: Add test coverage for 2023 format
8. **Phase 8**: Update BDD tests (if needed)
9. **Phase 9**: Improve error handling
10. **Phase 10**: Update documentation

## Testing Strategy

1. **Unit Tests**:
   - Test format detection (`isPre2024Format()`)
   - Test column accessor (`getColumn()`) for both formats
   - Test parsing 2023 format CSV files
   - Test parsing 2024 format CSV files (verify no regression)
   - Verify `subType` is null for 2023, populated for 2024

2. **Integration Tests**:
   - Test complete file processing for both formats
   - Test mixed directory (both 2023 and 2024 files)
   - Test reverse split detection with both formats

3. **BDD Tests**:
   - Ensure existing scenarios pass with both formats
   - Add scenario for 2023 format if needed

## Risks and Mitigations

**Risk 1: Breaking changes to transaction models**
- *Mitigation*: Use nullable `String?` for `subType` to maintain backward compatibility
- *Mitigation*: Existing code that doesn't use `subType` continues to work

**Risk 2: Incorrect column mapping**
- *Mitigation*: Comprehensive test coverage for both formats
- *Mitigation*: Format detection logging to verify correct format is detected
- *Mitigation*: Clear error messages with format information

**Risk 3: Reverse split detection may break**
- *Mitigation*: Update Portfolio.kt to use `subType` instead of `type`
- *Mitigation*: Test reverse split scenarios with both formats
- *Mitigation*: For 2023 format, `subType == null` so reverse split check returns false (correct behavior)

**Risk 4: Existing 2024 test files may break**
- *Mitigation*: Update all existing tests to include `subType` expectations
- *Mitigation*: Run full test suite after each phase

## Success Criteria

1. ✅ Application successfully parses both 2023 (18-column) and 2024 (21-column) CSV files
2. ✅ Format detection correctly identifies format based on column count
3. ✅ All extension functions correctly map to appropriate columns for both formats
4. ✅ `subType` field is `null` for 2023 format, populated for 2024 format
5. ✅ `type` field correctly contains CSV "Type" column (not "Sub Type")
6. ✅ Reverse split detection works correctly with `subType` check
7. ✅ All existing tests pass without regression
8. ✅ New tests for 2023 format pass
9. ✅ Clear error messages for unsupported CSV formats
10. ✅ Format detection logged at INFO level

## Follow-up Tasks

1. **Reverse splits in 2023 format**: If user encounters reverse splits in 2023 CSV files, request sample file and implement detection logic (likely based on description field)
2. Consider adding support for Total and Currency columns if needed in the future
3. Review and fix the unused `action` variable in the filter (line 30)
4. Monitor for additional Tastyworks CSV format changes

## Notes

- Implementation uses simple delegation pattern - minimal code changes
- Format detection is based on column count (18 vs 21) - simple and reliable
- No breaking changes to existing code that doesn't use `subType`
- Well-documented functions explain the WHY behind the offset logic