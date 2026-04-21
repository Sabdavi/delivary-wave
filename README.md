# delivery-wave

Minimal Maven + Kotlin project targeting Java 21.

## Project Layout

- `src/main/kotlin` - application source code
- `src/test/kotlin` - unit tests
- `src/main/resources` - application resources

## Quick Start

```bash
mvn clean test
mvn -q exec:java -Dexec.mainClass=com.deliverywave.AppKt
```

If you do not have Java 21 selected yet, set it before building.

