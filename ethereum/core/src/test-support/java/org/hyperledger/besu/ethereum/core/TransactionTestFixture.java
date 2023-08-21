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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.datatypes.AccessListEntry;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BlobsWithCommitments;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class TransactionTestFixture {

  private TransactionType transactionType = TransactionType.FRONTIER;

  private long nonce = 0;

  private Optional<Wei> gasPrice = Optional.empty();

  private long gasLimit = 5000;

  private Optional<Address> to = Optional.empty();
  private Address sender = Address.fromHexString(String.format("%020x", 1));

  private Wei value = Wei.of(4);

  private Bytes payload = Bytes.EMPTY;

  private Optional<BigInteger> chainId = Optional.of(BigInteger.valueOf(1337));

  private Optional<Wei> maxPriorityFeePerGas = Optional.empty();
  private Optional<Wei> maxFeePerGas = Optional.empty();
  private Optional<Wei> maxFeePerBlobGas = Optional.empty();

  private Optional<List<AccessListEntry>> accessListEntries = Optional.empty();
  private Optional<List<VersionedHash>> versionedHashes = Optional.empty();

  private Optional<BlobsWithCommitments> blobs = Optional.empty();
  private Optional<BigInteger> v = Optional.empty();

  public Transaction createTransaction(final KeyPair keys) {
    final Transaction.Builder builder = Transaction.builder();
    builder
        .type(transactionType)
        .gasLimit(gasLimit)
        .nonce(nonce)
        .payload(payload)
        .value(value)
        .sender(sender);

    switch (transactionType) {
      case FRONTIER:
        builder.gasPrice(gasPrice.orElse(Wei.of(5000)));
        break;
      case ACCESS_LIST:
        builder.gasPrice(gasPrice.orElse(Wei.of(5000)));
        builder.accessList(accessListEntries.orElse(List.of()));
        break;
      case EIP1559:
        builder.maxPriorityFeePerGas(maxPriorityFeePerGas.orElse(Wei.of(500)));
        builder.maxFeePerGas(maxFeePerGas.orElse(Wei.of(5000)));
        builder.accessList(accessListEntries.orElse(List.of()));
        break;
      case BLOB:
        builder.maxPriorityFeePerGas(maxPriorityFeePerGas.orElse(Wei.of(500)));
        builder.maxFeePerGas(maxFeePerGas.orElse(Wei.of(5000)));
        builder.accessList(accessListEntries.orElse(List.of()));
        builder.maxFeePerBlobGas(maxFeePerBlobGas.orElse(Wei.ONE));
        builder.versionedHashes(
            versionedHashes.orElse(List.of(VersionedHash.DEFAULT_VERSIONED_HASH)));
        blobs.ifPresent(
            bwc -> {
              builder.kzgBlobs(bwc.getKzgCommitments(), bwc.getBlobs(), bwc.getKzgProofs());
            });
        break;
    }

    to.ifPresent(builder::to);
    chainId.ifPresent(builder::chainId);
    v.ifPresent(builder::v);

    return builder.signAndBuild(keys);
  }

  public TransactionTestFixture type(final TransactionType transactionType) {
    this.transactionType = transactionType;
    return this;
  }

  public TransactionTestFixture nonce(final long nonce) {
    this.nonce = nonce;
    return this;
  }

  public TransactionTestFixture gasPrice(final Wei gasPrice) {
    this.gasPrice = Optional.ofNullable(gasPrice);
    return this;
  }

  public TransactionTestFixture gasLimit(final long gasLimit) {
    this.gasLimit = gasLimit;
    return this;
  }

  public TransactionTestFixture to(final Optional<Address> to) {
    this.to = to;
    return this;
  }

  public TransactionTestFixture sender(final Address sender) {
    this.sender = sender;
    return this;
  }

  public TransactionTestFixture value(final Wei value) {
    this.value = value;
    return this;
  }

  public TransactionTestFixture payload(final Bytes payload) {
    this.payload = payload;
    return this;
  }

  public TransactionTestFixture chainId(final Optional<BigInteger> chainId) {
    this.chainId = chainId;
    return this;
  }

  public TransactionTestFixture maxPriorityFeePerGas(final Optional<Wei> maxPriorityFeePerGas) {
    this.maxPriorityFeePerGas = maxPriorityFeePerGas;
    return this;
  }

  public TransactionTestFixture maxFeePerGas(final Optional<Wei> maxFeePerGas) {
    this.maxFeePerGas = maxFeePerGas;
    return this;
  }

  public TransactionTestFixture maxFeePerBlobGas(final Optional<Wei> maxFeePerBlobGas) {
    this.maxFeePerBlobGas = maxFeePerBlobGas;
    return this;
  }

  public TransactionTestFixture accessList(final List<AccessListEntry> accessListEntries) {
    this.accessListEntries = Optional.ofNullable(accessListEntries);
    return this;
  }

  public TransactionTestFixture versionedHashes(
      final Optional<List<VersionedHash>> versionedHashes) {
    this.versionedHashes = versionedHashes;
    return this;
  }

  public TransactionTestFixture v(final Optional<BigInteger> v) {
    this.v = v;
    return this;
  }

  public TransactionTestFixture blobsWithCommitments(final Optional<BlobsWithCommitments> blobs) {
    this.blobs = blobs;
    return this;
  }
}
