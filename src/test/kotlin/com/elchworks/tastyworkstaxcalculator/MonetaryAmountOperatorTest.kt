package com.elchworks.tastyworkstaxcalculator

import org.assertj.core.api.Assertions
import org.javamoney.moneta.Money
import org.junit.jupiter.api.Test

class MonetaryAmountOperatorTest {
    @Test
    fun test() {
        // Given
        val a = Money.of(1, "EUR")
        val b = Money.of(1, "EUR")


        Assertions.assertThat( a + b ).isEqualTo( Money.of(2, "EUR"))


        // Then
    }
}
