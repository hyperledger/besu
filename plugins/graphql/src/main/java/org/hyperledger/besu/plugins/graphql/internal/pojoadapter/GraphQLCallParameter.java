/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.plugins.graphql.internal.pojoadapter;

import org.hyperledger.besu.datatypes.AccessListEntry;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.CallParameter;
import org.hyperledger.besu.datatypes.CodeDelegation;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.apache.tuweni.bytes.Bytes;

/**
 * The call parameters of the {@code call} and {@code estimateGas} fields: only the fields of the
 * GraphQL {@code CallData} input are set, everything else keeps the simulator defaults.
 *
 * @param sender the sender
 * @param to the recipient
 * @param gas the gas limit
 * @param gasPrice the gas price
 * @param value the value
 * @param maxPriorityFeePerGas the max priority fee per gas
 * @param maxFeePerGas the max fee per gas
 * @param payload the input data
 */
record GraphQLCallParameter(
    Optional<Address> sender,
    Optional<Address> to,
    OptionalLong gas,
    Optional<Wei> gasPrice,
    Optional<Wei> value,
    Optional<Wei> maxPriorityFeePerGas,
    Optional<Wei> maxFeePerGas,
    Optional<Bytes> payload)
    implements CallParameter {

  @Override
  public Optional<BigInteger> getChainId() {
    return Optional.empty();
  }

  @Override
  public Optional<Address> getSender() {
    return sender;
  }

  @Override
  public Optional<Address> getTo() {
    return to;
  }

  @Override
  public OptionalLong getGas() {
    return gas;
  }

  @Override
  public Optional<Wei> getMaxPriorityFeePerGas() {
    return maxPriorityFeePerGas;
  }

  @Override
  public Optional<Wei> getMaxFeePerGas() {
    return maxFeePerGas;
  }

  @Override
  public Optional<Wei> getMaxFeePerBlobGas() {
    return Optional.empty();
  }

  @Override
  public Optional<Wei> getGasPrice() {
    return gasPrice;
  }

  @Override
  public Optional<Wei> getValue() {
    return value;
  }

  @Override
  public Optional<List<AccessListEntry>> getAccessList() {
    return Optional.empty();
  }

  @Override
  public Optional<List<VersionedHash>> getBlobVersionedHashes() {
    return Optional.empty();
  }

  @Override
  public OptionalLong getNonce() {
    return OptionalLong.empty();
  }

  @Override
  public Optional<Boolean> getStrict() {
    return Optional.empty();
  }

  @Override
  public Optional<Bytes> getPayload() {
    return payload;
  }

  @Override
  public List<CodeDelegation> getCodeDelegationAuthorizations() {
    return List.of();
  }
}
