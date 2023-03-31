package com.elchworks.tastyworkstaxcalculator.fiscalyear

import com.elchworks.tastyworkstaxcalculator.convert.currencyExchange
import org.springframework.stereotype.Component

@Component
class FiscalYearRepository(
    private val currencyExchange: currencyExchange
) {
    private val fiscalYears = mutableMapOf<Int, FiscalYear>()

    fun getFiscalYear(year: Int) = fiscalYears.computeIfAbsent(year) { FiscalYear(currencyExchange, year) }
    fun getAllSortedByYear() = fiscalYears.values.sortedBy { it.fiscalYear }
}
