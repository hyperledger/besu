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
import org.hyperledger.besu.evm.cache.JumpDestBitmaskCache;
import org.hyperledger.besu.evm.frame.MessageFrame;

import org.apache.tuweni.bytes.Bytes;

/** JumpService contains the logic to verify and execute jumps in the EVM */
public class JumpService {
  private static final JumpDestBitmaskCache jumpDestBitmaskCache = new JumpDestBitmaskCache();

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
    final long[] validJumpDestinationsBitMask = getOrCreateValidJumpDestinationsBitMask(code);

    if (isJumpDestInvalid(code, validJumpDestinationsBitMask, jumpDestination)) {
      return invalidJumpResponse;
    }
    frame.setPC(jumpDestination);
    return validJumpResponse;
  }

  private long[] getOrCreateValidJumpDestinationsBitMask(final Code code) {
    long[] validJumpDestinationsBitMask = code.getJumpDestBitMask();

    if (validJumpDestinationsBitMask != null) {
      return validJumpDestinationsBitMask;
    }

    long[] cachedValidJumpDestinationsBitMask =
        jumpDestBitmaskCache.getIfPresent(code.getCodeHash());

    if (cachedValidJumpDestinationsBitMask == null) {
      cachedValidJumpDestinationsBitMask = calculateJumpDestBitMask(code);
      jumpDestBitmaskCache.put(code.getCodeHash(), cachedValidJumpDestinationsBitMask);
    }

    code.setJumpDestBitMask(cachedValidJumpDestinationsBitMask);

    return cachedValidJumpDestinationsBitMask;
  }

  private boolean isJumpDestInvalid(
      final Code code, final long[] validJumpDestinationsBitMask, final int jumpDestination) {
    if (jumpDestination < 0 || jumpDestination >= code.getSize()) {
      return true;
    }

    // This selects which long in the array holds the bit for the given offset:
    //	1)	>>> 6 is equivalent to jumpDestination / 64
    //	2)	Each long holds 64 bits, so this finds the correct chunk
    final long targetLong = validJumpDestinationsBitMask[jumpDestination >>> 6];

    // 1) & 0x3F is jumpDestination % 64
    // 2)	1L << ... gives a mask for the specific bit in that long
    final long targetBit = 1L << (jumpDestination & 0x3F);

    // If the bit is not set, then it is an invalid jump destination
    return (targetLong & targetBit) == 0L;
  }

  long[] calculateJumpDestBitMask(final Code code) {
    final int size = code.getSize();
    final long[] bitmap = new long[(size >> 6) + 1];
    final byte[] rawCode = code.getBytes().toArrayUnsafe();
    final int length = rawCode.length;
    for (int i = 0; i < length; ) {
      long thisEntry = 0L;
      final int entryPos = i >> 6;
      final int max = Math.min(64, length - (entryPos << 6));
      int j = i & 0x3F;
      for (; j < max; i++, j++) {
        final byte operationNum = rawCode[i];
        if (operationNum >= JumpDestOperation.OPCODE) {
          switch (operationNum) {
            case JumpDestOperation.OPCODE:
              thisEntry |= 1L << j;
              break;
            case 0x60:
              i += 1;
              j += 1;
              break;
            case 0x61:
              i += 2;
              j += 2;
              break;
            case 0x62:
              i += 3;
              j += 3;
              break;
            case 0x63:
              i += 4;
              j += 4;
              break;
            case 0x64:
              i += 5;
              j += 5;
              break;
            case 0x65:
              i += 6;
              j += 6;
              break;
            case 0x66:
              i += 7;
              j += 7;
              break;
            case 0x67:
              i += 8;
              j += 8;
              break;
            case 0x68:
              i += 9;
              j += 9;
              break;
            case 0x69:
              i += 10;
              j += 10;
              break;
            case 0x6a:
              i += 11;
              j += 11;
              break;
            case 0x6b:
              i += 12;
              j += 12;
              break;
            case 0x6c:
              i += 13;
              j += 13;
              break;
            case 0x6d:
              i += 14;
              j += 14;
              break;
            case 0x6e:
              i += 15;
              j += 15;
              break;
            case 0x6f:
              i += 16;
              j += 16;
              break;
            case 0x70:
              i += 17;
              j += 17;
              break;
            case 0x71:
              i += 18;
              j += 18;
              break;
            case 0x72:
              i += 19;
              j += 19;
              break;
            case 0x73:
              i += 20;
              j += 20;
              break;
            case 0x74:
              i += 21;
              j += 21;
              break;
            case 0x75:
              i += 22;
              j += 22;
              break;
            case 0x76:
              i += 23;
              j += 23;
              break;
            case 0x77:
              i += 24;
              j += 24;
              break;
            case 0x78:
              i += 25;
              j += 25;
              break;
            case 0x79:
              i += 26;
              j += 26;
              break;
            case 0x7a:
              i += 27;
              j += 27;
              break;
            case 0x7b:
              i += 28;
              j += 28;
              break;
            case 0x7c:
              i += 29;
              j += 29;
              break;
            case 0x7d:
              i += 30;
              j += 30;
              break;
            case 0x7e:
              i += 31;
              j += 31;
              break;
            case 0x7f:
              i += 32;
              j += 32;
              break;
            default:
          }
        }
      }
      bitmap[entryPos] = thisEntry;
    }
    return bitmap;
  }
}
