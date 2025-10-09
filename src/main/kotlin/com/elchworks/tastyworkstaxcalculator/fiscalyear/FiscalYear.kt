package com.elchworks.tastyworkstaxcalculator.fiscalyear

import com.elchworks.tastyworkstaxcalculator.convert.CurrencyExchange
import com.elchworks.tastyworkstaxcalculator.eur
import com.elchworks.tastyworkstaxcalculator.format
import com.elchworks.tastyworkstaxcalculator.plus
import com.elchworks.tastyworkstaxcalculator.portfolio.Profit
import com.elchworks.tastyworkstaxcalculator.portfolio.ProfitAndLoss
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionBuyToCloseEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionSellToOpenEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.plus
import com.elchworks.tastyworkstaxcalculator.portfolio.stock.StockSellToCloseEvent
import com.elchworks.tastyworkstaxcalculator.times
import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade
import com.elchworks.tastyworkstaxcalculator.transactions.Transaction
import com.elchworks.tastyworkstaxcalculator.transactions.optionDescription
import com.elchworks.tastyworkstaxcalculator.transactions.year
import org.javamoney.moneta.Money
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.Year
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.money.MonetaryAmount

class FiscalYear(
    private val currencyExchange: CurrencyExchange,
    val fiscalYear: Year,
) {
    private var profitAndLossFromOptions = ProfitAndLoss()
    private var profitAndLossFromStocks = eur(0)
    private val log = LoggerFactory.getLogger(FiscalYear::class.java)

    fun profits(): ProfitsSummary = ProfitsSummary(
        profitsFromOptions = profitAndLossFromOptions.profit,
        lossesFromOptions = profitAndLossFromOptions.loss,
        profitsFromStocks = profitAndLossFromStocks
    )

    fun onOptionPositionOpened(stoEvent: OptionSellToOpenEvent) {
        val stoTx = stoEvent.stoTx
        val premium = txValueInEur(stoTx)
        profitAndLossFromOptions += ProfitAndLoss(premium, Money.of(0, "EUR"))
        log.info("{} Option STO: {} premium {}", fiscalYear, stoTx.symbol, format(premium))
    }

    fun onOptionPositionClosed(btcEvent: OptionBuyToCloseEvent) {
        val btcTx = btcEvent.btcTx
        val stoTx = btcEvent.stoTx
        val premium = txValueInEur(stoTx)
        val buyValue = txValueInEur(btcTx)
        val profitAndLoss = if(positionWasOpenedInThisFiscalYear(stoTx)) {
            val netProfit = netProfit(btcTx, stoTx, btcEvent.quantitySold)
            /*
             * This is due to german tax laws.
             * It is only allowed to deduct losses from option trades from profits from option trades
             * (but from profits from stock trades).
             * So we need to track losses from option trades separately
             */
            if(isLoss(netProfit)) {
                // is loss. Reduce profit by the whole premium, increase loss by netProfit
                log.info("Close with net loss. position='{}', netProfit='{}',",
                    stoTx.optionDescription(), format(netProfit))
                ProfitAndLoss(premium.negate(), netProfit.negate())
            } else {
                // no loss. Only reduce profit by buyValue.
                log.debug("Close with net profit. position='{}', netProfit='{}'",
                    stoTx.optionDescription(), format(netProfit))
                ProfitAndLoss(buyValue, eur(0))
            }
        } else {
            // the position was not opened in the same year. The whole value of the buy transaction is a loss for the current year
            log.debug("Option position closed that was opened in a different fiscal year.")
            ProfitAndLoss(eur(0), buyValue.negate())
        }
        profitAndLossFromOptions += profitAndLoss
        log.info(
            "{} Option BTC: {} profitAndLoss='{}', profitAndLossFromOptions='{}'",
            fiscalYear,
            stoTx.optionDescription(), profitAndLoss, format(profitAndLossFromOptions.profit)
        )
    }

    fun onStockPositionClosed(event: StockSellToCloseEvent) {
        val btoTx = event.btoTx
        val stcTx = event.stcTx
        val quantity = event.quantitySold
        val netProfit = netProfit(btoTx, stcTx, quantity)
        profitAndLossFromStocks += netProfit
        log.info(
            "{} Stock STC: {} profitAndLoss='{}', profitFromStocks='{}'",
            fiscalYear,
            stcTx.description,
            format(netProfit),
            format(profitAndLossFromStocks)
        )
    }

    private fun netProfit(buyTx: Transaction, sellTx: Transaction, quantity: Int): MonetaryAmount {
        val sellPrice = sellTx.averagePrice
        val buyPrice = buyTx.averagePrice
        log.debug("netProfit quantity='{}', buyPrice='{}', sellPrice='{}'", quantity, format(buyPrice), format(sellPrice))
        val buyValue = buyPrice * quantity
        val buyValueEur = currencyExchange.usdToEur(buyValue, buyTx.date)
        val sellValue = sellPrice * quantity
        val sellValueEur = currencyExchange.usdToEur(sellValue, sellTx.date)
        // buy value is negative. Thus, we have to add the values
        val netProfit = sellValueEur + buyValueEur
        log.debug(
            "{} profit {} {} {} buy {} {} sell {} {} ",
            fiscalYear,
            format(netProfit),
            buyTx.quantity,
            buyTx.symbol,
            buyTx.date.formatDateOnly(),
            format(buyValueEur),
            sellTx.date.formatDateOnly(),
            format(sellValueEur)
        )
        return netProfit
    }

    private fun isLoss(netProfit: MonetaryAmount): Boolean {
        val isLoss = netProfit.isNegative
        log.debug("isLoss netProfit='{}', isLoss='{}'", format(netProfit), isLoss)
        return isLoss
    }

    private fun positionWasOpenedInThisFiscalYear(stoTx: OptionTrade): Boolean {
        val stoYear = stoTx.year()
        val positionWasOpenedInThisFiscalYear = stoYear == fiscalYear
        log.debug("positionWasOpenedInThisFiscalYear stoYear='{}', fiscalYear='{}', positionWasOpenedInThisFiscalYear='{}'", stoYear, fiscalYear, positionWasOpenedInThisFiscalYear)
        return positionWasOpenedInThisFiscalYear
    }

    private fun txValueInEur(btcTx: OptionTrade) = currencyExchange.usdToEur(btcTx.value, btcTx.date)

    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    // Use the same timezone as the currency exchange .. just in case
    fun Instant.formatDateOnly(): String = atZone(ZoneId.of("CET")).toLocalDate().format(dateFormatter)
}
