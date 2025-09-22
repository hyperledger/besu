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

public class ModOperationBenchmark extends BinaryOperationBenchmark {
  // Benches for a % b

  // Define available scenarios
  public enum Case {
    DIV1_MOD1(1, 1),
    DIV2_MOD1(2, 1),
    DIV2_MOD2(2, 2),
    DIV4_MOD1(4, 1),
    DIV4_MOD2(4, 2),
    DIV4_MOD4(4, 4),
    DIV6_MOD1(6, 1),
    DIV6_MOD2(6, 2),
    DIV6_MOD4(6, 4),
    DIV6_MOD6(6, 6),
    DIV8_MOD1(8, 1),
    DIV8_MOD2(8, 2),
    DIV8_MOD4(8, 4),
    DIV8_MOD6(8, 6),
    DIV8_MOD8(8, 8),
    MOD4_LARGER_THAN_DIV2(2, 4),
    MOD8_LARGER_THAN_DIV6(6, 8),
    ZERO_MOD_DIV4(4, 0);

    final int divSize;
    final int modSize;

    Case(final int divSize, final int modSize) {
      this.divSize = divSize;
      this.modSize = modSize;
    }
  }

  @Param({
    "DIV1_MOD1",
    "DIV2_MOD1",
    "DIV2_MOD2",
    "DIV4_MOD1",
    "DIV4_MOD2",
    "DIV4_MOD4",
    "DIV6_MOD1",
    "DIV6_MOD2",
    "DIV6_MOD4",
    "DIV6_MOD6",
    "DIV8_MOD1",
    "DIV8_MOD2",
    "DIV8_MOD4",
    "DIV8_MOD6",
    "DIV8_MOD8",
    "MOD4_LARGER_THAN_DIV2",
    "MOD8_LARGER_THAN_DIV6",
    "ZERO_MOD_DIV4"
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
