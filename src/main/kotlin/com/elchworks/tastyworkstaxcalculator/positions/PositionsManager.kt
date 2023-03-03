package com.elchworks.tastyworkstaxcalculator.positions

import com.elchworks.tastyworkstaxcalculator.ExchangeRate
import com.elchworks.tastyworkstaxcalculator.positions.OptionPositionStatus.ASSIGNED
import com.elchworks.tastyworkstaxcalculator.positions.OptionPositionStatus.EXPIRED
import com.elchworks.tastyworkstaxcalculator.transactions.Action.BUY_TO_CLOSE
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_OPEN
import com.elchworks.tastyworkstaxcalculator.transactions.NewTransactionEvent
import com.elchworks.tastyworkstaxcalculator.transactions.OptionRemoval
import com.elchworks.tastyworkstaxcalculator.transactions.OptionTransaction
import com.elchworks.tastyworkstaxcalculator.transactions.Trade
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class PositionsManager(
    private val eventPublisher: ApplicationEventPublisher,
    private val exchangeRate: ExchangeRate,
) {
    private val log = LoggerFactory.getLogger(PositionsManager::class.java)
    private val positions = mutableMapOf<String, OptionPosition>()
    private val closedPositions = mutableListOf<OptionPosition>()

    @EventListener(NewTransactionEvent::class)
    fun onNewTransaction(event: NewTransactionEvent) {
        val tx = event.tx
        when {
            tx is Trade && tx.action == SELL_TO_OPEN -> openPosition(tx)
            tx is Trade && tx.action == BUY_TO_CLOSE -> closePosition(tx)
            tx is OptionRemoval -> optionRemoval(tx)
        }
    }

    private fun optionRemoval(tx: OptionRemoval) {
        val position = positions.remove(tx.key())!!

        when(tx.status) {
            ASSIGNED -> position.assigned()
            EXPIRED -> position.expired()
            else -> error("unexpected status ${tx.status}")
        }
    }

    private fun closePosition(btcTx: Trade) {
        val position = positions.remove(btcTx.key())!!
        position.buyToClose(btcTx)
        closedPositions.add(position)
        log.debug("closed position='{}'", position)
        eventPublisher.publishEvent(OptionBuyToCloseEvent(position, btcTx))
    }

    private fun openPosition(tx: Trade) {
        val position = OptionPosition.fromTransction(tx, exchangeRate)
        positions[tx.key()] = position
        log.debug("opened position='{}'", position)
        eventPublisher.publishEvent(OptionSellToOpenEvent(position, tx))
    }

    private fun OptionTransaction.key() = "${this.callOrPut}-${this.rootSymbol}-${this.expirationDate}-${this.strikePrice}"
}
