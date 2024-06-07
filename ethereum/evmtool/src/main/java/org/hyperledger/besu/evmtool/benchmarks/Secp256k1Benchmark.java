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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.besu.crypto.Hash.keccak256;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SECPSignature;

import java.io.PrintStream;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** Benchmark secp256k1 public key extraction */
public class Secp256k1Benchmark extends BenchmarkExecutor {

  /** secp256k1 benchmark using default math warmup and iterations */
  public Secp256k1Benchmark() {
    super(MATH_WARMUP, MATH_ITERATIONS);
  }

  @Override
  public void runBenchmark(
      final PrintStream output, final Boolean attemptNative, final String fork) {
    final SECP256K1 signatureAlgorithm = new SECP256K1();
    if (attemptNative != null && (!attemptNative || !signatureAlgorithm.maybeEnableNative())) {
      signatureAlgorithm.disableNative();
    }
    output.println(signatureAlgorithm.isNative() ? "Native secp256k1" : "Java secp256k1");

    final SECPPrivateKey privateKey =
        signatureAlgorithm.createPrivateKey(
            new BigInteger("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4", 16));
    final KeyPair keyPair = signatureAlgorithm.createKeyPair(privateKey);

    final Bytes data = Bytes.wrap("This is an example of a signed message.".getBytes(UTF_8));
    final Bytes32 dataHash = keccak256(data);
    final SECPSignature signature = signatureAlgorithm.sign(dataHash, keyPair);
    for (int i = 0; i < warmup; i++) {
      signatureAlgorithm.recoverPublicKeyFromSignature(dataHash, signature);
    }
    final Stopwatch timer = Stopwatch.createStarted();
    for (int i = 0; i < iterations; i++) {
      signatureAlgorithm.recoverPublicKeyFromSignature(dataHash, signature);
    }
    timer.stop();

    final double elapsed = timer.elapsed(TimeUnit.NANOSECONDS) / 1.0e9D;
    final double perCall = elapsed / MATH_ITERATIONS;

    output.printf("secp256k1 signature recovery for %,.1f µs%n", perCall * 1_000_000);
  }
}
