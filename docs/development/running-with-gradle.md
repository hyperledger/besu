# Running Pantheon

You can build and run Pantheon with default options via:

```
./gradlew run
```

By default this stores all persistent data in `build/pantheon`.

If you want to set custom CLI arguments for the Pantheon execution, you can use the property `pantheon.run.args` like e.g.:

```sh
./gradlew run -Ppantheon.run.args="--discovery=false --home=/tmp/pantheontmp"
```

which will pass `--discovery=false` and `--home=/tmp/pantheontmp` to the invocation.
