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

import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.MessageFrame;

import org.apache.tuweni.bytes.Bytes;

/** JumpService contains the logic to verify and execute jumps in the EVM */
public class JumpService {
  /** creates a new JumpService object */
  public JumpService() {
    // Default constructor
  }

  /**
   * Performs the jump operation
   *
   * @param frame the MessageFrame containing the code and PC
   * @param dest the jump destination
   * @param validJumpResponse the response to return in case the jump is successful
   * @param invalidJumpResponse the response to return in case the jump failed
   * @return either @validJumpResponse or @invalidJumpResponse depending on the result
   */
  public Operation.OperationResult performJump(
      final MessageFrame frame,
      final Bytes dest,
      final Operation.OperationResult validJumpResponse,
      final Operation.OperationResult invalidJumpResponse) {
    final int jumpDestination;
    try {
      jumpDestination = dest.toInt();
    } catch (final RuntimeException re) {
      return invalidJumpResponse;
    }

    final Code code = frame.getCode();

    if (code.isJumpDestInvalid(jumpDestination)) {
      return invalidJumpResponse;
    }

    frame.setPC(jumpDestination);
    return validJumpResponse;
  }

  /**
   * Performs the jump operation with a native UInt256 destination.
   *
   * @param frame the MessageFrame containing the code and PC
   * @param dest the jump destination as UInt256
   * @param validJumpResponse the response to return in case the jump is successful
   * @param invalidJumpResponse the response to return in case the jump failed
   * @return either validJumpResponse or invalidJumpResponse depending on the result
   */
  public Operation.OperationResult performJump(
      final MessageFrame frame,
      final org.hyperledger.besu.evm.UInt256 dest,
      final Operation.OperationResult validJumpResponse,
      final Operation.OperationResult invalidJumpResponse) {
    if (dest.u3() != 0
        || dest.u2() != 0
        || dest.u1() != 0
        || dest.u0() < 0
        || dest.u0() > Integer.MAX_VALUE) {
      return invalidJumpResponse;
    }
    final int jumpDestination = (int) dest.u0();

    final Code code = frame.getCode();

    if (code.isJumpDestInvalid(jumpDestination)) {
      return invalidJumpResponse;
    }

    frame.setPC(jumpDestination);
    return validJumpResponse;
  }

  /**
   * Performs the jump operation reading the destination directly from the raw stack array.
   *
   * @param frame the MessageFrame containing the code and PC
   * @param s the raw stack long array
   * @param off the offset of the destination slot in the array (4 longs: u3, u2, u1, u0)
   * @param validJumpResponse the response to return in case the jump is successful
   * @param invalidJumpResponse the response to return in case the jump failed
   * @return either validJumpResponse or invalidJumpResponse depending on the result
   */
  public Operation.OperationResult performJump(
      final MessageFrame frame,
      final long[] s,
      final int off,
      final Operation.OperationResult validJumpResponse,
      final Operation.OperationResult invalidJumpResponse) {
    if (s[off] != 0
        || s[off + 1] != 0
        || s[off + 2] != 0
        || s[off + 3] < 0
        || s[off + 3] > Integer.MAX_VALUE) {
      return invalidJumpResponse;
    }
    final int jumpDestination = (int) s[off + 3];

    final Code code = frame.getCode();

    if (code.isJumpDestInvalid(jumpDestination)) {
      return invalidJumpResponse;
    }

    frame.setPC(jumpDestination);
    return validJumpResponse;
  }
}
