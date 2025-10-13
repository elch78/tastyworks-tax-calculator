package com.elchworks.tastyworkstaxcalculator.convert

import com.opencsv.CSVReaderBuilder
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.io.FileReader
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Repository for managing historical EUR/USD exchange rates.
 *
 * This component loads exchange rate data from the European Central Bank's historical
 * exchange rate CSV file and provides lookup functionality for USD to EUR conversions
 * on specific dates.
 *
 * The exchange rates are loaded once at application startup from the classpath resource
 * `eurofxref-hist.csv`.
 *
 * @see [ECB Exchange Rate Data](https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.zip)
 */
@Component
class ExchangeRateRepository {
    private val log = LoggerFactory.getLogger(ExchangeRateRepository::class.java)

    // source: https://de.statista.com/statistik/daten/studie/214878/umfrage/wechselkurs-des-euro-gegenueber-dem-us-dollar-monatliche-entwicklung/
    private val rates = HashMap<LocalDate, BigDecimal>()

    @PostConstruct
    fun readCsv() {
        val ecbHistoricExchangeRates = ClassPathResource("eurofxref-hist.csv").file
        val one = BigDecimal.ONE.setScale(4)
        CSVReaderBuilder(FileReader(ecbHistoricExchangeRates.absoluteFile))
            .withSkipLines(1)// skip header
            .build()
            .readAll()
            .forEach {
                val date = LocalDate.parse(it[0])
                val rateEurToUsd = BigDecimal(it[1])
                val rate = one.divide(rateEurToUsd, java.math.RoundingMode.HALF_UP)
                log.debug("rate $date $rate (USD -> EUR) $rateEurToUsd (EUR -> USD)")
                rates[date] = rate
            }
    }

    /**
     * Retrieves the USD to EUR exchange rate for a specific date.
     */
    fun dailyRateUsdToEur(date: LocalDate): BigDecimal {
        val rate = (rates[date] ?: error("No rate for date $date"))
        log.debug("usdToEur rate='{}'", rate)
        return rate
    }
}
