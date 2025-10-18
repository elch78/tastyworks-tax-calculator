package com.elchworks.tastyworkstaxcalculator.snapshot

import java.time.Instant

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
