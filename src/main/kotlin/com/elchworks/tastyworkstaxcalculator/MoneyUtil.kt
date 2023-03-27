package com.elchworks.tastyworkstaxcalculator

import org.javamoney.moneta.Money
import javax.money.MonetaryAmount

operator fun MonetaryAmount.times(other: Number): MonetaryAmount = this.multiply(other)
operator fun MonetaryAmount.plus(other: MonetaryAmount): MonetaryAmount = this.add(other)
operator fun MonetaryAmount.minus(other: MonetaryAmount): MonetaryAmount = this.subtract(other)

fun Float.toMonetaryAmountUsd(): MonetaryAmount = Money.of(this, "USD")
fun MonetaryAmount.toEur(): MonetaryAmount = Money.of(this.number, "EUR")

fun eur(amount: Number): MonetaryAmount = Money.of(amount, "EUR")
fun usd(amount: Number): MonetaryAmount = Money.of(amount, "USD")
