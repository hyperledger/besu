/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.mainnet.precompiles;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.ethereum.mainnet.IstanbulGasCalculator;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import org.apache.tuweni.bytes.Bytes;

public class Benchmarks {

  static final Random random = new Random();

  static final long GAS_PER_SECOND_STANDARD = 35_000_000L;

  static final int HASH_WARMUP = 1_000_000;
  static final int HASH_ITERATIONS = 10_000;

  static final int MATH_WARMUP = 10_000;
  static final int MATH_ITERATIONS = 1_000;

  public static void benchSha256() {
    final SHA256PrecompiledContract contract =
        new SHA256PrecompiledContract(new IstanbulGasCalculator());
    final byte[] warmupData = new byte[240];
    final Bytes warmupBytes = Bytes.wrap(warmupData);
    for (int i = 0; i < HASH_WARMUP; i++) {
      contract.compute(warmupBytes, null);
    }
    for (int len = 0; len <= 256; len += 8) {
      final byte[] data = new byte[len];
      random.nextBytes(data);
      final Bytes bytes = Bytes.wrap(data);
      final Stopwatch timer = Stopwatch.createStarted();
      for (int i = 0; i < HASH_ITERATIONS; i++) {
        contract.compute(bytes, null);
      }
      timer.stop();

      final double elapsed = timer.elapsed(TimeUnit.NANOSECONDS) / 1.0e9D;
      final double perCall = elapsed / HASH_ITERATIONS;
      final double gasSpent = perCall * GAS_PER_SECOND_STANDARD;

      System.out.printf("Hashed %d bytes for %d gas.%n", len, (int) gasSpent);
    }
  }

  private static void benchKeccak256() {
    final byte[] warmupData = new byte[240];
    final Bytes warmupBytes = Bytes.wrap(warmupData);
    for (int i = 0; i < HASH_WARMUP; i++) {
      Hash.keccak256(warmupBytes);
    }
    for (int len = 0; len <= 512; len += 8) {
      final byte[] data = new byte[len];
      random.nextBytes(data);
      final Bytes bytes = Bytes.wrap(data);
      final Stopwatch timer = Stopwatch.createStarted();
      for (int i = 0; i < HASH_ITERATIONS; i++) {
        Hash.keccak256(bytes);
      }
      timer.stop();

      final double elapsed = timer.elapsed(TimeUnit.NANOSECONDS) / 1.0e9D;
      final double perCall = elapsed / HASH_ITERATIONS;
      final double gasSpent = perCall * GAS_PER_SECOND_STANDARD;

      System.out.printf("Hashed %d bytes for %d gas.%n", len, (int) gasSpent);
    }
  }

  private static void benchRipeMD() {
    final RIPEMD160PrecompiledContract contract =
        new RIPEMD160PrecompiledContract(new IstanbulGasCalculator());
    final byte[] warmupData = new byte[240];
    final Bytes warmupBytes = Bytes.wrap(warmupData);
    for (int i = 0; i < HASH_WARMUP; i++) {
      contract.compute(warmupBytes, null);
    }
    for (int len = 0; len <= 256; len += 8) {
      final byte[] data = new byte[len];
      random.nextBytes(data);
      final Bytes bytes = Bytes.wrap(data);
      final Stopwatch timer = Stopwatch.createStarted();
      for (int i = 0; i < HASH_ITERATIONS; i++) {
        contract.compute(bytes, null);
      }
      timer.stop();

      final double elapsed = timer.elapsed(TimeUnit.NANOSECONDS) / 1.0e9D;
      final double perCall = elapsed / HASH_ITERATIONS;
      final double gasSpent = perCall * GAS_PER_SECOND_STANDARD;

      System.out.printf("Hashed %d bytes for %d gas.%n", len, (int) gasSpent);
    }
  }

  private static void benchBNADD() {
    final Bytes g1Point0 =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x17c139df0efee0f766bc0204762b774362e4ded88953a39ce849a8a7fa163fa9"),
            Bytes.fromHexString(
                "0x01e0559bacb160664764a357af8a9fe70baa9258e0b959273ffc5718c6d4cc7c"));

    final Bytes g1Point1 =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x17c139df0efee0f766bc0204762b774362e4ded88953a39ce849a8a7fa163fa9"),
            Bytes.fromHexString(
                "0x2e83f8d734803fc370eba25ed1f6b8768bd6d83887b87165fc2434fe11a830cb"));
    final Bytes arg = Bytes.concatenate(g1Point0, g1Point1);

    final AltBN128AddPrecompiledContract contract =
        AltBN128AddPrecompiledContract.istanbul(new IstanbulGasCalculator());
    if (contract.compute(arg, null) == null) {
      throw new RuntimeException("Test Points are Invalid");
    }

    for (int i = 0; i < MATH_WARMUP; i++) {
      contract.compute(arg, null);
    }
    final Stopwatch timer = Stopwatch.createStarted();
    for (int i = 0; i < MATH_ITERATIONS; i++) {
      contract.compute(arg, null);
    }
    timer.stop();

    final double elapsed = timer.elapsed(TimeUnit.NANOSECONDS) / 1.0e9D;
    final double perCall = elapsed / MATH_ITERATIONS;
    final double gasSpent = perCall * GAS_PER_SECOND_STANDARD;

    System.out.printf("BNADD for %d gas.%n", (int) gasSpent);
  }

  private static void benchBNMUL() {
    final Bytes g1Point1 =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"),
            Bytes.fromHexString(
                "0x30644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd45"));
    final Bytes scalar =
        Bytes.fromHexString("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
    final Bytes arg = Bytes.concatenate(g1Point1, scalar);

    final AltBN128MulPrecompiledContract contract =
        AltBN128MulPrecompiledContract.istanbul(new IstanbulGasCalculator());
    if (contract.compute(arg, null) == null) {
      throw new RuntimeException("Test Points are Invalid");
    }

    for (int i = 0; i < MATH_WARMUP; i++) {
      contract.compute(arg, null);
    }
    final Stopwatch timer = Stopwatch.createStarted();
    for (int i = 0; i < MATH_ITERATIONS; i++) {
      contract.compute(arg, null);
    }
    timer.stop();

    final double elapsed = timer.elapsed(TimeUnit.NANOSECONDS) / 1.0e9D;
    final double perCall = elapsed / MATH_ITERATIONS;
    final double gasSpent = perCall * GAS_PER_SECOND_STANDARD;

    System.out.printf("BNMUL for %d gas.%n", (int) gasSpent);
  }

  public static void main(final String[] args) {
    System.out.println("SHA256");
    benchSha256();
    System.out.println("Keccak256");
    benchKeccak256();
    System.out.println("RIPEMD256");
    benchRipeMD();
    System.out.println("BNADD");
    benchBNADD();
    System.out.println("BNMUL");
    benchBNMUL();
  }
}
