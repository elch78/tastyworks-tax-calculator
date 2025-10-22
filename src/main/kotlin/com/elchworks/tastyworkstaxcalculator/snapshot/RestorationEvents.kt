package com.elchworks.tastyworkstaxcalculator.snapshot

/**
 * Published by Portfolio when its state is restored from a snapshot.
 * State trackers listen to this event to restore their own state.
 */
data class PortfolioStateRestoredEvent(
    val portfolioSnapshot: PortfolioSnapshot
)

/**
 * Published by FiscalYear when its state is restored from a snapshot.
 * State trackers listen to this event to restore their own state.
 */
data class FiscalYearStateRestoredEvent(
    val fiscalYearSnapshot: FiscalYearSnapshot
)
