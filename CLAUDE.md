# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kotlin/Spring Boot command-line application that calculates profits and losses from option trades with the Tastyworks broker for German tax purposes. The application processes CSV transaction files and generates tax reports considering currency exchange rates.

## Coding Guidelines

## Coding Principles

We prefer simple, clean, maintainable solutions over clever or complex ones.

Readability and maintainability are primary concerns.

Self-documenting names and code. The WHAT should be clear from the code, Comments should only be used to document the
WHY.

Small functions.

Follow single responsibility principles in classes and functions.

The project is developed by Continuous Delivery principles. I.e. trunk based development is practiced. All changes
should be integrated at least once per day. Changes should be made in small iterative steps.

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
  - methods should log their inputs on debug level and the result on info or debug
    
    
## Development Commands

### Building and Testing
- `./mvnw clean package` - Assembles and tests the project
- `./mvnw test` - Runs the test suite (includes unit tests and BDD tests)
- `./mvnw clean` - Deletes the build directory
- `./mvnw compiler:compile` - Compiles Kotlin source code
- `./mvnw spring-boot:run --transactionsDir=/path/to/transactions` - Run the application

### Running the Application
The application requires a command-line argument:
```bash
./mvnw spring-boot:run --transactionsDir=/path/to/transactions
```

### Testing Framework
- Uses JUnit 5 as the primary testing framework
- Cucumber BDD tests are located in `src/test/resources/bdd/E2E.feature`
- Test files follow the pattern `*Test.kt`
- Run individual test: `./mvnw test -Dtest="ClassName"`

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

### Domain Rules

- Losses from options can be deducted only from profits from options in the same year up to a certain limit (€10000)
- Losses from options cannot be deducted from profits from stocks

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

Run BDD tests with: `./gradlew test`