# BesuFuzz

BesuFuzz is where all the besu guided fuzzing tools live.

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