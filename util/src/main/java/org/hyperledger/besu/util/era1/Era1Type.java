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
package org.hyperledger.besu.util.era1;

import java.util.HexFormat;

/** An enumeration of known sections of era1 files */
public enum Era1Type {
  /** An empty section */
  EMPTY(new byte[] {0x00, 0x00}),
  /** A snappy compressed execution block header */
  COMPRESSED_EXECUTION_BLOCK_HEADER(new byte[] {0x03, 0x00}),
  /** A snappy compressed execution block body */
  COMPRESSED_EXECUTION_BLOCK_BODY(new byte[] {0x04, 0x00}),
  /** A snappy compressed list of execution block transaction receipts */
  COMPRESSED_EXECUTION_BLOCK_RECEIPTS(new byte[] {0x05, 0x00}),
  /** The total difficulty */
  TOTAL_DIFFICULTY(new byte[] {0x06, 0x00}),
  /** The accumulator */
  ACCUMULATOR(new byte[] {0x07, 0x00}),
  /** A version section */
  VERSION(new byte[] {0x65, 0x32}),
  /** An execution block index */
  BLOCK_INDEX(new byte[] {0x66, 0x32}),
  ;
  private final byte[] typeCode;

  Era1Type(final byte[] typeCode) {
    this.typeCode = typeCode;
  }

  /**
   * Gets the type code
   *
   * @return the type code
   */
  public byte[] getTypeCode() {
    return typeCode;
  }

  /**
   * Gets the Era1Type corresponding to the supplied typeCode
   *
   * @param typeCode the typeCode to find the corresponding Era1Type for
   * @return the Era1Type corresponding to the supplied typeCode
   * @throws IllegalArgumentException if there is no Era1Type corresponding to the supplied typeCode
   */
  public static Era1Type getForTypeCode(final byte[] typeCode) {
    if (typeCode == null || typeCode.length != 2) {
      throw new IllegalArgumentException("typeCode must be 2 bytes");
    }

    Era1Type result = null;
    for (Era1Type era1Type : values()) {
      if (era1Type.typeCode[0] == typeCode[0] && era1Type.typeCode[1] == typeCode[1]) {
        result = era1Type;
      }
    }
    if (result == null) {
      throw new IllegalArgumentException(
          "typeCode " + HexFormat.of().formatHex(typeCode) + " is not recognised");
    }
    return result;
  }
}
