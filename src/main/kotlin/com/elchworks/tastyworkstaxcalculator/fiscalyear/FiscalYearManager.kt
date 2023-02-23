package com.elchworks.tastyworkstaxcalculator.fiscalyear

import com.elchworks.tastyworkstaxcalculator.OptionSellToOpenEvent
import com.elchworks.tastyworkstaxcalculator.TransactionsProcessedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class FiscalYearManager(
    private val fiscalYearRepository: FiscalYearRepository
) {

    @EventListener(OptionSellToOpenEvent::class)
    fun onPositionOpened(event: OptionSellToOpenEvent) {
        val fiscalYear = fiscalYearRepository.getFiscalYear(event.position.yearOpened())
        fiscalYear.addOptionPosition(event.position)
    }

    @EventListener(TransactionsProcessedEvent::class)
    fun onTransactionsProccessed(event: TransactionsProcessedEvent) {
        fiscalYearRepository.getFiscalYear(2021).calculateProfitAndLoss()
    }
}
