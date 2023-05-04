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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SendRawTransactionConditionalParameter {

  private final Optional<Long> fromBlock;
  private final Optional<Long> toBlock;
  // TODO may need a custom deserializer because the hash here could actually be a map
  private final Optional<Map<Address, Hash>> knownAccounts;
  private final Optional<Long> timestampMin, timestampMax;

  @JsonCreator
  public SendRawTransactionConditionalParameter(
      @JsonProperty("blockNumberMin") final Long fromBlock,
      @JsonProperty("blockNumberMax") final Long toBlock,
      @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
          @JsonProperty("knownAccounts")
          final Map<Address, Hash> knownAccounts,
      @JsonProperty("timestampMin") final Long timestampMin,
      @JsonProperty("timestampMax") final Long timestampMax) {
    this.fromBlock = Optional.ofNullable(fromBlock);
    this.toBlock = Optional.ofNullable(toBlock);
    this.knownAccounts = Optional.ofNullable(knownAccounts);
    this.timestampMin = Optional.ofNullable(timestampMin);
    this.timestampMax = Optional.ofNullable(timestampMax);
  }

  public Optional<Long> getBlockNumberMin() {
    return fromBlock;
  }

  public Optional<Long> getBlockNumberMax() {
    return toBlock;
  }

  public Optional<Map<Address, Hash>> getKnownAccounts() {
    return knownAccounts;
  }

  public Optional<Long> getTimestampMin() {
    return timestampMin;
  }

  public Optional<Long> getTimestampMax() {
    return timestampMax;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SendRawTransactionConditionalParameter that = (SendRawTransactionConditionalParameter) o;
    return Objects.equals(fromBlock, that.fromBlock)
        && Objects.equals(toBlock, that.toBlock)
        && Objects.equals(knownAccounts, that.knownAccounts)
        && Objects.equals(timestampMin, that.timestampMin)
        && Objects.equals(timestampMax, that.timestampMax);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fromBlock, toBlock, knownAccounts, timestampMin, timestampMax);
  }

  @Override
  public String toString() {
    return "SendRawTransactionConditionalParameter{"
        + "fromBlock="
        + fromBlock
        + ", toBlock="
        + toBlock
        + ", fromAddress="
        + knownAccounts
        + ", timestampMin="
        + timestampMin
        + ", timestampMax="
        + timestampMax
        + '}';
  }
}
