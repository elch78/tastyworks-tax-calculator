package com.elchworks.tastyworkstaxcalculator.convert

import com.opencsv.CSVReaderBuilder
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.io.FileReader
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Component
class ExchangeRateRepository {
    private val log = LoggerFactory.getLogger(ExchangeRateRepository::class.java)

    // source: https://de.statista.com/statistik/daten/studie/214878/umfrage/wechselkurs-des-euro-gegenueber-dem-us-dollar-monatliche-entwicklung/
    private val rates = HashMap<LocalDate, BigDecimal>()

    private val ONE_SCALE_2 = BigDecimal.ONE.setScale(2)

    @PostConstruct
    fun readCsv() {
        val ecbHistoricExchangeRates = ClassPathResource("eurofxref-hist.csv").file
        CSVReaderBuilder(FileReader(ecbHistoricExchangeRates.absoluteFile))
            .withSkipLines(1)// skip header
            .build()
            .readAll()
            .forEach {
                val date = LocalDate.parse(it[0])
                val rate = BigDecimal(it[1])
                log.debug("rate $date $rate")
                rates[date] = rate
            }
    }

    fun monthlyRateUsdToEur(date: LocalDate): BigDecimal {
        val rateEurToUsd = (rates[date] ?: error("No rate for date $date"))
        val rate = ONE_SCALE_2.divide(rateEurToUsd, RoundingMode.HALF_UP)
        log.debug("usdToEur rate='{}'", rate)
        return rate
    }
}
