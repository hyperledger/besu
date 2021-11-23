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

public class ExecutionPayloadAttributesParameter {

  final Long timestamp;
  final Bytes32 random;
  final Address feeRecipient;

  @JsonCreator
  public ExecutionPayloadAttributesParameter(
      @JsonProperty("timestamp") final String timestamp,
      @JsonProperty("random") final String random,
      @JsonProperty("feeRecpient") final String feeRecipient) {
    this.timestamp = Long.decode(timestamp);
    this.random = Bytes32.fromHexString(random);
    this.feeRecipient = Address.fromHexString(feeRecipient);
  }

  public Long getTimestamp() {
    return timestamp;
  }

  public Bytes32 getRandom() {
    return random;
  }

  public Address getFeeRecipient() {
    return feeRecipient;
  }

  public String serialize() {
    return new JsonObject()
        .put("timestamp", timestamp)
        .put("random", random.toShortHexString())
        .put("feeRecipient", feeRecipient.toShortHexString())
        .encode();
  }
}
