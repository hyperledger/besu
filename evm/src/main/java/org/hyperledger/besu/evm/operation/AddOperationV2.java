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
package org.hyperledger.besu.evm.operation;

import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * EVM v2 ADD operation using long[] stack representation.
 *
 * <p>Each 256-bit word is stored as four longs: index 0 = most significant 64 bits, index 3 = least
 * significant 64 bits. Addition is performed with carry propagation from least to most significant
 * word, with overflow silently truncated (mod 2^256).
 */
public class AddOperationV2 {

  private static final Operation.OperationResult ADD_SUCCESS =
      new Operation.OperationResult(3, null);

  private AddOperationV2() {}

  /**
   * Execute the ADD opcode on the v2 long[] stack.
   *
   * @param frame the message frame
   * @return the operation result
   */
  public static Operation.OperationResult staticOperation(final MessageFrame frame) {
    final long[] a = new long[4];
    final long[] b = new long[4];
    frame.popStackLongs(a);
    frame.popStackLongs(b);

    // 256-bit addition with carry, least-significant word at index 3
    final long sum3 = a[3] + b[3];
    final long carry3 = Long.compareUnsigned(sum3, a[3]) < 0 ? 1L : 0L;

    final long sum2 = a[2] + b[2] + carry3;
    final long carry2 =
        (carry3 != 0 && sum2 == a[2]) || Long.compareUnsigned(sum2, a[2]) < 0 ? 1L : 0L;

    final long sum1 = a[1] + b[1] + carry2;
    final long carry1 =
        (carry2 != 0 && sum1 == a[1]) || Long.compareUnsigned(sum1, a[1]) < 0 ? 1L : 0L;

    // Most significant word: overflow is silently discarded (mod 2^256)
    final long sum0 = a[0] + b[0] + carry1;

    frame.pushStackLongs(sum0, sum1, sum2, sum3);
    return ADD_SUCCESS;
  }
}
