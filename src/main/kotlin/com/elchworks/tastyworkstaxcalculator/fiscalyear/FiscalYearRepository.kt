package com.elchworks.tastyworkstaxcalculator.fiscalyear

import com.elchworks.tastyworkstaxcalculator.convert.currencyExchange
import org.springframework.stereotype.Component
import java.time.Year

@Component
class FiscalYearRepository(
    private val currencyExchange: currencyExchange
) {
    private val fiscalYears = mutableMapOf<Year, FiscalYear>()

    fun getFiscalYear(year: Year) = fiscalYears.computeIfAbsent(year) { FiscalYear(currencyExchange, year) }
    fun getAllSortedByYear() = fiscalYears.values.sortedBy { it.fiscalYear }
}
