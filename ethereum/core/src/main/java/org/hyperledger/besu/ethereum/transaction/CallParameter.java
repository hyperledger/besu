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
package org.hyperledger.besu.ethereum.transaction;

import org.hyperledger.besu.datatypes.AccessListEntry;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.json.HexStringDeserializer;
import org.hyperledger.besu.ethereum.core.json.OptionalGasDeserializer;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.tuweni.bytes.Bytes;
import org.immutables.value.Value;

// Represents parameters for eth_call and eth_estimateGas JSON-RPC methods.
@Value.Immutable
@JsonDeserialize(as = ImmutableCallParameter.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class CallParameter {
  public abstract Optional<BigInteger> getChainId();

  public abstract Optional<Address> getSender();

  public abstract Optional<Address> getTo();

  @JsonDeserialize(using = OptionalGasDeserializer.class)
  public abstract OptionalLong getGas();

  public abstract Optional<Wei> getMaxPriorityFeePerGas();

  public abstract Optional<Wei> getMaxFeePerGas();

  public abstract Optional<Wei> getMaxFeePerBlobGas();

  public abstract Optional<Wei> getGasPrice();

  public abstract Optional<Wei> getValue();

  public abstract Optional<List<AccessListEntry>> getAccessList();

  public abstract Optional<List<VersionedHash>> getBlobVersionedHashes();

  public abstract OptionalLong getNonce();

  public abstract Optional<Boolean> getStrict();

  /**
   * 'input' is mutually exclusive with 'data', so it needs special handling. This method is only
   * used to deserialize the 'input' field, always use getPayload() to get the value.
   */
  @JsonDeserialize(contentUsing = HexStringDeserializer.class)
  protected abstract Optional<Bytes> getInput();

  /**
   * 'data' is mutually exclusive with 'input', so it needs special handling. This method is only
   * used to deserialize the 'data' field, always use getPayload() to get the value.
   */
  @JsonDeserialize(contentUsing = HexStringDeserializer.class)
  protected abstract Optional<Bytes> getData();

  @Value.Check
  protected void check() {
    if (getInput().isPresent() && getData().isPresent() && !getInput().equals(getData())) {
      throw new IllegalArgumentException("Only one of 'input' or 'data' should be provided");
    }
  }

  /**
   * Returns either the 'input' or 'data' field, depending on which is present, or empty if neither
   * is present.
   *
   * @return the payload, or empty if none is present.
   */
  @Value.Derived
  public Optional<Bytes> getPayload() {
    return getInput().or(this::getData);
  }

  public static CallParameter fromTransaction(final Transaction tx) {
    final var builder =
        ImmutableCallParameter.builder()
            .chainId(tx.getChainId())
            .sender(tx.getSender())
            .gas(tx.getGasLimit())
            .value(tx.getValue())
            .input(tx.getPayload())
            .nonce(tx.getNonce());

    tx.getTo().ifPresent(builder::to);
    tx.getGasPrice().ifPresent(builder::gasPrice);
    tx.getMaxPriorityFeePerGas().ifPresent(builder::maxPriorityFeePerGas);
    tx.getMaxFeePerGas().ifPresent(builder::maxFeePerGas);

    tx.getAccessList().ifPresent(builder::accessList);

    tx.getMaxFeePerBlobGas().ifPresent(builder::maxFeePerBlobGas);
    tx.getVersionedHashes().ifPresent(builder::blobVersionedHashes);
    return builder.build();
  }

  public static CallParameter fromTransaction(final org.hyperledger.besu.datatypes.Transaction tx) {
    final var builder =
        ImmutableCallParameter.builder()
            .chainId(tx.getChainId())
            .sender(tx.getSender())
            .gas(tx.getGasLimit())
            .value(Wei.fromQuantity(tx.getValue()))
            .input(tx.getPayload())
            .nonce(tx.getNonce());

    tx.getTo().ifPresent(builder::to);
    tx.getGasPrice().map(Wei::fromQuantity).ifPresent(builder::gasPrice);

    tx.getMaxPriorityFeePerGas().map(Wei::fromQuantity).ifPresent(builder::maxPriorityFeePerGas);
    tx.getMaxFeePerGas().map(Wei::fromQuantity).ifPresent(builder::maxFeePerGas);

    tx.getAccessList().ifPresent(builder::accessList);
    tx.getMaxFeePerBlobGas().map(Wei::fromQuantity).ifPresent(builder::maxFeePerBlobGas);
    tx.getVersionedHashes().ifPresent(builder::blobVersionedHashes);
    return builder.build();
  }
}
