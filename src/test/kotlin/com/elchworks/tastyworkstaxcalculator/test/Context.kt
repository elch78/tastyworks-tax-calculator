package com.elchworks.tastyworkstaxcalculator.test

import com.elchworks.tastyworkstaxcalculator.portfolio.NewTransactionEvent
import com.elchworks.tastyworkstaxcalculator.transactions.Action.BUY_TO_OPEN
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_CLOSE
import com.elchworks.tastyworkstaxcalculator.transactions.Transaction
import com.elchworks.tastyworkstaxcalculator.usd
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Month
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

    fun sellStock(quantity: Int, price: MonetaryAmount) = publishTx(
        defaultStockTrade().copy(
            action = SELL_TO_CLOSE,
            quantity = quantity,
            averagePrice = price
        )
    )

    fun reverseSplit(originalQuantity: Int, newQuantity: Int) {
        val splitDate = randomDate(YEAR_2022, Month.FEBRUARY);
        publishTx(
            defaultReverseSplitTransaction().copy(
                date = splitDate,
                quantity = originalQuantity,
                // price of the reverse split transaction must be ignored. The original
                // buy price of the portfolio has to be retained for the tax calculation.
                averagePrice = randomUsdAmount(),
                action = SELL_TO_CLOSE
            )
        )
        publishTx(
            defaultReverseSplitTransaction().copy(
                date = splitDate,
                quantity = newQuantity,
                // price of the reverse split transaction must be ignored. The original
                // buy price of the portfolio has to be retained for the tax calculation.
                averagePrice = randomUsdAmount(),
                action = BUY_TO_OPEN
            )
        )
    }

}