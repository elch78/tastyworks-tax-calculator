package com.elchworks.tastyworkstaxcalculator.fiscalyear

import com.elchworks.tastyworkstaxcalculator.ExchangeRate
import org.springframework.stereotype.Component

@Component
class FiscalYearRepository(
    private val exchangeRate: ExchangeRate
) {
    private val fiscalYears = mutableMapOf<Int, FiscalYear>()

    fun getFiscalYear(year: Int) = fiscalYears.computeIfAbsent(year) { FiscalYear(exchangeRate, year) }
    fun getAllSortedByYear() = fiscalYears.values.sortedBy { it.fiscalYear }
}
