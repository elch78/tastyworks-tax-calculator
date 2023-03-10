package com.elchworks.tastyworkstaxcalculator.test.positions

import com.elchworks.tastyworkstaxcalculator.positions.ProfitAndLoss
import com.elchworks.tastyworkstaxcalculator.positions.plus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProfitAndLossTest {
    @Test
    fun plusOperator() {
        // Given
        val profitAndLoss1 = ProfitAndLoss(1F, 2F)
        val profitAndLoss2 = ProfitAndLoss(1F, 2F)

        // Then
        assertThat(profitAndLoss1 + profitAndLoss2).isEqualTo(ProfitAndLoss(2F,4F))
    }

    @Test
    fun plusAssignmentOperator() {
        // Given
        var profitAndLoss1 = ProfitAndLoss(1F, 2F)
        profitAndLoss1 += ProfitAndLoss(1F, 2F)


        // Then
        assertThat(profitAndLoss1).isEqualTo(ProfitAndLoss(2F,4F))
    }
}
