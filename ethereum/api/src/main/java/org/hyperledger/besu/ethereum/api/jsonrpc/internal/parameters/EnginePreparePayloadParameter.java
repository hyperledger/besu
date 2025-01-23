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
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.parameters.UnsignedLongParameter;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes32;

public class EnginePreparePayloadParameter {
  private final Optional<Hash> parentHash;
  private final Address feeRecipient;
  private final Bytes32 prevRandao;
  private final Optional<Long> timestamp;
  final List<WithdrawalParameter> withdrawals;
  private final Optional<Bytes32> parentBeaconBlockRoot;

  @JsonCreator
  public EnginePreparePayloadParameter(
      @JsonProperty("parentHash") final Optional<Hash> parentHash,
      @JsonProperty("feeRecipient") final Optional<Address> feeRecipient,
      @JsonProperty("timestamp") final Optional<UnsignedLongParameter> timestamp,
      @JsonProperty("prevRandao") final Optional<String> prevRandao,
      @JsonProperty("withdrawals") final Optional<List<WithdrawalParameter>> withdrawals,
      @JsonProperty("parentBeaconBlockRoot") final Optional<Bytes32> parentBeaconBlockRoot) {
    this.parentHash = parentHash;
    this.feeRecipient = feeRecipient.orElse(Address.ZERO);
    this.timestamp = timestamp.map(UnsignedLongParameter::getValue);
    this.prevRandao = Bytes32.fromHexStringLenient(prevRandao.orElse("deadbeef"));
    this.withdrawals = withdrawals.orElse(Collections.emptyList());
    this.parentBeaconBlockRoot = parentBeaconBlockRoot;
  }

  public Optional<Hash> getParentHash() {
    return parentHash;
  }

  public Address getFeeRecipient() {
    return feeRecipient;
  }

  public Optional<Long> getTimestamp() {
    return timestamp;
  }

  public Bytes32 getPrevRandao() {
    return prevRandao;
  }

  public List<WithdrawalParameter> getWithdrawals() {
    return withdrawals;
  }

  public Optional<Bytes32> getParentBeaconBlockRoot() {
    return parentBeaconBlockRoot;
  }
}
