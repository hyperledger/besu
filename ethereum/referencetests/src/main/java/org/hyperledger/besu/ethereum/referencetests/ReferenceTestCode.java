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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.tuweni.bytes.Bytes;

/** A mock for representing EVM Code associated with an account. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReferenceTestCode extends Code {

  /**
   * Public constructor.
   *
   * @param bytes - A hex string representation of the code.
   */
  @JsonCreator
  public ReferenceTestCode(final String bytes) {
    super(Bytes.fromHexString(bytes), Hash.hash(Bytes.fromHexString(bytes)));
  }
}
