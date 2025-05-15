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

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.tuweni.bytes.Bytes;
import org.immutables.value.Value;

// Represents parameters for eth_call and eth_estimateGas JSON-RPC methods.
@Value.Immutable
@JsonDeserialize
public interface CallParameter {
  Optional<BigInteger> getChainId();

  Optional<Address> getSender();

  Optional<Address> getTo();

  OptionalLong getGasLimit();

  Optional<Wei> getMaxPriorityFeePerGas();

  Optional<Wei> getMaxFeePerGas();

  Optional<Wei> getMaxFeePerBlobGas();

  Optional<Wei> getGasPrice();

  Optional<Wei> getValue();

  Optional<Bytes> getPayload();

  Optional<List<AccessListEntry>> getAccessList();

  Optional<List<VersionedHash>> getBlobVersionedHashes();

  OptionalLong getNonce();

  Optional<Boolean> getStrict();

  static CallParameter fromTransaction(final Transaction tx) {
    final var builder =
        ImmutableCallParameter.builder()
            .chainId(tx.getChainId())
            .sender(tx.getSender())
            .gasLimit(tx.getGasLimit())
            .value(tx.getValue())
            .payload(tx.getPayload())
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

  static CallParameter fromTransaction(final org.hyperledger.besu.datatypes.Transaction tx) {
    final var builder =
        ImmutableCallParameter.builder()
            .chainId(tx.getChainId())
            .sender(tx.getSender())
            .gasLimit(tx.getGasLimit())
            .value(Wei.fromQuantity(tx.getValue()))
            .payload(tx.getPayload())
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
