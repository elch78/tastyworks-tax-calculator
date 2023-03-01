package com.elchworks.tastyworkstaxcalculator.positions

import com.elchworks.tastyworkstaxcalculator.ExchangeRate
import com.elchworks.tastyworkstaxcalculator.transactions.NewTransactionEvent
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
        when (tx.action) {
            "SELL_TO_OPEN" -> openPosition(tx)
            "BUY_TO_CLOSE" -> closePosition(tx)
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

    private fun Trade.key() = "${this.callOrPut}-${this.rootSymbol}-${this.expirationDate}-${this.strikePrice}"
}
