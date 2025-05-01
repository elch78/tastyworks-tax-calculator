Feature: Simple Assignment
  Scenario: Put option is assigned
    Given Exchange rate on "11/01/24" is "2.00" USD to EUR
    When Sell option "CLF 11/01/24 Put 13.50 @ 0.32" on "11/01/24"
    And Assignment "CLF 11/01/24 Put 13.50 @ 0.32"
    Then Portfolio should have a stock position for symbol "CLF" with quantity 100
    And Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 0.0