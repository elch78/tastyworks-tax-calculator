package com.elchworks.tastyworkstaxcalculator.convert

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class ExchangeRateRepositoryTest {
    @Test
    fun readCsv() {
        // Given
        val sut = ExchangeRateRepository()

        // When
        sut.readCsv()
        val rate = sut.dailyRateUsdToEur(LocalDate.of(2024, 1, 25))

        // Then
        assertThat(rate).isEqualTo(BigDecimal.valueOf(0.9180).setScale(4))
    }
}
