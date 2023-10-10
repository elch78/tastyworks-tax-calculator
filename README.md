This is a command line program to calculate win and loss from option trades with the Tastyworks
broker for the german tax.

# Run
- Download your transactions from the Tastyworks application and store them in a directory.
- Download the exchange rates from ecb and put the im the resources directory.
- Run the application and provide the directory where the Tastywork transaction csv files are located:
`--transactionsDir=...`

# Exchange Rates
Exchange rates are static from file `src/main/resources/eurofxref-hist.csv`. Download time series from ECB to update
https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.zip?9c8efb82354da0f4694f95dfb4a4e167
