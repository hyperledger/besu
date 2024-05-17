Fix for Flaky Test in TimestampValidationRuleTest

This pull request addresses the flakiness observed in the  by using a fixed reference time instead of relying on the current system time. This change ensures that the test outcome is consistent across different test runs.
