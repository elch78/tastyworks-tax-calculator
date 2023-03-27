package com.elchworks.tastyworkstaxcalculator.test.positions

import com.elchworks.tastyworkstaxcalculator.eur
import com.elchworks.tastyworkstaxcalculator.positions.ProfitAndLoss
import com.elchworks.tastyworkstaxcalculator.positions.plus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProfitAndLossTest {
    @Test
    fun plusOperator() {
        // Given
        val profitAndLoss1 = ProfitAndLoss(eur(1), eur(2))
        val profitAndLoss2 = ProfitAndLoss(eur(1), eur(2))

        // Then
        assertThat(profitAndLoss1 + profitAndLoss2).isEqualTo(ProfitAndLoss(eur(2), eur(4)))
    }

    @Test
    fun plusAssignmentOperator() {
        // Given
        var profitAndLoss1 = ProfitAndLoss(eur(1), eur(2))
        profitAndLoss1 += ProfitAndLoss(eur(1), eur(2))


        // Then
        assertThat(profitAndLoss1).isEqualTo(ProfitAndLoss(eur(2),eur(4F)))
    }
}
