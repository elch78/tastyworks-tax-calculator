package com.elchworks.tastyworkstaxcalculator.positions

import com.elchworks.tastyworkstaxcalculator.transactions.Transaction

data class NewTransactionEvent(val tx: Transaction)
