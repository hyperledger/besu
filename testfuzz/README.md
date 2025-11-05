# BesuFuzz

BesuFuzz is where all the besu guided fuzzing tools live.

## eof-container

Performs differential fuzzing between Ethereum clients based on
the [txparse eofparse](https://github.com/holiman/txparse/blob/main/README.md#eof-parser-eofparse)
format. Note that only the initial `OK` and `err` values are used to determine if
there is a difference.

### Prototypical CLI Usage:

```shell
BesuFuzz eof-container \
  --tests-dir=~/git/ethereum/tests/EOFTests \
  --client=evm1=evmone-eofparse \
  --client=revm=revme bytecode
```

### Prototypical Gradle usage:

```shell
./gradlew fuzzEvmone fuzzReth
```

There are pre-written Gradle targets for `fuzzEthereumJS`, `fuzzEvmone`,
`fuzzGeth`, `fuzzNethermind`, and `fuzzReth`. Besu is always a fuzzing target.
The `fuzzAll` target will fuzz all clients.

## p256verify

Performs differential fuzzing of P256 signature verification between Java and Native (BoringSSL)
implementations of the P256Verify precompiled contract. Tests all combinations of signature
generation and verification to ensure consistency across implementations.

### Prototypical CLI Usage:

```shell
cd testfuzz/build/install/BesuFuzz
./bin/BesuFuzz p256verify \
  --corpus-dir=build/generated/p256-corpus \
  --timeout-seconds=3600
```

### Prototypical Gradle usage:

```shell
# Quick test with default settings
./gradlew fuzzP256Verify
```

The P256 fuzzer tests multiple mutation strategies including bit flips, boundary values,
curve attacks, and signature malleability. See `P256_FUZZER_README.md` for detailed documentation.