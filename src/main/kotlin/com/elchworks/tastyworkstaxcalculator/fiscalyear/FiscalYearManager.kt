package com.elchworks.tastyworkstaxcalculator.fiscalyear

import com.elchworks.tastyworkstaxcalculator.positions.OptionBuyToCloseEvent
import com.elchworks.tastyworkstaxcalculator.positions.OptionSellToOpenEvent
import com.elchworks.tastyworkstaxcalculator.positions.StockSellToCloseEvent
import com.elchworks.tastyworkstaxcalculator.transactions.Transaction
import com.elchworks.tastyworkstaxcalculator.transactions.year
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class FiscalYearManager(
    private val fiscalYearRepository: FiscalYearRepository
) {

    @EventListener(OptionSellToOpenEvent::class)
    fun onPositionOpened(event: OptionSellToOpenEvent) =
        getFiscalYear(event.stoTx).onOptionPositionOpened(event)

    @EventListener(OptionBuyToCloseEvent::class)
    fun onPositionClosed(event: OptionBuyToCloseEvent) {
        getFiscalYear(event.btcTx).onOptionPositionClosed(event)
    }

    fun onStockPositionClosed(event: StockSellToCloseEvent) {
       getFiscalYear(event.stcTx).onStockPositionClosed(event)
    }

    @EventListener(EndOfYearEvent::class)
    fun onEndOfYear(event: EndOfYearEvent) {
        fiscalYearRepository.getFiscalYear(event.year).printReport()
    }

    private fun getFiscalYear(tx: Transaction) =
        fiscalYearRepository.getFiscalYear(tx.year())
}
