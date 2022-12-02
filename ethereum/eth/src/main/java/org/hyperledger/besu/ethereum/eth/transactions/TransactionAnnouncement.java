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
package org.hyperledger.besu.ethereum.eth.transactions;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class TransactionAnnouncement {
  private final Hash hash;
  private final Optional<TransactionType> type;
  private final Optional<Integer> size;

  public TransactionAnnouncement(final Hash hash) {
    this.hash = hash;
    this.type = Optional.empty();
    this.size = Optional.empty();
  }

  public TransactionAnnouncement(final Transaction transaction) {
    this(transaction.getHash(), transaction.getType(), transaction.calculateSize());
  }

  public TransactionAnnouncement(final Hash hash, final TransactionType type, final Integer size) {
    this.hash = hash;
    this.type = Optional.ofNullable(type);
    this.size = Optional.ofNullable(size);
  }

  public Hash getHash() {
    return hash;
  }

  public Optional<TransactionType> getType() {
    return type;
  }

  public Optional<Integer> getSize() {
    return size;
  }

  public static List<TransactionAnnouncement> create(
      final List<TransactionType> types, final List<Integer> sizes, final List<Hash> hashes) {
    // Check if lists have the same size
    if (!(types.size() == hashes.size() && hashes.size() == sizes.size())) {
      throw new IllegalArgumentException(
          "Hashes, sizes and types must have the same number of elements");
    }
    final List<TransactionAnnouncement> transactions = new ArrayList<>(hashes.size());
    for (int i = 0; i < hashes.size(); i++) {
      transactions.add(new TransactionAnnouncement(hashes.get(i), types.get(i), sizes.get(i)));
    }
    return transactions;
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
