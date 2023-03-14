package com.elchworks.tastyworkstaxcalculator.positions

import com.elchworks.tastyworkstaxcalculator.transactions.StockTransaction

data class StockSellToCloseEvent(
    val btoTx: StockTransaction,
    val stcTx: StockTransaction,
    val quantitySold: Int,
)
