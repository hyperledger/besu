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
package org.hyperledger.besu.ethereum.transaction;

import org.hyperledger.besu.datatypes.AccessListEntry;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

// Represents parameters for eth_call and eth_estimateGas JSON-RPC methods.
public class CallParameter {
  private final Optional<BigInteger> chainId;

  private final Address from;

  private final Address to;

  private final long gasLimit;

  private final Optional<Wei> maxPriorityFeePerGas;

  private final Optional<Wei> maxFeePerGas;
  private final Optional<Wei> maxFeePerBlobGas;

  private final Wei gasPrice;

  private final Wei value;

  private final Bytes payload;

  private final Optional<List<AccessListEntry>> accessList;
  private final Optional<List<VersionedHash>> blobVersionedHashes;
  private final Optional<Long> nonce;

  public CallParameter(
      final Address from,
      final Address to,
      final long gasLimit,
      final Wei gasPrice,
      final Wei value,
      final Bytes payload) {
    this.chainId = Optional.empty();
    this.from = from;
    this.to = to;
    this.gasLimit = gasLimit;
    this.accessList = Optional.empty();
    this.maxPriorityFeePerGas = Optional.empty();
    this.maxFeePerGas = Optional.empty();
    this.gasPrice = gasPrice;
    this.value = value;
    this.payload = payload;
    this.maxFeePerBlobGas = Optional.empty();
    this.blobVersionedHashes = Optional.empty();
    this.nonce = Optional.empty();
  }

  public CallParameter(
      final Address from,
      final Address to,
      final long gasLimit,
      final Wei gasPrice,
      final Wei value,
      final Bytes payload,
      final Optional<Long> nonce) {
    this.chainId = Optional.empty();
    this.from = from;
    this.to = to;
    this.gasLimit = gasLimit;
    this.accessList = Optional.empty();
    this.maxPriorityFeePerGas = Optional.empty();
    this.maxFeePerGas = Optional.empty();
    this.gasPrice = gasPrice;
    this.value = value;
    this.payload = payload;
    this.maxFeePerBlobGas = Optional.empty();
    this.blobVersionedHashes = Optional.empty();
    this.nonce = nonce;
  }

  public CallParameter(
      final Address from,
      final Address to,
      final long gasLimit,
      final Wei gasPrice,
      final Optional<Wei> maxPriorityFeePerGas,
      final Optional<Wei> maxFeePerGas,
      final Wei value,
      final Bytes payload,
      final Optional<List<AccessListEntry>> accessList,
      final Optional<Long> nonce) {
    this.chainId = Optional.empty();
    this.from = from;
    this.to = to;
    this.gasLimit = gasLimit;
    this.maxPriorityFeePerGas = maxPriorityFeePerGas;
    this.maxFeePerGas = maxFeePerGas;
    this.gasPrice = gasPrice;
    this.value = value;
    this.payload = payload;
    this.accessList = accessList;
    this.maxFeePerBlobGas = Optional.empty();
    this.blobVersionedHashes = Optional.empty();
    this.nonce = nonce;
  }

  public CallParameter(
      final Optional<BigInteger> chainId,
      final Address from,
      final Address to,
      final long gasLimit,
      final Wei gasPrice,
      final Optional<Wei> maxPriorityFeePerGas,
      final Optional<Wei> maxFeePerGas,
      final Wei value,
      final Bytes payload,
      final Optional<List<AccessListEntry>> accessList,
      final Optional<Wei> maxFeePerBlobGas,
      final Optional<List<VersionedHash>> blobVersionedHashes,
      final Optional<Long> nonce) {
    this.chainId = chainId;
    this.from = from;
    this.to = to;
    this.gasLimit = gasLimit;
    this.maxPriorityFeePerGas = maxPriorityFeePerGas;
    this.maxFeePerGas = maxFeePerGas;
    this.gasPrice = gasPrice;
    this.value = value;
    this.payload = payload;
    this.accessList = accessList;
    this.maxFeePerBlobGas = maxFeePerBlobGas;
    this.blobVersionedHashes = blobVersionedHashes;
    this.nonce = nonce;
  }

  public Optional<BigInteger> getChainId() {
    return chainId;
  }

  public Address getFrom() {
    return from;
  }

  public Address getTo() {
    return to;
  }

  public long getGasLimit() {
    return gasLimit;
  }

  public Wei getGasPrice() {
    return gasPrice;
  }

  public Optional<Wei> getMaxPriorityFeePerGas() {
    return maxPriorityFeePerGas;
  }

  public Optional<Wei> getMaxFeePerGas() {
    return maxFeePerGas;
  }

  public Wei getValue() {
    return value;
  }

  public Bytes getPayload() {
    return payload;
  }

  public Optional<List<AccessListEntry>> getAccessList() {
    return accessList;
  }

  public Optional<Wei> getMaxFeePerBlobGas() {
    return maxFeePerBlobGas;
  }

  public Optional<List<VersionedHash>> getBlobVersionedHashes() {
    return blobVersionedHashes;
  }

  public Optional<Long> getNonce() {
    return nonce;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CallParameter that = (CallParameter) o;
    return gasLimit == that.gasLimit
        && chainId.equals(that.chainId)
        && Objects.equals(from, that.from)
        && Objects.equals(to, that.to)
        && Objects.equals(gasPrice, that.gasPrice)
        && Objects.equals(maxPriorityFeePerGas, that.maxPriorityFeePerGas)
        && Objects.equals(maxFeePerGas, that.maxFeePerGas)
        && Objects.equals(value, that.value)
        && Objects.equals(payload, that.payload)
        && Objects.equals(accessList, that.accessList)
        && Objects.equals(maxFeePerBlobGas, that.maxFeePerBlobGas)
        && Objects.equals(blobVersionedHashes, that.blobVersionedHashes)
        && Objects.equals(nonce, that.nonce);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        chainId,
        from,
        to,
        gasLimit,
        gasPrice,
        maxPriorityFeePerGas,
        maxFeePerGas,
        value,
        payload,
        accessList,
        maxFeePerBlobGas,
        blobVersionedHashes,
        nonce);
  }

  @Override
  public String toString() {
    return "CallParameter{"
        + "chainId="
        + chainId
        + ", from="
        + from
        + ", to="
        + to
        + ", gasLimit="
        + gasLimit
        + ", maxPriorityFeePerGas="
        + maxPriorityFeePerGas.map(Wei::toHumanReadableString).orElse("N/A")
        + ", maxFeePerGas="
        + maxFeePerGas.map(Wei::toHumanReadableString).orElse("N/A")
        + ", maxFeePerBlobGas="
        + maxFeePerBlobGas.map(Wei::toHumanReadableString).orElse("N/A")
        + ", gasPrice="
        + (gasPrice != null ? gasPrice.toHumanReadableString() : "N/A")
        + ", value="
        + (value != null ? value.toHumanReadableString() : "N/A")
        + ", payloadSize="
        + (payload != null ? payload.size() : "null")
        + ", accessListSize="
        + accessList.map(List::size)
        + ", blobVersionedHashesSize="
        + blobVersionedHashes.map(List::size)
        + ", nonce="
        + nonce.map(Object::toString).orElse("N/A")
        + '}';
  }

  public static CallParameter fromTransaction(final Transaction tx) {
    return new CallParameter(
        tx.getChainId(),
        tx.getSender(),
        tx.getTo().orElse(null),
        tx.getGasLimit(),
        tx.getGasPrice().orElse(Wei.ZERO),
        tx.getMaxPriorityFeePerGas(),
        tx.getMaxFeePerGas(),
        tx.getValue(),
        tx.getPayload(),
        tx.getAccessList(),
        tx.getMaxFeePerBlobGas(),
        tx.getVersionedHashes(),
        Optional.of(tx.getNonce()));
  }

  public static CallParameter fromTransaction(final org.hyperledger.besu.datatypes.Transaction tx) {
    return new CallParameter(
        tx.getChainId(),
        tx.getSender(),
        tx.getTo().orElse(null),
        tx.getGasLimit(),
        tx.getGasPrice().map(Wei::fromQuantity).orElse(Wei.ZERO),
        tx.getMaxPriorityFeePerGas().map(Wei::fromQuantity),
        tx.getMaxFeePerGas().map(Wei::fromQuantity),
        Wei.fromQuantity(tx.getValue()),
        tx.getPayload(),
        tx.getAccessList(),
        tx.getMaxFeePerBlobGas().map(Wei::fromQuantity),
        tx.getVersionedHashes(),
        Optional.of(tx.getNonce()));
  }
}
