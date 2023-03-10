package com.elchworks.tastyworkstaxcalculator.fiscalyear

import com.elchworks.tastyworkstaxcalculator.positions.OptionBuyToCloseEvent
import com.elchworks.tastyworkstaxcalculator.positions.OptionSellToOpenEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class FiscalYearManager(
    private val fiscalYearRepository: FiscalYearRepository
) {

    @EventListener(OptionSellToOpenEvent::class)
    fun onPositionOpened(event: OptionSellToOpenEvent) {
        val fiscalYear = fiscalYearRepository.getFiscalYear(event.stoTx.year())
        fiscalYear.onPositionOpened(event)
    }

    @EventListener(OptionBuyToCloseEvent::class)
    fun onPositionClosed(event: OptionBuyToCloseEvent) {
        val fiscalYear = fiscalYearRepository.getFiscalYear(event.btcTx.year())
        fiscalYear.onPositionClosed(event)
    }

    @EventListener(EndOfYearEvent::class)
    fun onEndOfYear(event: EndOfYearEvent) {
        fiscalYearRepository.getFiscalYear(event.year).printReport()
    }
}
