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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.DiffBasedWorldStateUpdateAccumulator;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TransactionConflictChecker {

  private final Map<Long, DiffBasedWorldStateUpdateAccumulator<?>>
      accumulatorByParallelizedTransaction = new ConcurrentHashMap<>();

  private final Map<Long, TransactionProcessingResult> resultByParallelizedTransaction =
      new ConcurrentHashMap<>();

  public void saveParallelizedTransactionProcessingResult(
      final TransactionWithLocation transaction,
      final DiffBasedWorldStateUpdateAccumulator<?> accumulator,
      final TransactionProcessingResult result) {
    accumulatorByParallelizedTransaction.put(transaction.getLocation(), accumulator);
    resultByParallelizedTransaction.put(transaction.getLocation(), result);
  }

  public boolean checkConflicts(
      final Address producer,
      final TransactionWithLocation transaction,
      final DiffBasedWorldStateUpdateAccumulator<?> trxAccumulator,
      final DiffBasedWorldStateUpdateAccumulator<?> blockAccumulator) {
    final Set<Address> addressesTouchedByTransaction =
        getAddressesTouchedByTransaction(transaction, Optional.of(trxAccumulator));
    if (addressesTouchedByTransaction.contains(producer)) {
      return true;
    }
    final Set<Address> addressesTouchedByBlock =
        getAddressesTouchedByBlock(Optional.of(blockAccumulator));
    final Set<Address> commonAddresses = new HashSet<>(addressesTouchedByTransaction);
    commonAddresses.retainAll(addressesTouchedByBlock);
    return !commonAddresses.isEmpty();
  }

  private Set<Address> getAddressesTouchedByTransaction(
      final TransactionWithLocation transaction,
      final Optional<DiffBasedWorldStateUpdateAccumulator<?>> accumulator) {
    HashSet<Address> addresses = new HashSet<>();
    addresses.add(transaction.getSender());
    if (transaction.getTo().isPresent()) {
      addresses.add(transaction.getTo().get());
    }
    accumulator.ifPresent(
        diffBasedWorldStateUpdateAccumulator -> {
          diffBasedWorldStateUpdateAccumulator
              .getAccountsToUpdate()
              .forEach(
                  (address, diffBasedValue) -> {
                    addresses.add(address);
                    /*System.out.println("update "+diffBasedValue.getPrior()+" "+diffBasedValue.getUpdated());
                    diffBasedWorldStateUpdateAccumulator.getTouchedAccounts().stream().forEach(account -> {
                      System.out.println(account);
                    });*/
                  });
          addresses.addAll(diffBasedWorldStateUpdateAccumulator.getDeletedAccountAddresses());
        });
    return addresses;
  }

  private Set<Address> getAddressesTouchedByBlock(
      final Optional<DiffBasedWorldStateUpdateAccumulator<?>> accumulator) {
    HashSet<Address> addresses = new HashSet<>();
    accumulator.ifPresent(
        diffBasedWorldStateUpdateAccumulator -> {
          diffBasedWorldStateUpdateAccumulator
              .getAccountsToUpdate()
              .forEach(
                  (address, diffBasedValue) -> {
                    if (!diffBasedValue.isUnchanged()) {
                      addresses.add(address);
                    }
                  });
          addresses.addAll(diffBasedWorldStateUpdateAccumulator.getDeletedAccountAddresses());
        });
    return addresses;
  }

  public Map<Long, DiffBasedWorldStateUpdateAccumulator<?>> getAccumulatorByTransaction() {
    return accumulatorByParallelizedTransaction;
  }

  public Map<Long, TransactionProcessingResult> getResultByTransaction() {
    return resultByParallelizedTransaction;
  }

  public static final class TransactionWithLocation {
    private final long location;
    private final Transaction transaction;

    public TransactionWithLocation(final long location, final Transaction transaction) {
      this.location = location;
      this.transaction = transaction;
    }

    public long getLocation() {
      return location;
    }

    public Transaction transaction() {
      return transaction;
    }

    public Address getSender() {
      return transaction.getSender();
    }

    public Optional<Address> getTo() {
      return transaction.getTo();
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TransactionWithLocation that = (TransactionWithLocation) o;
      return location == that.location;
    }

    @Override
    public int hashCode() {
      return Objects.hash(location);
    }
  }
}
