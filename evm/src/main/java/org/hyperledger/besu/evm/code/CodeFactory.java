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
package org.hyperledger.besu.evm.code;

import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.code.EOFLayout.EOFContainerMode;

import javax.annotation.Nonnull;

import org.apache.tuweni.bytes.Bytes;

/** The Code factory. */
public class CodeFactory {

  /** The constant EOF_LEAD_BYTE. */
  public static final byte EOF_LEAD_BYTE = -17; // 0xEF in signed byte form

  /** Maximum EOF version that can be produced. Legacy is considered EOF version zero. */
  protected final int maxEofVersion;

  /** Maximum size of the code stream that can be produced, including all header bytes. */
  protected final int maxContainerSize;

  /** The EOF validator against which EOF layouts will be validated. */
  EOFValidator eofValidator;

  /**
   * Create a code factory.
   *
   * @param maxEofVersion Maximum EOF version that can be set
   * @param maxContainerSize Maximum size of a container that will be parsed.
   */
  public CodeFactory(final int maxEofVersion, final int maxContainerSize) {
    this.maxEofVersion = maxEofVersion;
    this.maxContainerSize = maxContainerSize;

    eofValidator = new CodeV1Validation(maxContainerSize);
  }

  /**
   * Create Code.
   *
   * @param bytes the bytes
   * @return the code
   */
  public Code createCode(final Bytes bytes) {
    return createCode(bytes, false);
  }

  /**
   * Create Code.
   *
   * @param bytes the bytes
   * @param createTransaction This is in a create transaction, allow dangling data
   * @return the code
   */
  public Code createCode(final Bytes bytes, final boolean createTransaction) {
    return switch (maxEofVersion) {
      case 0 -> new CodeV0(bytes);
      case 1 -> createV1Code(bytes, createTransaction);
      default -> new CodeInvalid(bytes, "Unsupported max code version " + maxEofVersion);
    };
  }

  private @Nonnull Code createV1Code(final Bytes bytes, final boolean createTransaction) {
    int codeSize = bytes.size();
    if (codeSize > 0 && bytes.get(0) == EOF_LEAD_BYTE) {
      if (codeSize < 3) {
        return new CodeInvalid(bytes, "EOF Container too short");
      }
      if (bytes.get(1) != 0) {
        if (createTransaction) {
          // because some 0xef code made it to mainnet, this is only an error at contract creation
          // time
          return new CodeInvalid(bytes, "Incorrect second byte");
        } else {
          return new CodeV0(bytes);
        }
      }
      int version = bytes.get(2);
      if (version != 1) {
        return new CodeInvalid(bytes, "Unsupported EOF Version: " + version);
      }

      final EOFLayout layout = EOFLayout.parseEOF(bytes, !createTransaction);
      if (createTransaction) {
        layout.containerMode().set(EOFContainerMode.INITCODE);
      }
      return createCode(layout);
    } else {
      return new CodeV0(bytes);
    }
  }

  @Nonnull
  Code createCode(final EOFLayout layout) {
    if (!layout.isValid()) {
      return new CodeInvalid(layout.container(), "Invalid EOF Layout: " + layout.invalidReason());
    }

    final String validationError = eofValidator.validate(layout);
    if (validationError != null) {
      return new CodeInvalid(layout.container(), "EOF Code Invalid : " + validationError);
    }

    return new CodeV1(layout);
  }
}
