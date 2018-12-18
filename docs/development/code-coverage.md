# Code coverage

We use the jacoco test coverage plugin, which will generate coverage data whenever tests are run.

To run the report:
```
./gradlew test jacocoTestReport
```

The report will be available at `build/reports/jacoco/test/html/index.html`