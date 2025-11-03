workspace "Tastyworks Tax Calculator" "Component architecture for a Kotlin/Spring Boot CLI application that calculates German tax reports from option trades" {

    model {
        cliApp = softwareSystem "Tastyworks Tax Calculator" {

            cliContainer = container "CLI Application" "Command-line application that processes transactions and generates tax reports" "Kotlin/Spring Boot" {
                applicationRunner = component "ApplicationRunner" "Main entry point, coordinates the flow" "Kotlin/Spring Boot" {
                    tags "Entry Point"
                }

                transactionCsvReader = component "TransactionCsvReader" "Parses Tastyworks CSV files" "Kotlin" {
                    tags "Support"
                }

                portfolio = component "Portfolio" "Tracks and manages all trading positions" "Kotlin" {
                    tags "Core Domain"
                }

                fiscalYear = component "FiscalYear" "Manages tax calculations across fiscal years" "Kotlin" {
                    tags "Core Domain"
                }

                currencyExchange = component "CurrencyExchange" "Converts USD to EUR for tax reporting" "Kotlin" {
                    tags "Support"
                }

                snapshotService = component "SnapshotService" "Saves and restores application state as JSON" "Kotlin" {
                    tags "Support"
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
            }


            # Relationships - Main flow
            applicationRunner -> snapshotService "Load/Save state"

            snapshotService -> portfolio "Restores"
            snapshotService -> fiscalYear "Restores"
            applicationRunner -> transactionCsvReader "Reads transactions via"

            applicationRunner -> newTransactionEvent "Publishes"

            # Event-driven flows
            portfolio -> newTransactionEvent "Listens to"
            portfolio -> positionEvents "Publishes"

            fiscalYear -> positionEvents "listens to"
            fiscalYear -> profitLossEvents "publishes"
            fiscalYear -> currencyExchange "convert monetary amounts"
            snapshotService -> positionEvents "Listens to"
            snapshotService -> profitLossEvents "Listens to"

            # Report generation
            applicationRunner -> fiscalYear "Prints reports via"
        }
    }

    views {
        component cliContainer "CoreDomain" {
            include *
            exclude snapshotService profitLossEvents
        }

        component cliContainer "snapshotService" {
            include ->snapshotService->
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
            element "Support" {
                background #a8c5e0
                color #000000
            }
            element "Repository" {
                background #c0d9ed
                color #000000
            }
            element "Event" {
                background #90ee90
                color #000000
                shape Ellipse
            }
        }
    }

}
