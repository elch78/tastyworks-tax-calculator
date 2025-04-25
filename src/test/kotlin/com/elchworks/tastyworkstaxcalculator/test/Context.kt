package com.elchworks.tastyworkstaxcalculator.test

import com.elchworks.tastyworkstaxcalculator.portfolio.NewTransactionEvent
import com.elchworks.tastyworkstaxcalculator.transactions.Action.BUY_TO_OPEN
import com.elchworks.tastyworkstaxcalculator.transactions.Transaction
import com.elchworks.tastyworkstaxcalculator.usd
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import javax.money.MonetaryAmount

@Component
class Context(
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun publishTx(tx: Transaction) {
        eventPublisher.publishEvent(NewTransactionEvent(tx))
    }

    fun assignedPut(premium: MonetaryAmount = ZERO_USD, strikePrice: MonetaryAmount = usd(100.00)) {
        publishTx(
            defaultOptionStoTx().copy(
                callOrPut = "PUT",
                value = premium,
            )
        )
        publishTx(
            defaultAssignment().copy(
                callOrPut = "PUT",
                averagePrice = strikePrice,
            )
        )
        publishTx(
            defaultStockTrade().copy(
                action = BUY_TO_OPEN,
                averagePrice = strikePrice.negate(),
            )
        )
    }

}