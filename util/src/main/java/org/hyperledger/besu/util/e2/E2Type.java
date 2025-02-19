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
package org.hyperledger.besu.util.e2;

import java.util.HexFormat;

public enum E2Type {
  EMPTY(new byte[] {0x00, 0x00}),
  COMPRESSED_SIGNED_BEACON_BLOCK(new byte[] {0x01, 0x00}),
  COMPRESSED_BEACON_STATE(new byte[] {0x02, 0x00}),
  COMPRESSED_EXECUTION_BLOCK_HEADER(new byte[] {0x03, 0x00}),
  COMPRESSED_EXECUTION_BLOCK_BODY(new byte[] {0x04, 0x00}),
  COMPRESSED_EXECUTION_BLOCK_RECEIPTS(new byte[] {0x05, 0x00}),
  TOTAL_DIFFICULTY(new byte[] {0x06, 0x00}),
  ACCUMULATOR(new byte[] {0x07, 0x00}),
  VERSION(new byte[] {0x65, 0x32}),
  BLOCK_INDEX(new byte[] {0x66, 0x32}),
  SLOT_INDEX(new byte[] {0x69, 0x32}),
  ;
  private final byte[] typeCode;

  E2Type(final byte[] typeCode) {
    this.typeCode = typeCode;
  }

  public byte[] getTypeCode() {
    return typeCode;
  }

  public static E2Type getForTypeCode(final byte[] typeCode) {
    if (typeCode == null || typeCode.length != 2) {
      throw new IllegalArgumentException("typeCode must be 2 bytes");
    }

    E2Type result = null;
    for (E2Type e2Type : values()) {
      if (e2Type.typeCode[0] == typeCode[0] && e2Type.typeCode[1] == typeCode[1]) {
        result = e2Type;
      }
    }
    if (result == null) {
      throw new IllegalArgumentException(
          "typeCode " + HexFormat.of().formatHex(typeCode) + " is not recognised");
    }
    return result;
  }
}
