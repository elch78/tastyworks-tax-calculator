package com.elchworks.tastyworkstaxcalculator.fiscalyear

import com.elchworks.tastyworkstaxcalculator.convert.CurrencyExchange
import org.springframework.stereotype.Component
import java.time.Year

@Component
class FiscalYearRepository(
    private val currencyExchange: CurrencyExchange
) {
    private val fiscalYears = mutableMapOf<Year, FiscalYear>()

    fun getFiscalYear(year: Year) = fiscalYears.computeIfAbsent(year) { FiscalYear(currencyExchange, year) }
    fun getAllSortedByYear() = fiscalYears.values.sortedBy { it.fiscalYear }
    fun reset() {
        fiscalYears.clear()
    }
}
