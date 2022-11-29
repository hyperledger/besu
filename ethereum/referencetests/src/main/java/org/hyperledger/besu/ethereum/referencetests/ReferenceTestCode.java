/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.referencetests;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.code.CodeFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.tuweni.bytes.Bytes;

/** A mock for representing EVM Code associated with an account. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReferenceTestCode implements Code {

  private final Code code;

  /**
   * Public constructor.
   *
   * @param bytes - A hex string representation of the code.
   */
  @JsonCreator
  public ReferenceTestCode(final String bytes) {
    this.code =
        CodeFactory.createCode(
            Bytes.fromHexString(bytes),
            Hash.hash(Bytes.fromHexString(bytes)),
            CodeFactory.MAX_KNOWN_CODE_VERSION,
            false);
  }

  @Override
  public int getSize() {
    return code.getSize();
  }

  @Override
  public Bytes getCodeBytes() {
    return code.getCodeBytes();
  }

  @Override
  public Bytes getContainerBytes() {
    return code.getContainerBytes();
  }

  @Override
  public Hash getCodeHash() {
    return code.getCodeHash();
  }

  @Override
  public boolean isJumpDestInvalid(final int jumpDestination) {
    return code.isJumpDestInvalid(jumpDestination);
  }

  @Override
  public boolean isValid() {
    return code.isValid();
  }
}
