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

import org.hyperledger.besu.evm.Code;

import org.apache.tuweni.bytes.Bytes;

/** The Code factory. */
public final class CodeFactory {

  /** The constant EOF_LEAD_BYTE. */
  public static final byte EOF_LEAD_BYTE = -17; // 0xEF in signed byte form

  private CodeFactory() {
    // factory class, no instantiations.
  }

  /**
   * Create Code.
   *
   * @param bytes the bytes
   * @param maxEofVersion the max eof version
   * @param inCreateOperation the in create operation
   * @return the code
   */
  public static Code createCode(
      final Bytes bytes, final int maxEofVersion, final boolean inCreateOperation) {
    if (maxEofVersion == 0) {
      return new CodeV0(bytes);
    } else if (maxEofVersion == 1) {
      int codeSize = bytes.size();
      if (codeSize > 0 && bytes.get(0) == EOF_LEAD_BYTE) {
        if (codeSize == 1 && !inCreateOperation) {
          return new CodeV0(bytes);
        }
        if (codeSize < 3) {
          return new CodeInvalid(bytes, "EOF Container too short");
        }
        if (bytes.get(1) != 0) {
          if (inCreateOperation) {
            // because some 0xef code made it to mainnet, this is only an error at contract create
            return new CodeInvalid(bytes, "Incorrect second byte");
          } else {
            return new CodeV0(bytes);
          }
        }
        int version = bytes.get(2);
        if (version != 1) {
          return new CodeInvalid(bytes, "Unsupported EOF Version: " + version);
        }

        final EOFLayout layout = EOFLayout.parseEOF(bytes);
        if (!layout.isValid()) {
          return new CodeInvalid(bytes, "Invalid EOF Layout: " + layout.invalidReason());
        }

        final String codeValidationError = CodeV1Validation.validateCode(layout);
        if (codeValidationError != null) {
          return new CodeInvalid(bytes, "EOF Code Invalid : " + codeValidationError);
        }

        final String stackValidationError = CodeV1Validation.validateStack(layout);
        if (stackValidationError != null) {
          return new CodeInvalid(bytes, "EOF Code Invalid : " + stackValidationError);
        }

        return new CodeV1(layout);
      } else {
        return new CodeV0(bytes);
      }
    } else {
      return new CodeInvalid(bytes, "Unsupported max code version " + maxEofVersion);
    }
  }
}
