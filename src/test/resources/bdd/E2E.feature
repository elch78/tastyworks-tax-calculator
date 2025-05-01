Feature: Simple Assignment
  Scenario: Put option is assigned
    Given Fixed exchange rate of "2.00" USD to EUR
    When Sell option "CLF 15/01/24 Put 13.50 @ 0.32" on "10/01/24"
    Then Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 0.0
    When Assignment "CLF 15/01/24 Put 13.50 @ 0.32"
    Then Portfolio should have a stock position for symbol "CLF" with quantity 100
    And Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 0.0
    When Sell option "CLF 25/01/24 Call 14.50 @ 0.32" on "20/01/24"
    Then Profits for fiscal year 2024 should be options profits 128.0 losses 0.0 stocks 0.0
    When Assignment "CLF 25/01/24 Call 14.50 @ 0.32"
    Then Profits for fiscal year 2024 should be options profits 128.0 losses 0.0 stocks 200.0
#    And Portfolio should have a stock position for symbol "CLF" with quantity 0