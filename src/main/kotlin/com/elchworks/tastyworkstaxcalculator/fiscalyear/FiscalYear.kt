package com.elchworks.tastyworkstaxcalculator.fiscalyear

import com.elchworks.tastyworkstaxcalculator.convert.CurrencyExchange
import com.elchworks.tastyworkstaxcalculator.eur
import com.elchworks.tastyworkstaxcalculator.format
import com.elchworks.tastyworkstaxcalculator.plus
import com.elchworks.tastyworkstaxcalculator.positions.Profit
import com.elchworks.tastyworkstaxcalculator.positions.ProfitAndLoss
import com.elchworks.tastyworkstaxcalculator.positions.option.OptionBuyToCloseEvent
import com.elchworks.tastyworkstaxcalculator.positions.option.OptionSellToOpenEvent
import com.elchworks.tastyworkstaxcalculator.positions.plus
import com.elchworks.tastyworkstaxcalculator.positions.stock.StockSellToCloseEvent
import com.elchworks.tastyworkstaxcalculator.times
import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade
import com.elchworks.tastyworkstaxcalculator.transactions.optionDescription
import com.elchworks.tastyworkstaxcalculator.transactions.year
import org.javamoney.moneta.Money
import org.slf4j.LoggerFactory
import java.time.Year
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
        log.info("Option position opened. position='{}', premium='{}', profit='{}', loss='{}', fiscalYear='{}'",
            stoTx.optionDescription(), format(premium), format( profitAndLossFromOptions.profit), format(profitAndLossFromOptions.loss), fiscalYear)
    }

    fun onOptionPositionClosed(btcEvent: OptionBuyToCloseEvent) {
        val btcTx = btcEvent.btcTx
        val stoTx = btcEvent.stoTx
        if(positionWasOpenedInThisFiscalYear(stoTx)) {
            val premium = txValueInEur(stoTx)
            val buyValue = txValueInEur(btcTx)
            val netProfit = netProfit(premium, buyValue)
            if(isLoss(netProfit)) {
                // is loss. Reduce profit by the whole premium, increase loss by netProfit
                profitAndLossFromOptions += ProfitAndLoss(premium.negate(), netProfit.negate())
                log.info("Close with net loss. position='{}', netProfit='{}', profit='{}', loss='{}'",
                    stoTx.optionDescription(), format(netProfit), format(profitAndLossFromOptions.profit), format(profitAndLossFromOptions.loss))
            } else {
                // no loss. Only reduce profit by buyValue.
                profitAndLossFromOptions += ProfitAndLoss(buyValue, eur(0))
                log.info("Close with net profit. position='{}', netProfit='{}', profit='{}', loss='{}'",
                    stoTx.optionDescription(), format(netProfit), format(profitAndLossFromOptions.profit), format(profitAndLossFromOptions.loss))
            }
        } else {
            // the position was not opened in the same year. The whole value of the buy transaction is a loss for the current year
            val netProfit = txValueInEur(btcTx)
            profitAndLossFromOptions += ProfitAndLoss(eur(0), netProfit.negate())
            log.info("Option position closed that was opened in a different fiscal year. netProfit='{}', profitAndLoss='{}'", format(netProfit), profitAndLossFromOptions)
        }
    }

    fun onStockPositionClosed(event: StockSellToCloseEvent) {
        val symbol = event.btoTx.symbol
        val quantitySold = event.quantitySold
        val sellPrice = event.stcTx.averagePrice
        val buyPrice = event.btoTx.averagePrice
        log.debug("onStockPositionClosed symbol='{}', quantitySold='{}', buyPrice='{}', sellPrice='{}'", symbol, quantitySold, format(buyPrice), format(sellPrice))
        val buyValue = buyPrice * quantitySold
        val buyValueEur = currencyExchange.usdToEur(Profit(buyValue, event.btoTx.date))
        log.debug("onStockPositionClosed buyValue='{}', buyValueEur='{}'", format(buyValue), format(buyValueEur))
        val sellValue = sellPrice * quantitySold
        val sellValueEur = currencyExchange.usdToEur(Profit(sellValue, event.stcTx.date))
        log.debug("onStockPositionClosed sellValue='{}', sellValueEur='{}'", format(sellValue), format(sellValueEur))
        // buy value is negative. Thus we have to add the values
        val netProfit = sellValueEur + buyValueEur
        profitAndLossFromStocks += netProfit
        log.info("Stock sold. symbol='{}', quantity='{}', netProfit='{}', profitFromStocks='{}'",
            symbol, quantitySold, format(netProfit), format(profitAndLossFromStocks))
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

    private fun netProfit(premium: MonetaryAmount, buyValue: MonetaryAmount): MonetaryAmount {
        // buyValue is negative
        val netProfit = premium + buyValue
        log.debug("netProfit premium='{}', buyValue='{}', netProfit='{}'", format(premium), format(buyValue), format(netProfit))
        return netProfit
    }

    private fun txValueInEur(btcTx: OptionTrade) = currencyExchange.usdToEur(Profit(btcTx.value, btcTx.date))

}
