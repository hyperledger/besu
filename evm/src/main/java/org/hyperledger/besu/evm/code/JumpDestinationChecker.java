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
package org.hyperledger.besu.evm.code;

import org.hyperledger.besu.evm.code.bytecode.Bytecode;
import org.hyperledger.besu.evm.operation.JumpDestOperation;

/**
 * The {@code JumpDestinationChecker} class provides methods to identify and verify valid jump
 * destinations within EVM bytecode. It maintains a map of valid jump destinations, allowing checks
 * to determine if a given offset is a valid {@code JUMPDEST} location (i.e., not part of push-data
 * or out of the bytecode range).
 */
public class JumpDestinationChecker {

  private final Bytecode.RawByteArray rawByteCode;

  /** Used to cache valid jump destinations. */
  private long[] validJumpDestinations;

  /**
   * Construct a jump destination checker for the corresponding bytecode
   *
   * @param bytecode to check
   */
  public JumpDestinationChecker(final Bytecode bytecode) {
    this.rawByteCode = bytecode.getRawByteArray();
  }

  /**
   * Checks whether the given jump destination offset is invalid within the raw bytecode.
   *
   * @param jumpDestination the offset in the bytecode to check
   * @return {@code true} if the jump destination is invalid, or {@code false} if it is valid
   */
  public boolean isJumpDestInvalid(final int jumpDestination) {
    if (jumpDestination < 0 || jumpDestination >= rawByteCode.size()) {
      return true;
    }
    if (validJumpDestinations == null || validJumpDestinations.length == 0) {
      validJumpDestinations = calculateJumpDests();
    }

    final long targetLong = validJumpDestinations[jumpDestination >>> 6];
    final long targetBit = 1L << (jumpDestination & 0x3F);
    return (targetLong & targetBit) == 0L;
  }

  /**
   * Calculate jump destination.
   *
   * @return the long [ ]
   */
  long[] calculateJumpDests() {
    final int size = rawByteCode.size();
    final long[] bitmap = new long[(size >> 6) + 1];
    final int length = rawByteCode.size();
    for (int i = 0; i < length; ) {
      long thisEntry = 0L;
      final int entryPos = i >> 6;
      final int max = Math.min(64, length - (entryPos << 6));
      int j = i & 0x3F;
      for (; j < max; i++, j++) {
        final byte operationNum = rawByteCode.get(i);
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
