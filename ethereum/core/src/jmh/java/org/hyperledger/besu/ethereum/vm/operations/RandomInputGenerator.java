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

import java.util.concurrent.ThreadLocalRandom;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.word256.Word256;

/** Utility for generating random 256-bit (32-byte) values for benchmarking or testing. */
public final class RandomInputGenerator {

  private RandomInputGenerator() {}

  /**
   * Fills two arrays with random 32-byte values.
   *
   * @param aPool the destination array for first operands
   * @param bPool the destination array for second operands
   */
  public static void fillPools(final Word256[] aPool, final Word256[] bPool) {
    final ThreadLocalRandom random = ThreadLocalRandom.current();
    for (int i = 0; i < aPool.length; i++) {
      final byte[] a = new byte[32];
      final byte[] b = new byte[32];
      random.nextBytes(a);
      random.nextBytes(b);
      aPool[i] = Word256.fromBytes(a);
      bPool[i] = Word256.fromBytes(b);
    }
  }
}
