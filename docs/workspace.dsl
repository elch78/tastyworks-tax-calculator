workspace "Tastyworks Tax Calculator" "Component architecture for a Kotlin/Spring Boot CLI application that calculates German tax reports from option trades" {

    model {
        cliApp = softwareSystem "Tastyworks Tax Calculator CLI" {

            # Main entry point
            applicationRunner = component "ApplicationRunner" "Main entry point, coordinates the flow" "Kotlin/Spring Boot" {
                tags "Entry Point"
            }

            # Transaction processing
            transactionCsvReader = component "TransactionCsvReader" "Parses Tastyworks CSV files" "Kotlin" {
                tags "Input"
            }

            # Portfolio management
            portfolio = component "Portfolio" "Tracks and manages all trading positions" "Kotlin" {
                tags "Core Domain"
            }
            optionShortPosition = component "OptionShortPosition" "Manages individual short option positions with FIFO" "Kotlin" {
                tags "Core Domain"
            }
            optionLongPosition = component "OptionLongPosition" "Manages individual long option positions with FIFO" "Kotlin" {
                tags "Core Domain"
            }
            stockPosition = component "StockPosition" "Manages stock positions from assignments" "Kotlin" {
                tags "Core Domain"
            }

            # Fiscal year management
            fiscalYearManager = component "FiscalYearManager" "Manages tax calculations across fiscal years" "Kotlin" {
                tags "Core Domain"
            }
            fiscalYear = component "FiscalYear" "Calculates P&L per year, applies German tax rules" "Kotlin" {
                tags "Core Domain"
            }
            fiscalYearRepository = component "FiscalYearRepository" "Stores and retrieves fiscal year instances" "Kotlin" {
                tags "Repository"
            }

            # Currency conversion
            currencyExchange = component "CurrencyExchange" "Converts USD to EUR for tax reporting" "Kotlin" {
                tags "Service"
            }
            exchangeRateRepository = component "ExchangeRateRepository" "Loads ECB historical exchange rates from CSV" "Kotlin" {
                tags "Repository"
            }

            # Snapshot persistence
            snapshotService = component "SnapshotService" "Saves and restores application state as JSON" "Kotlin" {
                tags "Service"
            }

            # Events (Spring events)
            newTransactionEvent = component "NewTransactionEvent" "Published when transaction is processed" "Event" {
                tags "Event"
            }
            positionEvents = component "Position Events" "PositionOpenedEvent, PositionClosedEvent, etc." "Events" {
                tags "Event"
            }
            profitLossEvents = component "ProfitLoss Events" "OptionProfitEvent, OptionLossEvent, StockProfitEvent" "Events" {
                tags "Event"
            }

            # Relationships - Main flow
            applicationRunner -> snapshotService "Loads state from"
            snapshotService -> portfolio "Restores positions to"
            snapshotService -> fiscalYearRepository "Restores fiscal years to"
            applicationRunner -> transactionCsvReader "Reads transactions via"
            applicationRunner -> newTransactionEvent "Publishes"

            # Event-driven flows
            newTransactionEvent -> portfolio "Listened by"
            newTransactionEvent -> snapshotService "Listened by (validates chronological order)"
            portfolio -> optionShortPosition "Manages"
            portfolio -> optionLongPosition "Manages"
            portfolio -> stockPosition "Manages"
            portfolio -> positionEvents "Publishes"

            positionEvents -> fiscalYearManager "Listened by"
            positionEvents -> snapshotService "Listened by (tracks state)"
            fiscalYearManager -> fiscalYearRepository "Gets FiscalYear from"
            fiscalYearManager -> fiscalYear "Delegates calculations to"
            fiscalYear -> currencyExchange "Converts amounts via"
            currencyExchange -> exchangeRateRepository "Gets rates from"
            fiscalYear -> profitLossEvents "Publishes"
            profitLossEvents -> snapshotService "Listened by (tracks P&L)"

            # Report generation
            applicationRunner -> fiscalYearManager "Prints reports via"
            fiscalYearManager -> fiscalYearRepository "Gets all fiscal years from"

            # Snapshot saving
            applicationRunner -> snapshotService "Saves state via"
        }
    }

    views {
        component cliApp "Components" {
            include *
            autoLayout
        }

        component cliApp "CoreFlow" "Shows the main processing flow" {
            include applicationRunner transactionCsvReader newTransactionEvent portfolio positionEvents fiscalYearManager fiscalYear currencyExchange
            autoLayout
        }

        component cliApp "PortfolioDetails" "Shows portfolio position management" {
            include portfolio optionShortPosition optionLongPosition stockPosition positionEvents
            autoLayout
        }

        styles {
            element "Component" {
                background #438dd5
                color #ffffff
            }
            element "Entry Point" {
                background #1168bd
                color #ffffff
                shape RoundedBox
            }
            element "Core Domain" {
                background #85bbf0
                color #000000
            }
            element "Service" {
                background #a8c5e0
                color #000000
            }
            element "Repository" {
                background #c0d9ed
                color #000000
            }
            element "Input" {
                background #999999
                color #ffffff
            }
            element "Event" {
                background #90ee90
                color #000000
                shape Ellipse
            }
        }
    }

}
