package com.elchworks.tastyworkstaxcalculator.portfolio.stock

import com.elchworks.tastyworkstaxcalculator.transactions.StockTransaction

data class StockBuyToOpenEvent(
    val btoTx: StockTransaction,
)
