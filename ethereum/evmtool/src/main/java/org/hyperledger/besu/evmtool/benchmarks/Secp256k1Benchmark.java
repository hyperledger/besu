/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.evmtool.benchmarks;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.gascalculator.IstanbulGasCalculator;
import org.hyperledger.besu.evm.precompile.ECRECPrecompiledContract;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;

/** Benchmark secp256k1 public key extraction */
public class Secp256k1Benchmark extends BenchmarkExecutor {

  /**
   * The constructor. Use default math based warmup and interations.
   *
   * @param output where to write the stats.
   * @param benchmarkConfig benchmark configurations.
   */
  public Secp256k1Benchmark(final PrintStream output, final BenchmarkConfig benchmarkConfig) {
    super(MATH_WARMUP, MATH_ITERATIONS, output, benchmarkConfig);
  }

  @Override
  public void runBenchmark(final Boolean attemptNative, final String fork) {
    final Map<String, Bytes> testCases = new LinkedHashMap<>();
    testCases.put(
        "secp256k1",
        Bytes.fromHexString(
            "0x0049872459827432342344987245982743234234498724598274323423429943000000000000000000000000000000000000000000000000000000000000001be8359c341771db7f9ea3a662a1741d27775ce277961470028e054ed3285aab8e31f63eaac35c4e6178abbc2a1073040ac9bbb0b67f2bc89a2e9593ba9abe8c53"));

    final SECP256K1 signatureAlgorithm = new SECP256K1();
    if (attemptNative != null && (!attemptNative || !signatureAlgorithm.maybeEnableNative())) {
      signatureAlgorithm.disableNative();
    }
    output.println(signatureAlgorithm.isNative() ? "Native secp256k1" : "Java secp256k1");
    final ECRECPrecompiledContract contract =
        new ECRECPrecompiledContract(new IstanbulGasCalculator(), signatureAlgorithm);

    precompile(testCases, contract, EvmSpecVersion.fromName(fork));
  }

  @Override
  public boolean isPrecompile() {
    return true;
  }
}
