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
}
