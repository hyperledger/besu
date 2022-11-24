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
package org.hyperledger.besu.ethereum.eth.messages;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;

public class TransactionAnnouncement {
  private final Hash hash;
  private final TransactionType type;
  private final Integer size;

  public TransactionAnnouncement(final Hash hash) {
    this.hash = hash;
    this.type = null;
    this.size = null;
  }

  public TransactionAnnouncement(final Transaction transaction) {
    this(transaction.getHash(), transaction.getType(), transaction.calculateSize());
  }

  public TransactionAnnouncement(final Hash hash, final TransactionType type, final Integer size) {
    this.hash = hash;
    this.type = type;
    this.size = size;
  }

  public Hash getHash() {
    return hash;
  }

  public Optional<TransactionType> getType() {
    return Optional.ofNullable(type);
  }

  public Optional<Integer> getSize() {
    return Optional.ofNullable(size);
  }

  public static List<TransactionAnnouncement> create(
      final List<TransactionType> types, final List<Integer> sizes, final List<Hash> hashes) {
    final List<TransactionAnnouncement> transactions = new ArrayList<>();
    for (int i = 0; i < hashes.size(); i++) {
      transactions.add(new TransactionAnnouncement(hashes.get(i), types.get(i), sizes.get(i)));
    }
    return transactions;
  }

  public static Bytes encodeForEth66(final List<Hash> hashes) {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.writeList(hashes, (h, w) -> w.writeBytes(h));
    return out.encoded();
  }

  public static Bytes encodeForEth68(final List<Transaction> transactions) {
    final List<Integer> sizes = new ArrayList<>();
    final List<TransactionType> types = new ArrayList<>();
    final List<Hash> hashes = new ArrayList<>();
    transactions.forEach(
        transaction -> {
          types.add(transaction.getType());
          sizes.add(transaction.calculateSize());
          hashes.add(transaction.getHash());
        });
    return encodeForEth68(types, sizes, hashes);
  }

  public static Bytes encodeForEth68(
      final List<TransactionType> types, final List<Integer> sizes, final List<Hash> hashes) {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeList(types, (h, w) -> w.writeByte(h.getSerializedType()));
    out.writeList(sizes, (h, w) -> w.writeInt(h));
    out.writeList(hashes, (h, w) -> w.writeBytes(h));
    out.endList();
    return out.encoded();
  }

  public static List<Hash> toHashList(final Collection<TransactionAnnouncement> transactions) {
    return transactions.stream()
        .map(TransactionAnnouncement::getHash)
        .collect(Collectors.toUnmodifiableList());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TransactionAnnouncement that = (TransactionAnnouncement) o;
    return Objects.equals(size, that.size)
        && Objects.equals(type, that.type)
        && Objects.equals(hash, that.hash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(hash, size, type);
  }
}
