# Running Developer Builds

Build and run Besu with default options using:

```
./gradlew installDist
```

By default this stores all persistent data in `build/install/besu`.

To set custom CLI arguments for the Besu execution:

```sh
cd build/install/besu

./bin/besu --discovery-enabled=false --data-path=/tmp/besutmp
```
