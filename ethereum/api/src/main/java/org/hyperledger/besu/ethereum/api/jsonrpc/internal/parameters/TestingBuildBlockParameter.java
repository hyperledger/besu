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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.datatypes.Hash;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;

public class TestingBuildBlockParameter {

  private final Hash parentBlockHash;
  private final EnginePayloadAttributesParameter payloadAttributes;
  private final List<String> transactions;
  private final Bytes extraData;

  @JsonCreator
  public TestingBuildBlockParameter(
      @JsonProperty("parentBlockHash") final String parentBlockHash,
      @JsonProperty("payloadAttributes") final EnginePayloadAttributesParameter payloadAttributes,
      @JsonProperty("transactions") final List<String> transactions,
      @JsonProperty("extraData") final String extraData) {
    this.parentBlockHash = Hash.fromHexString(parentBlockHash);
    this.payloadAttributes = payloadAttributes;
    this.transactions = transactions;
    this.extraData =
        extraData == null || extraData.isEmpty() ? Bytes.EMPTY : Bytes.fromHexString(extraData);
  }

  public Hash getParentBlockHash() {
    return parentBlockHash;
  }

  public EnginePayloadAttributesParameter getPayloadAttributes() {
    return payloadAttributes;
  }

  public List<String> getTransactions() {
    return transactions;
  }

  public Bytes getExtraData() {
    return extraData;
  }
}
