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
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

public class TransactionCollisionDetector {

  /**
   * Determines if a transaction has a collision based on the addresses it touches. A collision
   * occurs if the transaction touches the mining beneficiary address or if there are common
   * addresses touched by both the transaction and other transactions within the same block.
   *
   * @param transaction The transaction to check for collisions.
   * @param miningBeneficiary The address of the mining beneficiary.
   * @param parallelizedTransactionContext The context containing the accumulator for the
   *     transaction.
   * @param blockAccumulator The accumulator for the block.
   * @return true if there is a collision; false otherwise.
   */
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
    final Iterator<Address> it = addressesTouchedByTransaction.iterator();
    boolean commonAddressFound = false;
    while (it.hasNext() && !commonAddressFound) {
      if (addressesTouchedByBlock.contains(it.next())) {
        commonAddressFound = true;
      }
    }
    return commonAddressFound;
  }

  /**
   * Retrieves the set of addresses that were touched by a transaction. This includes the sender and
   * recipient of the transaction, as well as any addresses that were read from or written to by the
   * transaction's execution.
   *
   * @param transaction The transaction to analyze.
   * @param accumulator An optional accumulator containing state changes made by the transaction.
   * @return A set of addresses touched by the transaction.
   */
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

  /**
   * Retrieves the set of addresses that were touched by all transactions within a block. This
   * method filters out addresses that were only read and not modified.
   *
   * @param accumulator An optional accumulator containing state changes made by the block.
   * @return A set of addresses that were modified by the block's transactions.
   */
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
