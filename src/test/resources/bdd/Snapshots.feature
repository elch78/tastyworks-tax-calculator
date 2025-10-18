Feature: Persistent State Snapshots

  Scenario: Create snapshot after processing transactions
    Given Fixed exchange rate of "2.00" USD to EUR
    And a clean state with no snapshots
    When Sell option "CLF 15/01/24 Put 13.50 @ 0.32" on "10/01/24"
    And Buy option "CLF 15/01/24 Put 13.50 @ 0.32" on "15/01/24"
    Then a snapshot file should be created in "snapshots/" directory
    And the snapshot filename should match format "snapshot-2024-01-15-*.json"
    And Profits for fiscal year 2024 should be options profits 0.0 losses 0.0 stocks 0.0

  Scenario: Serialize and deserialize portfolio snapshot
    Given Fixed exchange rate of "2.00" USD to EUR
    When Sell option "CLF 15/01/24 Put 13.50 @ 0.32" on "10/01/24"
    And a snapshot is created
    And the portfolio is restored from the snapshot
    Then Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 0.0
    And Portfolio should have an option position "CLF 15/01/24 Put 13.50 @ 0.32" with quantity 1

  Scenario: Resume with open option positions from snapshot
    Given Fixed exchange rate of "2.00" USD to EUR
    When Sell option "CLF 20/02/24 Put 15.00 @ 0.10" on "10/01/24" quantity 2
    And a snapshot is created
    And the portfolio is restored from the snapshot
    Then Profits for fiscal year 2024 should be options profits 40.0 losses 0.0 stocks 0.0
    When Buy option "CLF 20/02/24 Put 15.00 @ 0.10" on "15/01/24" quantity 1
    Then Profits for fiscal year 2024 should be options profits 20.0 losses 0.0 stocks 0.0
    And Portfolio should have an option position "CLF 20/02/24 Put 15.00 @ 0.10" with quantity 1

  Scenario: Resume with stock positions from snapshot
    Given Fixed exchange rate of "2.00" USD to EUR
    When Sell option "CLF 15/01/24 Put 13.50 @ 0.32" on "10/01/24"
    And Assignment "CLF 15/01/24 Put 13.50 @ 0.32"
    And a snapshot is created
    And the portfolio is restored from the snapshot
    Then Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 0.0
    And Portfolio should have a stock position for symbol "CLF" with quantity 100
    When Sell stock 100 "CLF" on "20/01/24" average price: "14.50"
    Then Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 200.0

  Scenario: Resume across fiscal years
    Given Fixed exchange rate of "2.00" USD to EUR
    When Sell option "CLF 15/02/24 Put 13.50 @ 0.32" on "31/12/24"
    And a snapshot is created
    And the portfolio is restored from the snapshot
    Then Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 0.0
    When Buy option "CLF 15/02/24 Put 13.50 @ 0.32" on "01/01/25"
    Then Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 0.0
    And Profits for fiscal year 2025 should be options profits 0.0 losses 64.0 stocks 0.0

  Scenario: Snapshot file operations
    Given Fixed exchange rate of "2.00" USD to EUR
    And a clean snapshots directory
    When Sell option "CLF 15/01/24 Put 13.50 @ 0.32" on "10/01/24"
    And a snapshot is saved to file
    Then a snapshot file should exist
    And the snapshot filename should match format "snapshot-2024-01-10-*.json"
    When the snapshot is loaded from file
    Then the snapshot last transaction date should be "10/01/24"
