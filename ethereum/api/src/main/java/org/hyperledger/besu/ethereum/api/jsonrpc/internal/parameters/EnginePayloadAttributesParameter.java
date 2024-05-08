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

import org.hyperledger.besu.datatypes.Address;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.json.JsonObject;
import org.apache.tuweni.bytes.Bytes32;

/** The type Engine payload attributes parameter. */
public class EnginePayloadAttributesParameter {

  /** The Timestamp. */
  final Long timestamp;

  /** The Prev randao. */
  final Bytes32 prevRandao;

  /** The Suggested fee recipient. */
  final Address suggestedFeeRecipient;

  /** The Withdrawals. */
  final List<WithdrawalParameter> withdrawals;

  private final Bytes32 parentBeaconBlockRoot;

  /**
   * Instantiates a new Engine payload attributes parameter.
   *
   * @param timestamp the timestamp
   * @param prevRandao the prev randao
   * @param suggestedFeeRecipient the suggested fee recipient
   * @param withdrawals the withdrawals
   * @param parentBeaconBlockRoot the parent beacon block root
   */
  @JsonCreator
  public EnginePayloadAttributesParameter(
      @JsonProperty("timestamp") final String timestamp,
      @JsonProperty("prevRandao") final String prevRandao,
      @JsonProperty("suggestedFeeRecipient") final String suggestedFeeRecipient,
      @JsonProperty("withdrawals") final List<WithdrawalParameter> withdrawals,
      @JsonProperty("parentBeaconBlockRoot") final String parentBeaconBlockRoot) {
    this.timestamp = Long.decode(timestamp);
    this.prevRandao = Bytes32.fromHexString(prevRandao);
    this.suggestedFeeRecipient = Address.fromHexString(suggestedFeeRecipient);
    this.withdrawals = withdrawals;
    this.parentBeaconBlockRoot =
        parentBeaconBlockRoot == null ? null : Bytes32.fromHexString(parentBeaconBlockRoot);
  }

  /**
   * Gets timestamp.
   *
   * @return the timestamp
   */
  public Long getTimestamp() {
    return timestamp;
  }

  /**
   * Gets prev randao.
   *
   * @return the prev randao
   */
  public Bytes32 getPrevRandao() {
    return prevRandao;
  }

  /**
   * Gets suggested fee recipient.
   *
   * @return the suggested fee recipient
   */
  public Address getSuggestedFeeRecipient() {
    return suggestedFeeRecipient;
  }

  /**
   * Gets parent beacon block root.
   *
   * @return the parent beacon block root
   */
  public Bytes32 getParentBeaconBlockRoot() {
    return parentBeaconBlockRoot;
  }

  /**
   * Gets withdrawals.
   *
   * @return the withdrawals
   */
  public List<WithdrawalParameter> getWithdrawals() {
    return withdrawals;
  }

  /**
   * Serialize string.
   *
   * @return the string
   */
  public String serialize() {
    final JsonObject json =
        new JsonObject()
            .put("timestamp", timestamp)
            .put("prevRandao", prevRandao.toHexString())
            .put("suggestedFeeRecipient", suggestedFeeRecipient.toHexString());
    if (withdrawals != null) {
      json.put(
          "withdrawals",
          withdrawals.stream().map(WithdrawalParameter::asJsonObject).collect(Collectors.toList()));
    }
    if (parentBeaconBlockRoot != null) {
      json.put("parentBeaconBlockRoot", parentBeaconBlockRoot.toHexString());
    }
    return json.encode();
  }
}
