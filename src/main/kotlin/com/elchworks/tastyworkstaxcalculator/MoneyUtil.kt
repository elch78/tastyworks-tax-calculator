package com.elchworks.tastyworkstaxcalculator

import org.javamoney.moneta.Money
import java.text.NumberFormat
import java.util.*
import javax.money.MonetaryAmount

operator fun MonetaryAmount.times(other: Number): MonetaryAmount = this.multiply(other)
operator fun MonetaryAmount.plus(other: MonetaryAmount): MonetaryAmount = this.add(other)
operator fun MonetaryAmount.minus(other: MonetaryAmount): MonetaryAmount = this.subtract(other)

fun Float.toMonetaryAmountUsd(): MonetaryAmount = Money.of(this, "USD")
fun MonetaryAmount.toEur(): MonetaryAmount = Money.of(this.number, "EUR")

fun eur(amount: Number): MonetaryAmount = Money.of(amount, "EUR")
fun usd(amount: Number): MonetaryAmount = Money.of(amount, "USD")

val usdFormat = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
    currency = Currency.getInstance("USD")
}
val eurFormat = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
    currency = Currency.getInstance("EUR")
}

fun format(monetaryAmount: MonetaryAmount): String? {
    val currency = monetaryAmount.currency
    val numberFormat = when (currency.currencyCode) {
        "USD" -> usdFormat
        "EUR" -> eurFormat
        else -> throw RuntimeException("Unexpected currency $currency")
    }
    return numberFormat.format(monetaryAmount.number)
}
