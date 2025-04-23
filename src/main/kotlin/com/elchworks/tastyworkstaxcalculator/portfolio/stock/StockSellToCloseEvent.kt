package com.elchworks.tastyworkstaxcalculator.portfolio.stock

import com.elchworks.tastyworkstaxcalculator.transactions.StockTransaction

data class StockSellToCloseEvent(
    val btoTx: StockTransaction,
    val stcTx: StockTransaction,
    val quantitySold: Int,
)
