# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Coding guidelines

## Modularity
The code in this package is for test support for easy setup of test cases. The methods are meant to be composed into
more complex test setups and thus should be kept small and modular.

## Builder and mother object pattern
For definition of test data the code makes heavy use of the builder and mother object pattern.
The base of a motherobject is the randomXY method that just defines an arbitrary object like a transaction
with randomly assigned required values. The object are meant to be customized by the test to specify all values that are
relevant for the test. Also more specific factory methods can be defined. E.g. optionStoTx or assignmentStockTrade