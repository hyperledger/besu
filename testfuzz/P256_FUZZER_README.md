# P256Verify Precompiled Contract Fuzzer

This directory contains a comprehensive fuzz testing framework for the P256VerifyPrecompiledContract. The fuzzer is designed to find edge cases, boundary conditions, and potential discrepancies between native (BoringSSL) and Java implementations.

## Overview

The P256Verify fuzzer tests the EIP-7951 P256 signature verification precompiled contract with a comprehensive test matrix:

### Test Matrix (All Combinations)
1. **Java-generated signature → Java verification**
2. **Java-generated signature → Native verification**
3. **Native-generated signature → Java verification**
4. **Native-generated signature → Native verification**

### Testing Strategies
1. **Signature Generation**: Creates signatures using both Java and Native implementations
2. **Mutation Testing**: Applies various mutations to valid signatures
3. **Boundary Conditions**: Tests edge values for r, s, qx, qy parameters
4. **Curve-Specific Attacks**: Tests special curve points, point at infinity, and invalid curve points
5. **Cross-Validation**: Ensures all implementation combinations produce consistent results
6. **BouncyCastle Bypass**: Tests specific edge cases that might bypass validation

## Key Components

### P256VerifyFuzzTarget.java
The main fuzz target implementing multiple mutation strategies:

- `NO_MUTATION`: Use valid signatures as-is for baseline testing
- `BIT_FLIP`: Random bit flips in valid signatures
- `BOUNDARY_VALUES`: Tests r,s ∈ {0, 1, n-1, n} and qx,qy ∈ {0, p-1, p}
- `ARITHMETIC_MUTATION`: Add, subtract, multiply, XOR operations on signature components
- `CURVE_ATTACKS`: Point at infinity, invalid curve points, generator point tests
- `SIGNATURE_MALLEABILITY`: Tests malleable signature variants (s vs n-s)
- `POINT_MANIPULATION`: Coordinate negation, swapping, and manipulation
- `BOUNCYCASTLE_BYPASS`: Tests edge cases specific to BouncyCastle validation

### P256VerifySubCommand.java
Command-line interface for running the fuzzer with various options.

To see all available command-line options:
```bash
# Build and run the fuzzer binary directly
./gradlew :testfuzz:installDist
./testfuzz/build/install/BesuFuzz/bin/BesuFuzz p256verify --help
```

### P256VerifyCorpusGenerator.java
Generates initial seed corpus with known interesting test cases to improve fuzzing efficiency.

## Usage

### Basic Fuzzing

The fuzzer can be run using a pre-configured Gradle task or directly with custom parameters:

```bash
# Run the fuzzer with default settings
./gradlew fuzzP256Verify
```

### Advanced Options

For custom parameters, use the direct execution method:

```bash
./gradlew :testfuzz:installDist
./testfuzz/build/install/BesuFuzz/bin/BesuFuzz p256verify --corpus-dir=/path/to/corpus --timeout-seconds=3600
```

### Command Line Options

- `--corpus-dir`: Directory containing initial test cases (default: `build/generated/p256-corpus`)
- `--new-corpus-dir`: Directory to save newly discovered interesting inputs
- `--guidance-regexp`: Regex for coverage guidance (default: focuses on EVM and crypto classes)
- `--timeout-seconds`: Maximum time to run (0 = unlimited)
- `--enable-native`: Enable native implementation comparison (default: true)

## Expected Behavior

### Successful Fuzzing
The fuzzer will continuously generate test inputs and report:
- Total executions performed
- Code coverage achieved
- Execution rate (executions/second)
- Memory usage

Example output:
```
#1000 NEW     cov: 145 corp: 23 exec/s: 89 rss: 128 MB native=true mem_delta=+2MB
#2000 PULSE   cov: 145 corp: 23 exec/s: 92 rss: 131 MB native=true mem_delta=+1MB
```

### Crash Detection
If the fuzzer finds a discrepancy between native and Java implementations, it will:
1. Print detailed error information
2. Save the crashing input to a file named `crash-<hash>`
3. Exit with status code 1

Example crash:
```
DISCREPANCY FOUND! Strategy: BOUNDARY_VALUES, Native: 0x0000000000000000000000000000000000000000000000000000000000000000, Default: 0x0000000000000000000000000000000000000000000000000000000000000001, Input: 0x1234...
```

## Interpretation of Results

### Valid Discrepancies (Should Be Investigated)
- Different return values between native and Java implementations for the same input
- Validation accepting obviously invalid inputs (point at infinity with valid result)
- Crashes or exceptions in either implementation

### Expected Behavior
- Both implementations returning `INVALID` (empty bytes) for malformed inputs
- Both implementations returning `VALID` (32 bytes of 0x00...01) for valid signatures
- Consistent handling of boundary conditions

## Integration with Development Workflow

### Continuous Integration
The fuzzer can be integrated into CI pipelines:

```bash
# Run fuzzer for 10 minutes in CI
timeout 600 ./gradlew fuzzP256Verify || echo "Fuzzer found issues"
```

## Architecture Details

### Code Structure
The fuzzer uses a clean separation of concerns:

1. **Signature Generation**:
   - `javaSignatureAlgorithm`: SECP256R1 instance configured for Java implementation
   - `nativeSignatureAlgorithm`: SECP256R1 instance configured for Native implementation
   - Each generates signatures independently

2. **Verification Testing**:
   - `verificationContract`: Single P256VerifyPrecompiledContract instance
   - Verification implementation controlled via static flags (BoringSSL enable/disable)
   - Tests both Java and Native verification for each signature

3. **Test Case Management**:
   - `SignatureTestCase`: Encapsulates signature data, generation method, and mutation strategy
   - Clean tracking of which implementation generated each signature

### Input Format (160 bytes total)
```
Offset | Size | Description
-------|------|------------
0      | 32   | Message hash
32     | 32   | Signature r component
64     | 32   | Signature s component
96     | 32   | Public key x coordinate (qx)
128    | 32   | Public key y coordinate (qy)
```

### Validation Logic Tested
1. **Range Checks**: r, s ∈ (0, n) and qx, qy ∈ [0, p)
2. **Point Validation**: Public key must be valid curve point
3. **Signature Verification**: ECDSA verification using secp256r1 curve
4. **Edge Cases**: Point at infinity, malleability, boundary values
5. **Implementation Consistency**: All combinations must produce identical results

## Troubleshooting

### Point Not On Curve Errors
```
Error: P256VERIFY verification failed: Could not create structure to store public key: : error:0800006B:elliptic curve routines::point is not on curve
Solution: This is EXPECTED behavior when fuzzing with synthetic/mutated inputs. The native BoringSSL
         implementation validates curve points and logs errors for invalid points. These are not bugs
         unless Java and Native implementations disagree on the verification result.
```

## Files Generated

- `corpus/`: Initial seed test cases
- `new-corpus/`: Newly discovered interesting inputs
- `crash-<hash>`: Inputs that caused discrepancies

## Performance Considerations

- **With Coverage Guidance**: ~100 executions/second
- **Memory Usage**: 128-512 MB typical, may grow with large corpus

This fuzzer provides comprehensive testing of the P256Verify precompiled contract and should help identify any issues with signature validation logic, implementation differences, or edge case handling.
