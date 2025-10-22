package com.elchworks.tastyworkstaxcalculator.snapshot

import com.elchworks.tastyworkstaxcalculator.eur
import com.elchworks.tastyworkstaxcalculator.fiscalyear.OptionProfitLossUpdatedEvent
import com.elchworks.tastyworkstaxcalculator.fiscalyear.StockProfitLossUpdatedEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.ProfitAndLoss
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
                    profit = MonetaryAmountSnapshot.from(state.profitAndLossFromOptions.profit),
                    loss = MonetaryAmountSnapshot.from(state.profitAndLossFromOptions.loss)
                ),
                profitAndLossFromStocks = MonetaryAmountSnapshot.from(state.profitAndLossFromStocks)
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
