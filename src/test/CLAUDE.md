# Testing strategy

The project uses Cucumber for comprehensive BDD scenarios covering:

Prefer acceptance test driven design. I.e. write an acceptance test first, then make it green.

Only use unit tests where it makes sense e.g. to test many edge cases or exceptions.

Tests should use easy to calculate numbers (e.g. 10 vs 17) while trying to have them unambiguous e.g. profit and price
should be different.