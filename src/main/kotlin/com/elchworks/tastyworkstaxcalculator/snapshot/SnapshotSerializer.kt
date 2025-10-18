package com.elchworks.tastyworkstaxcalculator.snapshot

import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYearRepository
import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYear
import com.elchworks.tastyworkstaxcalculator.portfolio.Portfolio
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionShortPosition
import com.elchworks.tastyworkstaxcalculator.portfolio.stock.StockPosition
import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade
import com.elchworks.tastyworkstaxcalculator.transactions.StockTransaction
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
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
        log.debug("serializePortfolio")
        val optionPositions = portfolio.getOptionPositionsMap()
            .mapValues { (_, queue) ->
                queue.map { serializeOptionPosition(it) }
            }

        val stockPositions = portfolio.getStockPositionsMap()
            .mapValues { (_, queue) ->
                queue.map { serializeStockPosition(it) }
            }

        log.debug("serializePortfolio optionPositions.size={}, stockPositions.size={}",
            optionPositions.size, stockPositions.size)
        return PortfolioSnapshot(optionPositions, stockPositions)
    }

    private fun serializeOptionPosition(position: OptionShortPosition): OptionPositionSnapshot {
        return OptionPositionSnapshot(
            stoTx = serializeOptionTrade(position.stoTx),
            quantityLeft = position.quantity()
        )
    }

    private fun serializeStockPosition(position: StockPosition): StockPositionSnapshot {
        return StockPositionSnapshot(
            btoTx = serializeStockTransaction(position.btoTx),
            quantityLeft = position.quantity()
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
            commissions = when(tx) {
                is com.elchworks.tastyworkstaxcalculator.transactions.StockTrade ->
                    serializeMonetaryAmount(tx.commissions)
                is com.elchworks.tastyworkstaxcalculator.transactions.OptionAssignment ->
                    serializeMonetaryAmount(com.elchworks.tastyworkstaxcalculator.usd(0.0))
                else -> serializeMonetaryAmount(com.elchworks.tastyworkstaxcalculator.usd(0.0))
            },
            fees = when(tx) {
                is com.elchworks.tastyworkstaxcalculator.transactions.StockTrade ->
                    serializeMonetaryAmount(tx.fees)
                is com.elchworks.tastyworkstaxcalculator.transactions.OptionAssignment ->
                    serializeMonetaryAmount(tx.fees)
                else -> serializeMonetaryAmount(com.elchworks.tastyworkstaxcalculator.usd(0.0))
            }
        )
    }

    private fun serializeMonetaryAmount(amount: MonetaryAmount): MonetaryAmountSnapshot {
        return MonetaryAmountSnapshot(
            amount = amount.number.doubleValueExact(),
            currency = amount.currency.currencyCode
        )
    }

    private fun serializeFiscalYears(repository: FiscalYearRepository): Map<Int, FiscalYearSnapshot> {
        val fiscalYears = repository.getAllSortedByYear()
            .associate { fiscalYear ->
                fiscalYear.fiscalYear.value to serializeFiscalYear(fiscalYear)
            }
        log.debug("serializeFiscalYears fiscalYears.size={}", fiscalYears.size)
        return fiscalYears
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
