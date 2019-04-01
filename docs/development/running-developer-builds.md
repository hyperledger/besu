# Running Developer Builds

Build and run Pantheon with default options using:

```
./gradlew installDist
```

By default this stores all persistent data in `build/install/pantheon`.

To set custom CLI arguments for the Pantheon execution:

```sh
cd build/install/pantheon

./bin/pantheon --discovery-enabled=false --data-path=/tmp/pantheontmp
```
