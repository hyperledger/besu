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

  public static final byte EOF_LEAD_BYTE = -17; // 0xEF in signed byte form

  private CodeFactory() {
    // factory class, no instantiations.
  }

  public static Code createCode(
      final Bytes bytes,
      final Hash codeHash,
      final int maxEofVersion,
      final boolean inCreateOperation) {
    if (maxEofVersion == 0) {
      return new CodeV0(bytes, codeHash);
    } else if (maxEofVersion == 1) {
      int codeSize = bytes.size();
      if (codeSize > 0 && bytes.get(0) == EOF_LEAD_BYTE) {
        if (codeSize == 1 && !inCreateOperation) {
          return new CodeV0(bytes, codeHash);
        }
        if (codeSize < 3) {
          return new CodeInvalid(codeHash, bytes, "EOF Container too short");
        }
        if (bytes.get(1) != 0) {
          if (inCreateOperation) {
            // because some 0xef code made it to mainnet, this is only an error at contract create
            return new CodeInvalid(codeHash, bytes, "Incorrect second byte");
          } else {
            return new CodeV0(bytes, codeHash);
          }
        }
        int version = bytes.get(2);
        if (version != 1) {
          return new CodeInvalid(codeHash, bytes, "Unsupported EOF Version: " + version);
        }

        final EOFLayout layout = EOFLayout.parseEOF(bytes);
        if (!layout.isValid()) {
          return new CodeInvalid(
              codeHash, bytes, "Invalid EOF Layout: " + layout.getInvalidReason());
        }

        final String codeValidationError = CodeV1.validateCode(layout);
        if (codeValidationError != null) {
          return new CodeInvalid(codeHash, bytes, "EOF Code Invalid : " + codeValidationError);
        }

        final String stackValidationError = CodeV1.validateStack(layout);
        if (stackValidationError != null) {
          return new CodeInvalid(codeHash, bytes, "EOF Code Invalid : " + codeValidationError);
        }

        return new CodeV1(codeHash, layout);
      } else {
        return new CodeV0(bytes, codeHash);
      }
    } else {
      return new CodeInvalid(codeHash, bytes, "Unsupported max code version " + maxEofVersion);
    }
  }
}
