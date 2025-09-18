# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kotlin/Spring Boot command-line application that calculates profits and losses from option trades with the Tastyworks broker for German tax purposes. The application processes CSV transaction files and generates tax reports considering currency exchange rates.

## Coding Guidelines

### Clean Code
- The code should be self explaining. I.e. it should be clear from the code itself WHAT the code is doing.
- Comments should only be used to document the WHY not the WHAT (that should be clear from the code).

### Logging
- all code should use log levels
  - error for critical errors
  - warn for errors that can be tolerated
  - info for successful steps
  - debug for debugging. The debug log should be able to replace a debugger. I.e. the log should enable a developer to
    comprehend the control flow of the program. e.g.
    ```
    var inputA: boolean
    var inputB: boolean
    var result = a && b
    log.debug("method inputA='{}', inputB='{}', result='{}'", inputA, inputB, result)
    if(result) {
      ...
    }
    ```
  - methods should log their inputs on debug leve and the result on info or debug
    
    
## Development Commands

### Building and Testing
- `./gradlew build` - Assembles and tests the project
- `./gradlew test` - Runs the test suite (includes unit tests and BDD tests)
- `./gradlew clean` - Deletes the build directory
- `./gradlew compileKotlin` - Compiles Kotlin source code
- `./gradlew bootRun --args="--transactionsDir=/path/to/transactions"` - Run the application

### Running the Application
The application requires a command-line argument:
```bash
./gradlew bootRun --args="--transactionsDir=/path/to/transactions"
```

### Testing Framework
- Uses JUnit 5 as the primary testing framework
- Cucumber BDD tests are located in `src/test/resources/bdd/E2E.feature`
- Test files follow the pattern `*Test.kt`
- Run individual test: `./gradlew test --tests "ClassName"`

## Architecture Overview

### Core Components
- **ApplicationRunner**: Main entry point that processes transaction CSV files and publishes events
- **Portfolio**: Central domain model that tracks positions and calculates profits/losses
- **FiscalYearManager**: Manages tax calculations across fiscal years
- **TransactionCsvReader**: Handles parsing of Tastyworks CSV files

### Package Structure
- `com.elchworks.tastyworkstaxcalculator.portfolio` - Core trading position logic
  - `option/` - Option-specific position handling (buy/sell, assignments, expirations)
  - Option positions support partial closes and complex scenarios
- `com.elchworks.tastyworkstaxcalculator.fiscalyear` - Tax year calculations
- `com.elchworks.tastyworkstaxcalculator.convert` - Currency conversion logic
- `com.elchworks.tastyworkstaxcalculator.transactions` - CSV transaction processing

### Event-Driven Architecture
The application uses Spring's event publishing mechanism:
- `NewTransactionEvent` - Published for each processed transaction
- Portfolio components listen to these events to update positions

### Key Domain Concepts
- **Option Positions**: Tracks short/long option positions with partial close support
- **Stock Positions**: Handles stock positions from option assignments
- **Profit and Loss Calculation**: Considers currency exchange rates for EUR tax reporting
- **Fiscal Year Management**: Separates profits/losses by German tax years

## Configuration

### Exchange Rates
- Static exchange rates are loaded from `src/main/resources/eurofxref-hist.csv`
- Download updated rates from: https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.zip

## Dependencies

Key external libraries:
- Spring Boot 3.4.5 with Kotlin support
- Money API (javax.money) for currency handling
- OpenCSV for transaction file parsing
- Cucumber for BDD testing
- Mockito for unit testing

## Testing Strategy

The project uses comprehensive BDD scenarios covering:
- the existing e2e tests in End2EndTest.kt are to be migrated to BDD (E2E.feature)
- Simple option roundtrips (sell-to-open → assignment/expiration)
- Currency exchange rate impact on profits/losses
- Cross-fiscal-year transactions
- Partial position closes
- Multiple position scenarios

Run BDD tests with: `./gradlew test`