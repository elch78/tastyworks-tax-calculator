package com.elchworks.tastyworkstaxcalculator.fiscalyear

import org.springframework.stereotype.Component

@Component
class FiscalYearRepository {
    private val fiscalYears = mutableMapOf<Int, FiscalYear>()

    fun getFiscalYear(year: Int) = fiscalYears.computeIfAbsent(year) { FiscalYear() }
}
