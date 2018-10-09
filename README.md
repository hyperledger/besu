# Pantheon Ethereum Client &middot; [![Build Status](https://circleci.com/gh/ConsenSys/pantheon.svg?style=shield&circle-token=fe99ba1f7e99c65632a1b1ae69a821ef52ee9bc4)](https://circleci.com/gh/ConsenSys/pantheon) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/ConsenSys/pantheon/blob/master/LICENSE)

## Pantheon Users

The process for building and running Pantheon as a user is different to when developing. All user documentation is on our Wiki and some processes are different to those described in this Readme. 

### Build, Install, and Run Pantheon

Building, installing, and running Pantheon is described in the Wiki:
* [Build and Install](https://github.com/ConsenSys/pantheon/wiki/Installation)
* [Quickstart](https://github.com/ConsenSys/pantheon/wiki/Quickstart)

### Documentation 

User and reference documentation available on the Wiki includes:
* [Command Line Options](https://github.com/ConsenSys/pantheon/wiki/Pantheon-CLI-Syntax)
* [https://github.com/ConsenSys/pantheon/wiki/JSON-RPC-API](https://github.com/ConsenSys/pantheon/wiki/JSON-RPC-API)
* [Docker Quickstart Tutorial](https://github.com/ConsenSys/pantheon/wiki/Docker-Quickstart)

## Pantheon Developers

## Build Instructions

To build, clone this repo and run with `./gradlew` like so:

```
git clone --recursive https://github.com/PegaSysEng/pantheon.git
cd pantheon
./gradlew
```

After a successful build, distribution packages will be available in `build/distribution`.

## Code Style

We use Google's Java coding conventions for the project. To reformat code, run: 

```
./gradlew spotlessApply
```

Code style will be checked automatically during a build.

## Testing

All the unit tests are run as part of the build, but can be explicitly triggered with:
```
./gradlew test
```
The integration tests can be triggered with:
```
./gradlew integrationTest
```

The reference tests (described below) can be triggered with:
```
./gradlew referenceTest
```
The system tests can be triggered with:
```
./gradlew smokeTest
```

## Running Pantheon

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

### Ethereum reference tests

On top of the project proper unit tests, specific unit tests are provided to
run the Ethereum reference tests available at https://github.com/ethereum/tests
and described at http://ethereum-tests.readthedocs.io/en/latest/. Those are run
as part of the unit test suite as described above, but for debugging, it is
often convenient to run only a subset of those tests, for which a few convenience
as provided. For instance, one can run only "Frontier" general state tests with
```
./gradlew :ethereum:net.consensys.pantheon.ethereum.vm:referenceTest -Dtest.single=GeneralStateTest -Dtest.ethereum.state.eip=Frontier
```
or only the tests that match a particular pattern with something like:
```
gradle :ethereum:net.consensys.pantheon.ethereum.vm:test -Dtest.single=GeneralStateTest -Dtest.ethereum.include='^CALLCODE.*-Frontier'
```
Please see the comment on the `test` target in the top level `build.gradle`
file for more details.

### Logging

This project employs the logging utility [Apache Log4j](https://logging.apache.org/log4j/2.x/),
accordingly levels of detail can be specified as follows:

```
ALL:	All levels including custom levels.
DEBUG:	Designates fine-grained informational events that are most useful to debug an application.
ERROR:	Designates error events that might still allow the application to continue running.
FATAL:	Designates very severe error events that will presumably lead the application to abort.
INFO:	Designates informational messages that highlight the progress of the application at coarse-grained level.
OFF:	The highest possible rank and is intended to turn off logging.
TRACE:	Designates finer-grained informational events than the DEBUG.
WARN:	Designates potentially harmful situations.
```

One mechanism of globally effecting the log output of a running client is though modification the file
`/pantheon/src/main/resources/log4j2.xml`, where it can be specified under the `<Property name="root.log.level">`.
As such, corresponding instances of information logs throughout the codebase, e.g. `log.fatal("Fatal Message!");`,
will be rendered to the console while the client is in use.

## Contribution

Welcome to the Pantheon Ethereum project repo. If you would like to help contribute
code to the project, please fork, commit and send us a pull request. 

Please read the [Contribution guidelines](docs/CONTRIBUTORS.md) for this project.
