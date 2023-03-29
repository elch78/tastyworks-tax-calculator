package com.elchworks.tastyworkstaxcalculator.positions.stock

import com.elchworks.tastyworkstaxcalculator.transactions.StockTransaction

data class StockSellToCloseEvent(
    val btoTx: StockTransaction,
    val stcTx: StockTransaction,
    val quantitySold: Int,
)
