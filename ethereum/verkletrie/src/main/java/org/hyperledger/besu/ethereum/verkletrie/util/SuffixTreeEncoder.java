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
package org.hyperledger.besu.ethereum.verkletrie.util;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class SuffixTreeEncoder {
  private static final int VERSION_OFFSET = 0;
  private static final int CODE_SIZE_OFFSET = 5;
  private static final int NONCE_OFFSET = 8;
  private static final int BALANCE_OFFSET = 16;
  private static final Bytes32 VERSION_VALUE_MASK;
  private static final Bytes32 CODE_SIZE_VALUE_MASK;
  private static final Bytes32 NONCE_VALUE_MASK;
  private static final Bytes32 BALANCE_VALUE_MASK;

  static {
    VERSION_VALUE_MASK =
        encodeIntoBasicDataLeaf(Bytes.repeat((byte) 0xff, 1), VERSION_OFFSET).not();
    CODE_SIZE_VALUE_MASK =
        encodeIntoBasicDataLeaf(Bytes.repeat((byte) 0xff, 3), CODE_SIZE_OFFSET).not();
    NONCE_VALUE_MASK = encodeIntoBasicDataLeaf(Bytes.repeat((byte) 0xff, 8), NONCE_OFFSET).not();
    BALANCE_VALUE_MASK =
        encodeIntoBasicDataLeaf(Bytes.repeat((byte) 0xff, 16), BALANCE_OFFSET).not();
  }

  public static Bytes32 eraseVersion(final Bytes32 value) {
    return value.and(VERSION_VALUE_MASK);
  }

  public static Bytes32 eraseCodeSize(final Bytes32 value) {
    return value.and(CODE_SIZE_VALUE_MASK);
  }

  public static Bytes32 eraseNonce(final Bytes32 value) {
    return value.and(NONCE_VALUE_MASK);
  }

  public static Bytes32 eraseBalance(final Bytes32 value) {
    return value.and(BALANCE_VALUE_MASK);
  }

  public static Bytes32 addVersionIntoValue(final Bytes32 value, final Bytes version) {
    return value.or(encodeIntoBasicDataLeaf(version, VERSION_OFFSET));
  }

  public static Bytes32 addCodeSizeIntoValue(final Bytes32 value, final Bytes codeSize) {
    return value.or(encodeIntoBasicDataLeaf(codeSize, CODE_SIZE_OFFSET));
  }

  public static Bytes32 addNonceIntoValue(final Bytes32 value, final Bytes nonce) {
    return value.or(encodeIntoBasicDataLeaf(nonce, NONCE_OFFSET));
  }

  public static Bytes32 addBalanceIntoValue(final Bytes32 value, final Bytes balance) {
    return value.or(encodeIntoBasicDataLeaf(balance, BALANCE_OFFSET));
  }

  /**
   * Encoding of a field into the BasicDataLeaf 32 byte value, using Little-Endian order.
   *
   * @param value to encode into a 32 byte value
   * @param byteShift byte position of `value` within the final 32 byte value
   * @throws IllegalArgumentException if `value` does not fit within 32 bytes after being encoded
   * @return encoded BasicDataLeaf value
   */
  public static Bytes32 encodeIntoBasicDataLeaf(final Bytes value, final int byteShift) {
    Bytes32 value32Bytes = Bytes32.leftPad(value);
    if (byteShift == 0) {
      return value32Bytes;
    } else if (byteShift < 0) {
      throw new IllegalArgumentException(
          "invalid byteShift " + byteShift + " must be greater than zero");
    } else if (value32Bytes.numberOfLeadingZeroBytes() < byteShift) {
      int valueSizeBytes = 32 - value32Bytes.numberOfLeadingZeroBytes() + byteShift;
      throw new IllegalArgumentException(
          "value must be 32 bytes but becomes "
              + valueSizeBytes
              + " bytes with byteShift "
              + byteShift);
    }
    return value32Bytes.shiftLeft(byteShift * 8);
  }
}
