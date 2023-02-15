package com.elchworks.tastyworkstaxcalculator

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class PositionsManager(
    private val eventPublisher: ApplicationEventPublisher
) {
    private val log = LoggerFactory.getLogger(PositionsManager::class.java)
    private val positions = mutableMapOf<String, OptionPosition>()

    fun process(transactions: List<Transaction>) {
        transactions
            .sortedBy { it.date }
            .forEach {
            when (it.action) {
                "SELL_TO_OPEN" -> openPosition(it)
                "BUY_TO_CLOSE" -> closePosition(it)
            }
        }
        positions.values.forEach{
            log.info("expired or still open {}", it)
        }
    }

    private fun closePosition(tx: Transaction) {
        val position = positions[tx.key()]!!
        positions.remove(tx.key())
        log.debug("closed position='{}'", position)
        eventPublisher.publishEvent(OptionBuyToCloseEvent(position, tx))
    }

    private fun openPosition(
        tx: Transaction
    ) {
        val position = OptionPosition.fromTransction(tx)
        positions[tx.key()] = position
        log.debug("opened position='{}'", position)
        eventPublisher.publishEvent(OptionSellToOpenEvent(position, tx))
    }
}
