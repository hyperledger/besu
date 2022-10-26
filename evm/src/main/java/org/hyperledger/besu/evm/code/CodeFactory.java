/*
 * Copyright contributors to Hyperledger Besu
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
 *
 */

package org.hyperledger.besu.evm.code;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;

import org.apache.tuweni.bytes.Bytes;

public final class CodeFactory {

  public static final int MAX_KNOWN_CODE_VERSION = 1;
  static final int DEFAULT_MAX_CODE_VERSION = 0;
  static final Bytes prefixV1 = Bytes.fromHexString("0xef0001");

  private CodeFactory() {
    // factory class, no instantiations.
  }

  public static Code createCode(final Bytes bytes, final Hash codeHash) {
    return createCode(bytes, codeHash, DEFAULT_MAX_CODE_VERSION);
  }

  public static Code createCode(final Bytes bytes, final Hash codeHash, final int maxEofVersion) {
    if (maxEofVersion == 0) {
      return new CodeV0(bytes, codeHash);
    } else if (maxEofVersion == 1) {
      if (bytes.commonPrefixLength(prefixV1) == 3) {
        final EOFLayout layout = EOFLayout.parseEOF(bytes);
        if (!layout.isValid()) {
          return new CodeInvalid(
              codeHash, bytes, "Invalid EOF Layout: " + layout.getInvalidReason());
        }
        final long[] jumpMap =
            OpcodesV1.validateAndCalculateJumpDests(layout.getSections()[EOFLayout.SECTION_CODE]);
        if (jumpMap != null) {
          return new CodeV1(codeHash, layout, jumpMap);
        } else {
          return new CodeInvalid(codeHash, bytes, "Opcode Validation Failed");
        }
      } else {
        return new CodeV0(bytes, codeHash);
      }
    } else {
      return new CodeInvalid(codeHash, bytes, "Unsupported max code version " + maxEofVersion);
    }
  }
}
