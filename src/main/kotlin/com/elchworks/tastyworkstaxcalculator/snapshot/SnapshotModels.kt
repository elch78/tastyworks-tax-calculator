package com.elchworks.tastyworkstaxcalculator.snapshot

import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionShortPosition
import com.elchworks.tastyworkstaxcalculator.portfolio.stock.StockPosition
import com.elchworks.tastyworkstaxcalculator.transactions.Action
import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade
import com.elchworks.tastyworkstaxcalculator.transactions.StockTrade
import com.elchworks.tastyworkstaxcalculator.transactions.StockTransaction
import com.elchworks.tastyworkstaxcalculator.usd
import org.javamoney.moneta.Money
import java.time.Instant
import java.time.LocalDate
import javax.money.MonetaryAmount

data class PortfolioSnapshot(
    val optionPositions: Map<String, List<OptionPositionSnapshot>>,
    val stockPositions: Map<String, List<StockPositionSnapshot>>
)

data class OptionPositionSnapshot(
    val stoTx: OptionTradeSnapshot,
    val quantityLeft: Int
) {
    fun toOptionShortPosition(): OptionShortPosition {
        return OptionShortPosition(
            stoTx = stoTx.toOptionTrade(),
            quantityLeft = quantityLeft
        )
    }

    companion object {
        fun from(position: OptionShortPosition): OptionPositionSnapshot {
            return OptionPositionSnapshot(
                stoTx = OptionTradeSnapshot.from(position.stoTx),
                quantityLeft = position.quantity()
            )
        }
    }
}

data class StockPositionSnapshot(
    val btoTx: StockTransactionSnapshot,
    val quantityLeft: Int
) {
    fun toStockPosition(): StockPosition {
        return StockPosition(
            btoTx = btoTx.toStockTrade(),
            quantityLeft = quantityLeft
        )
    }

    companion object {
        fun from(position: StockPosition): StockPositionSnapshot {
            return StockPositionSnapshot(
                btoTx = StockTransactionSnapshot.from(position.btoTx),
                quantityLeft = position.quantity()
            )
        }
    }
}

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
) {
    fun toOptionTrade(): OptionTrade {
        return OptionTrade(
            date = date,
            action = Action.SELL_TO_OPEN,
            symbol = symbol,
            callOrPut = callOrPut,
            expirationDate = LocalDate.parse(expirationDate),
            strikePrice = Money.of(strikePrice.amount, strikePrice.currency),
            quantity = quantity,
            averagePrice = Money.of(averagePrice.amount, averagePrice.currency),
            description = description,
            commissions = Money.of(commissions.amount, commissions.currency),
            fees = Money.of(fees.amount, fees.currency),
            instrumentType = "Equity Option",
            value = Money.of(averagePrice.amount, averagePrice.currency)
                .multiply(quantity).multiply(100),
            multiplier = 100,
            underlyingSymbol = symbol,
            orderNr = 0
        )
    }

    companion object {
        fun from(trade: OptionTrade): OptionTradeSnapshot {
            return OptionTradeSnapshot(
                date = trade.date,
                symbol = trade.symbol,
                callOrPut = trade.callOrPut,
                expirationDate = trade.expirationDate.toString(),
                strikePrice = MonetaryAmountSnapshot.from(trade.strikePrice),
                quantity = trade.quantity,
                averagePrice = MonetaryAmountSnapshot.from(trade.averagePrice),
                description = trade.description,
                commissions = MonetaryAmountSnapshot.from(trade.commissions),
                fees = MonetaryAmountSnapshot.from(trade.fees)
            )
        }
    }
}

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
) {
    fun toStockTrade(): StockTrade {
        return StockTrade(
            date = date,
            action = Action.BUY_TO_OPEN,
            symbol = symbol,
            type = type,
            value = Money.of(value.amount, value.currency),
            quantity = quantity,
            averagePrice = Money.of(averagePrice.amount, averagePrice.currency),
            description = description,
            commissions = Money.of(commissions.amount, commissions.currency),
            fees = Money.of(fees.amount, fees.currency)
        )
    }

    companion object {
        fun from(tx: StockTransaction): StockTransactionSnapshot {
            return StockTransactionSnapshot(
                date = tx.date,
                symbol = tx.symbol,
                type = tx.type,
                value = MonetaryAmountSnapshot.from(tx.value),
                quantity = tx.quantity,
                averagePrice = MonetaryAmountSnapshot.from(tx.averagePrice),
                description = tx.description,
                commissions = when(tx) {
                    is StockTrade -> MonetaryAmountSnapshot.from(tx.commissions)
                    is com.elchworks.tastyworkstaxcalculator.transactions.OptionAssignment ->
                        MonetaryAmountSnapshot.from(usd(0.0))
                    else -> MonetaryAmountSnapshot.from(usd(0.0))
                },
                fees = when(tx) {
                    is StockTrade -> MonetaryAmountSnapshot.from(tx.fees)
                    is com.elchworks.tastyworkstaxcalculator.transactions.OptionAssignment ->
                        MonetaryAmountSnapshot.from(tx.fees)
                    else -> MonetaryAmountSnapshot.from(usd(0.0))
                }
            )
        }
    }
}

data class MonetaryAmountSnapshot(
    val amount: Double,
    val currency: String
) {
    companion object {
        fun from(monetaryAmount: MonetaryAmount): MonetaryAmountSnapshot {
            return MonetaryAmountSnapshot(
                amount = monetaryAmount.number.doubleValueExact(),
                currency = monetaryAmount.currency.currencyCode
            )
        }
    }
}

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
