package com.elchworks.tastyworkstaxcalculator.fiscalyear

import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionBuyToCloseEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionSellToOpenEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.stock.StockSellToCloseEvent
import com.elchworks.tastyworkstaxcalculator.transactions.Transaction
import com.elchworks.tastyworkstaxcalculator.transactions.year
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class FiscalYearManager(
    private val fiscalYearRepository: FiscalYearRepository
) {
    private val log = LoggerFactory.getLogger(FiscalYearManager::class.java)

    @EventListener(OptionSellToOpenEvent::class)
    fun onPositionOpened(event: OptionSellToOpenEvent) =
        getFiscalYear(event.stoTx).onOptionPositionOpened(event)

    @EventListener(OptionBuyToCloseEvent::class)
    fun onPositionClosed(event: OptionBuyToCloseEvent) =
        getFiscalYear(event.btcTx).onOptionPositionClosed(event)

    @EventListener(StockSellToCloseEvent::class)
    fun onStockPositionClosed(event: StockSellToCloseEvent) =
       getFiscalYear(event.stcTx).onStockPositionClosed(event)

    fun printReports() {
        fiscalYearRepository.getAllSortedByYear().forEach{
            val profits = it.profits()
            log.info("""
            
            Profit and loss for fiscal year ${it.fiscalYear}: 
            profit from options = ${profits.profitsFromOptions} 
            loss from options = ${profits.lossesFromOptions}
            profit from stocks = ${profits.profitsFromStocks}
            """.trimIndent())
        }
    }


    private fun getFiscalYear(tx: Transaction) =
        fiscalYearRepository.getFiscalYear(tx.year())
}
