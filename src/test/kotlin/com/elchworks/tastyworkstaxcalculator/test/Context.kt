package com.elchworks.tastyworkstaxcalculator.test

import com.elchworks.tastyworkstaxcalculator.portfolio.NewTransactionEvent
import com.elchworks.tastyworkstaxcalculator.transactions.Transaction
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class Context(
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun publishTx(tx: Transaction) {
        eventPublisher.publishEvent(NewTransactionEvent(tx))
    }
}