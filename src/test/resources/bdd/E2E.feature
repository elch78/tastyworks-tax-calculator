Feature: Simple Assignment
  Scenario: Put option is assigned
    When Sell option "CLF 11/01/24 Put 13.50 @ 0.32"
    And Assignment "CLF 11/01/24 Put 13.50 @ 0.32"
    Then Portfolio should have a stock position for symbol "CLF" with quantity 100