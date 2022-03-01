/*
 * Copyright Hyperledger Besu Contributors.
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

import org.hyperledger.besu.datatypes.Address;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.json.JsonObject;
import org.apache.tuweni.bytes.Bytes32;

public class EnginePayloadAttributesParameter {

  final Long timestamp;
  final Bytes32 prevRandao;
  final Address suggestedFeeRecipient;

  @JsonCreator
  public EnginePayloadAttributesParameter(
      @JsonProperty("timestamp") final String timestamp,
      @JsonProperty("prevRandao") final String prevRandao,
      @JsonProperty("suggestedFeeRecipient") final String suggestedFeeRecipient) {
    this.timestamp = Long.decode(timestamp);
    this.prevRandao = Bytes32.fromHexString(prevRandao);
    this.suggestedFeeRecipient = Address.fromHexString(suggestedFeeRecipient);
  }

  public Long getTimestamp() {
    return timestamp;
  }

  public Bytes32 getPrevRandao() {
    return prevRandao;
  }

  public Address getSuggestedFeeRecipient() {
    return suggestedFeeRecipient;
  }

  public String serialize() {
    return new JsonObject()
        .put("timestamp", timestamp)
        .put("prevRandao", prevRandao.toHexString())
        .put("suggestedFeeRecipient", suggestedFeeRecipient.toHexString())
        .encode();
  }
}
