/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.vm.operations;

import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.ModOperation;
import org.hyperledger.besu.evm.operation.Operation;

import java.math.BigInteger;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;

public class ModOperationBySizesBenchmark extends BinaryOperationBenchmark {
  // Benches for a % b

  // Define available scenarios
  public enum Case {
    MOD_32_32(1, 1),
    MOD_64_32(2, 1),
    MOD_64_64(2, 2),
    MOD_128_32(4, 1),
    MOD_128_64(4, 2),
    MOD_128_128(4, 4),
    MOD_192_32(6, 1),
    MOD_192_64(6, 2),
    MOD_192_128(6, 4),
    MOD_192_192(6, 6),
    MOD_256_32(8, 1),
    MOD_256_64(8, 2),
    MOD_256_128(8, 4),
    MOD_256_192(8, 6),
    MOD_256_256(8, 8),
    LARGER_MOD_64_128(2, 4),
    LARGER_MOD_192_256(6, 8),
    ZERO_MOD_128_0(4, 0);

    final int divSize;
    final int modSize;

    Case(final int divSize, final int modSize) {
      this.divSize = divSize;
      this.modSize = modSize;
    }
  }

  @Param({
    "MOD_32_32",
    "MOD_64_32",
    "MOD_64_64",
    "MOD_128_32",
    "MOD_128_64",
    "MOD_128_128",
    "MOD_192_32",
    "MOD_192_64",
    "MOD_192_128",
    "MOD_192_192",
    "MOD_256_32",
    "MOD_256_64",
    "MOD_256_128",
    "MOD_256_192",
    "MOD_256_256",
    "LARGER_MOD_64_128",
    "LARGER_MOD_192_256",
    "ZERO_MOD_128_0"
  })
  private String caseName;

  @Setup(Level.Iteration)
  @Override
  public void setUp() {
    frame = BenchmarkHelper.createMessageCallFrame();

    Case scenario = Case.valueOf(caseName);
    aPool = new Bytes[SAMPLE_SIZE];
    bPool = new Bytes[SAMPLE_SIZE];

    final ThreadLocalRandom random = ThreadLocalRandom.current();

    for (int i = 0; i < SAMPLE_SIZE; i++) {
      final byte[] a = new byte[scenario.divSize * 4];
      final byte[] b = new byte[scenario.modSize * 4];
      random.nextBytes(a);
      random.nextBytes(b);

      if ((scenario.divSize != scenario.modSize)) {
        aPool[i] = Bytes.wrap(a);
        bPool[i] = Bytes.wrap(b);
      } else {
        BigInteger aInt = new BigInteger(a);
        BigInteger bInt = new BigInteger(b);
        if ((aInt.compareTo(bInt) < 0)) {
          aPool[i] = Bytes.wrap(b);
          bPool[i] = Bytes.wrap(a);
        } else {
          aPool[i] = Bytes.wrap(a);
          bPool[i] = Bytes.wrap(b);
        }
      }
    }
    index = 0;
  }

  @Override
  protected Operation.OperationResult invoke(final MessageFrame frame) {
    return ModOperation.staticOperation(frame);
  }
}
