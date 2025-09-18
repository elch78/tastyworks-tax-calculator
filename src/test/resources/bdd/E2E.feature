Feature: Calculation of profits and losses from options trading
  Scenario: Simple roundtrip put-assign-call-assign
    Given Fixed exchange rate of "2.00" USD to EUR
    When Sell option "CLF 15/01/24 Put 13.50 @ 0.32" on "10/01/24"
    Then Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 0.0
    And Portfolio should have an option position "CLF 15/01/24 Put 13.50 @ 0.32" with quantity 1
    When Assignment "CLF 15/01/24 Put 13.50 @ 0.32"
    Then Portfolio should have a stock position for symbol "CLF" with quantity 100
    And Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 0.0
    When Sell option "CLF 25/01/24 Call 14.50 @ 0.32" on "20/01/24"
    Then Profits for fiscal year 2024 should be options profits 128.0 losses 0.0 stocks 0.0
    When Assignment "CLF 25/01/24 Call 14.50 @ 0.32"
    Then Profits for fiscal year 2024 should be options profits 128.0 losses 0.0 stocks 200.0

  Scenario: Simple roundtrip loss due to exchange rate
    Given Exchange rate on "10/01/24" is "2.0" USD to EUR
    And Exchange rate on "15/01/24" is "2.0" USD to EUR
    And Exchange rate on "20/01/24" is "2.0" USD to EUR
    And Exchange rate on "25/01/24" is "1.0" USD to EUR
    When Sell option "CLF 15/01/24 Put 13.50 @ 0.32" on "10/01/24"
    And Assignment "CLF 15/01/24 Put 13.50 @ 0.32"
    And Sell option "CLF 25/01/24 Call 13.50 @ 0.32" on "20/01/24"
    And Assignment "CLF 25/01/24 Call 13.50 @ 0.32"
#    buy for $1350 at rate 2.0 = 2700€ sell at rate 1.0 = 1350 -> loss 1350
    Then Profits for fiscal year 2024 should be options profits 128.0 losses 0.0 stocks -1350.0

  Scenario: Simple roundtrip profit due to exchange rate
    Given Exchange rate on "10/01/24" is "2.0" USD to EUR
    And Exchange rate on "15/01/24" is "2.0" USD to EUR
    And Exchange rate on "20/01/24" is "2.0" USD to EUR
    And Exchange rate on "25/01/24" is "3.0" USD to EUR
    When Sell option "CLF 15/01/24 Put 13.50 @ 0.32" on "10/01/24"
    And Assignment "CLF 15/01/24 Put 13.50 @ 0.32"
    And Sell option "CLF 25/01/24 Call 13.50 @ 0.32" on "20/01/24"
    And Assignment "CLF 25/01/24 Call 13.50 @ 0.32"
#    buy for $1350 at rate 2.0 = 2700€ sell at rate 3.0 = 4050 -> profit 1350
    Then Profits for fiscal year 2024 should be options profits 128.0 losses 0.0 stocks 1350.0

  Scenario: option BUY TO CLOSE with loss in following year
    Given Fixed exchange rate of "2.00" USD to EUR
    When Sell option "CLF 15/01/24 Put 13.50 @ 0.32" on "31/12/24"
    Then Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 0.0
    When Buy option "CLF 15/01/24 Put 13.50 @ 0.32" on "01/01/25"
    Then Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 0.0
    # Complete buy costs in 2025 are loss for that year
    And Profits for fiscal year 2025 should be options profits 0.0 losses 64.0 stocks 0.0
    And Portfolio should have no option position "CLF 15/01/24 Put 13.50 @ 0.32"

  Scenario: Simple expiration
    Given Fixed exchange rate of "2.00" USD to EUR
    When Sell option "CLF 15/01/24 Put 13.50 @ 0.32" on "10/01/24"
    And Option expires "CLF 15/01/24 Put 13.50 @ 0.32" on "15/01/24"
    Then Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 0.0
    And Portfolio should have no option position "CLF 15/01/24 Put 13.50 @ 0.32"

  Scenario: Simple put assign sell stock
    Given Fixed exchange rate of "2.00" USD to EUR
    When Sell option "CLF 15/01/24 Put 13.50 @ 0.32" on "10/01/24"
    Then Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 0.0
    When Assignment "CLF 15/01/24 Put 13.50 @ 0.32"
    Then Portfolio should have a stock position for symbol "CLF" with quantity 100
    And Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 0.0
    And Portfolio should have no option position "CLF 15/01/24 Put 13.50 @ 0.32"
    When Sell stock 100 "CLF" on "20/01/24" average price: "14.50"
    Then Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 200.0

  Scenario: Partially close option position
    Given Fixed exchange rate of "2.00" USD to EUR
    When Sell option "CLF 20/01/24 Put 13.50 @ 0.32" on "10/01/24" quantity 2
    When Buy option "CLF 20/01/24 Put 13.50 @ 0.32" on "15/01/24" quantity 1
    Then Profits for fiscal year 2024 should be options profits 64.0 losses 0.0 stocks 0.0
    And Portfolio should have an option position "CLF 20/01/24 Put 13.50 @ 0.32" with quantity 1

  Scenario: Partially close option position (multiple positions)
    Given Fixed exchange rate of "2.00" USD to EUR
    When Sell option "CLF 20/01/24 Put 13.50 @ 0.32" on "10/01/24" quantity 1
    When Sell option "CLF 20/01/24 Put 13.50 @ 0.32" on "11/01/24" quantity 2
    When Buy option "CLF 20/01/24 Put 13.50 @ 0.32" on "15/01/24" quantity 2
    And Portfolio should have an option position "CLF 20/01/24 Put 13.50 @ 0.32" with quantity 1 sold on "11/01/24"

  Scenario: Reverse split
    Given Fixed exchange rate of "2.00" USD to EUR
    When Assigned put with premium "0.00" and strike price "10.0"
    And Assigned put with premium "0.00" and strike price "20.0"
    And Reverse split original quantity 100 new quantity 10
    And Sell stock quantity 5 price "400.0"
    Then Profits for fiscal year 2021 should be options profits 0.0 losses 0.0 stocks 1000.0

