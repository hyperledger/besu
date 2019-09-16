# Checkout code and build it

## Prerequisites

* Java 11+

## Quick start

```sh
git clone --recursive https://github.com/hyperledger/besu
cd besu
./gradlew build  
./gradlew integrationTest
```

## Checkout source code

```
git clone --recursive git@github.com:hyperledger/besu.git
```
OR
```
git clone --recursive https://github.com/hyperledger/besu
```

## See what tasks are available
To see all of the gradle tasks that are available:
```
cd besu
./gradlew tasks  
```


## Build from source
After you have checked out the code, this will build the distribution binaries.
```
cd besu
./gradlew build  
```

## Run tests
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
The acceptance tests can be triggered with:
```
./gradlew acceptanceTest
```

### Ethereum reference tests

On top of the project proper unit tests, specific unit tests are provided to
run the Ethereum reference tests available at https://github.com/ethereum/tests
and described at http://ethereum-tests.readthedocs.io/en/latest/. Those are run
as part of the unit test suite as described above, but for debugging, it is
often convenient to run only a subset of those tests, for which a few convenience
as provided. For instance, one can run only "Frontier" general state tests with
```
./gradlew :ethereum:org.hyperledger.besu.ethereum.vm:referenceTest -Dtest.single=GeneralStateTest -Dtest.ethereum.state.eip=Frontier
```
or only the tests that match a particular pattern with something like:
```
gradle :ethereum:org.hyperledger.besu.ethereum.vm:test -Dtest.single=GeneralStateTest -Dtest.ethereum.include='^CALLCODE.*-Frontier'
```
Please see the comment on the `test` target in the top level `build.gradle`
file for more details.

