package com.elchworks.tastyworkstaxcalculator.snapshot

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
        lastTransactionDate: Instant,
        portfolioStateTracker: PortfolioStateTracker,
        fiscalYearStateTracker: FiscalYearStateTracker
    ): StateSnapshot {
        log.info("Creating snapshot with lastTransactionDate={}", lastTransactionDate)

        val portfolioSnapshot = portfolioStateTracker.getPortfolioSnapshot()
        val fiscalYearsSnapshot = fiscalYearStateTracker.getFiscalYearsSnapshot()

        return StateSnapshot(
            metadata = SnapshotMetadata(
                createdAt = Instant.now(),
                lastTransactionDate = lastTransactionDate
            ),
            portfolio = portfolioSnapshot,
            fiscalYears = fiscalYearsSnapshot
        )
    }
}
