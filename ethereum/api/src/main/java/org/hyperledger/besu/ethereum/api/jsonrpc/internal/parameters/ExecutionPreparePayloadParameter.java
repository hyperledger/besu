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
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes32;

/**
 * parentHash: DATA, 32 Bytes - hash of the parent block timestamp: QUANTITY, 64 Bits - value for
 * the timestamp field of the new payload random: DATA, 32 Bytes - value for the random field of the
 * new payload feeRecipient: DATA, 20 Bytes - suggested value for the coinbase field of the new
 * payload
 */
public class ExecutionPreparePayloadParameter {

  @JsonCreator
  public ExecutionPreparePayloadParameter(
      @JsonProperty("parentHash") final Hash parentHash,
      @JsonProperty("timestamp") final UnsignedLongParameter timestamp,
      @JsonProperty("random") final String random,
      @JsonProperty("feeRecipient") final Address feeRecipient) {
    this.parentHash = parentHash;
    this.timestamp = timestamp.getValue();
    this.random = Bytes32.fromHexString(random);
    this.feeRecipient = feeRecipient;
  }

  private final Hash parentHash;
  private final Long timestamp;
  private final Bytes32 random;
  private final Address feeRecipient;

  public Hash getParentHash() {
    return parentHash;
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
}
