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
package org.hyperledger.besu.ethereum.mainnet.parallelization;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.DiffBasedWorldStateUpdateAccumulator;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class TransactionCollisionDetector {

  public boolean hasCollision(
      final Transaction transaction,
      final Address miningBeneficiary,
      final ParallelizedTransactionContext parallelizedTransactionContext,
      final DiffBasedWorldStateUpdateAccumulator<?> blockAccumulator) {
    final Set<Address> addressesTouchedByTransaction =
        getAddressesTouchedByTransaction(
            transaction, Optional.of(parallelizedTransactionContext.transactionAccumulator()));
    if (addressesTouchedByTransaction.contains(miningBeneficiary)) {
      return true;
    }
    final Set<Address> addressesTouchedByBlock =
        getAddressesTouchedByBlock(Optional.of(blockAccumulator));
    final Set<Address> commonAddresses = new HashSet<>(addressesTouchedByTransaction);
    commonAddresses.retainAll(addressesTouchedByBlock);
    return !commonAddresses.isEmpty();
  }

  public Set<Address> getAddressesTouchedByTransaction(
      final Transaction transaction,
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
              .forEach((address, diffBasedValue) -> addresses.add(address));
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
}
