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
 */
package org.hyperledger.besu.evm.precompile;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.fluent.EvmSpec;
import org.hyperledger.besu.evm.fluent.SimpleBlockValues;
import org.hyperledger.besu.evm.fluent.SimpleWorld;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.OsakaGasCalculator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import org.apache.tuweni.bytes.Bytes;

@SuppressWarnings("UnusedMethod")
public class Benchmarks {
  static final Random random = new Random();
  static final long GAS_PER_SECOND_STANDARD = 100_000_000L;

  static final int MATH_WARMUP = 15_000;
  static final int MATH_ITERATIONS = 1_000;
  static final MessageFrame fakeFrame =
      MessageFrame.builder()
          .type(MessageFrame.Type.CONTRACT_CREATION)
          .contract(Address.ZERO)
          .inputData(Bytes.EMPTY)
          .sender(Address.ZERO)
          .value(Wei.ZERO)
          .apparentValue(Wei.ZERO)
          .code(CodeV0.EMPTY_CODE)
          .completer(__ -> {})
          .address(Address.ZERO)
          .blockHashLookup((__, ___) -> null)
          .blockValues(new SimpleBlockValues())
          .gasPrice(Wei.ZERO)
          .miningBeneficiary(Address.ZERO)
          .originator(Address.ZERO)
          .initialGas(100_000L)
          .worldUpdater(new SimpleWorld())
          .build();

  private static void benchSecp256r1Verify() {
    final Map<String, Bytes> testCases = new LinkedHashMap<>();
    testCases.put(
        "wycheproof/ecdsa_secp256r1_sha256_p1363_test.json EcdsaP1363Verify SHA-256 #1: signature malleability",
        Bytes.fromHexString(
            "bb5a52f42f9c9261ed4361f59422a1e30036e7c32b270c8807a419feca6050232ba3a8be6b94d5ec80a6d9d1190a436effe50d85a1eee859b8cc6af9bd5c2e184cd60b855d442f5b3c7b11eb6c4e0ae7525fe710fab9aa7c77a67f79e6fadd762927b10512bae3eddcfe467828128bad2903269919f7086069c8c4df6c732838c7787964eaac00e5921fb1498a60f4606766b3d9685001558d1a974e7341513e"));
    testCases.put(
        "wycheproof/ecdsa_secp256r1_sha256_p1363_test.json EcdsaP1363Verify SHA-256 #3: Modified r or s, e.g. by adding or subtracting the order of the group",
        Bytes.fromHexString(
            "0xbb5a52f42f9c9261ed4361f59422a1e30036e7c32b270c8807a419feca605023d45c5740946b2a147f59262ee6f5bc90bd01ed280528b62b3aed5fc93f06f739b329f479a2bbd0a5c384ee1493b1f5186a87139cac5df4087c134b49156847db2927b10512bae3eddcfe467828128bad2903269919f7086069c8c4df6c732838c7787964eaac00e5921fb1498a60f4606766b3d9685001558d1a974e7341513e"));
    testCases.put(
        "wycheproof/ecdsa_secp256r1_sha256_p1363_test.json EcdsaP1363Verify SHA-256 #5: Modified r or s, e.g. by adding or subtracting the order of the group",
        Bytes.fromHexString(
            "0xbb5a52f42f9c9261ed4361f59422a1e30036e7c32b270c8807a419feca605023d45c5741946b2a137f59262ee6f5bc91001af27a5e1117a64733950642a3d1e8b329f479a2bbd0a5c384ee1493b1f5186a87139cac5df4087c134b49156847db2927b10512bae3eddcfe467828128bad2903269919f7086069c8c4df6c732838c7787964eaac00e5921fb1498a60f4606766b3d9685001558d1a974e7341513e"));
    testCases.put(
        "canonical case",
        Bytes.fromHexString(
            "0xbb5a52f42f9c9261ed4361f59422a1e30036e7c32b270c8807a419feca605023555555550000000055555555555555553ef7a8e48d07df81a693439654210c7044a5ad0ad0636d9f12bc9e0a6bdd5e1cbcb012ea7bf091fcec15b0c43202d52ed8adc00023a8edc02576e2b63e3e30621a471e2b2320620187bf067a1ac1ff3233e2b50ec09807accb36131fff95ed12a09a86b4ea9690aa32861576ba2362e1"));
    final P256VerifyPrecompiledContract contract =
        new P256VerifyPrecompiledContract(new OsakaGasCalculator());

    for (final Map.Entry<String, Bytes> testCase : testCases.entrySet()) {
      final long timePerCallInNs = runBenchmark(testCase.getValue(), contract);
      long gasRequirement = contract.gasRequirement(testCase.getValue());
      logPerformance("Secp256r1 signature verification", gasRequirement, timePerCallInNs);
    }
  }

  private static void benchKeccak256() {
    fakeFrame.expandMemory(0, 1024);
    var gasCalculator = EvmSpec.evmSpec().getEvm().getGasCalculator();

    for (int len = 0; len <= 512; len += 8) {
      final byte[] data = new byte[len];
      random.nextBytes(data);
      final Bytes bytes = Bytes.wrap(data);
      for (int i = 0; i < MATH_WARMUP; i++) {
        Hash.keccak256(bytes);
      }
      final Stopwatch timer = Stopwatch.createStarted();
      for (int i = 0; i < MATH_ITERATIONS; i++) {
        Hash.keccak256(bytes);
      }
      timer.stop();

      final long elapsed = timer.elapsed(TimeUnit.NANOSECONDS);
      final long timePerCallInNs = elapsed / MATH_ITERATIONS;
      long gasRequirement = gasCalculator.keccak256OperationGasCost(fakeFrame, 0, len);
      logPerformance(String.format("Keccak256 %,d bytes", len), gasRequirement, timePerCallInNs);
    }
  }

  private static long runBenchmark(final Bytes arg, final PrecompiledContract contract) {
    if (contract.computePrecompile(arg, fakeFrame).output() == null) {
      throw new RuntimeException("Input is Invalid");
    }

    for (int i = 0; i < MATH_WARMUP; i++) {
      contract.computePrecompile(arg, fakeFrame);
    }
    final Stopwatch timer = Stopwatch.createStarted();
    for (int i = 0; i < MATH_ITERATIONS; i++) {
      contract.computePrecompile(arg, fakeFrame);
    }
    timer.stop();

    final long elapsed = timer.elapsed(TimeUnit.NANOSECONDS);
    final long perCallInNs = elapsed / MATH_ITERATIONS;
    return perCallInNs;
  }

  public static void logPerformance(final String label, final long gasCost, final long timeNs) {
    double derivedGas = (timeNs / 1_000_000_000.0) * GAS_PER_SECOND_STANDARD;
    double mgps = (gasCost * 1000.0) / timeNs;

    System.out.printf(
        "%-30s | %,7d gas cost | %,7.0f calculated gas for execution time per call %,9d ns | %7.2f MGps%n",
        label, gasCost, derivedGas, timeNs, mgps);
  }

  public static void logHeader() {
    long executionTimeExampleNs = 247_914L;
    long gasPerSecond = GAS_PER_SECOND_STANDARD;
    long derivedGas = (executionTimeExampleNs * gasPerSecond) / 1_000_000_000L;

    System.out.println(
        "**** Calculate the derived gas from execution time with a target of 100 mgas/s *****");
    System.out.println(
        "*                                                                                  *");
    System.out.println(
        "*   If "
            + String.format("%,d", executionTimeExampleNs)
            + " ns is the execution time of the precompile call, so this is how     *");
    System.out.println(
        "*                the derived gas is calculated                                     *");
    System.out.println(
        "*                                                                                  *");
    System.out.println(
        "*   "
            + String.format("%,d", gasPerSecond)
            + " gas    -------> 1 second (1_000_000_000 ns)                        *");
    System.out.println(
        "*   x           gas    -------> "
            + String.format("%,d", executionTimeExampleNs)
            + " ns                                         *");
    System.out.println(
        "*                                                                                  *");
    System.out.println(
        "*\tx = ("
            + String.format("%,d", executionTimeExampleNs)
            + " * "
            + String.format("%,d", gasPerSecond)
            + ") / 1_000_000_000 = "
            + String.format("%,d", derivedGas)
            + " gas"
            + "                       *");
    System.out.println(
        "************************************************************************************");
    System.out.println();
    System.out.println("** System Properties **");
    System.out.println();
    System.getProperties().forEach((k, v) -> System.out.println(k + " = " + v));
  }

  public static void main(final String[] args) {
    logHeader();
    benchKeccak256();
    benchSecp256r1Verify();
  }
}
