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
            fees = deserializeMonetaryAmount(snapshot.fees),
            // These fields are not critical for position restoration but needed for OptionTrade
            instrumentType = "Equity Option",
            value = deserializeMonetaryAmount(snapshot.averagePrice).multiply(snapshot.quantity).multiply(100),
            multiplier = 100,
            underlyingSymbol = snapshot.symbol,
            orderNr = 0
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
        fiscalYear.restoreState(
            profitAndLossFromOptions = ProfitAndLoss(
                profit = deserializeMonetaryAmount(snapshot.profitAndLossFromOptions.profit),
                loss = deserializeMonetaryAmount(snapshot.profitAndLossFromOptions.loss)
            ),
            profitAndLossFromStocks = deserializeMonetaryAmount(snapshot.profitAndLossFromStocks)
        )
    }
}
