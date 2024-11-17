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

import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedAccount;
import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedValue;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.DiffBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.preload.StorageConsumingMap;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
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
    final Set<AccountUpdateContext> addressesTouchedByBlock =
        getAddressesTouchedByBlock(Optional.of(blockAccumulator));
      for (final Address next : addressesTouchedByTransaction) {
          final Optional<AccountUpdateContext> maybeFound = addressesTouchedByBlock.stream().filter(accountUpdateContext -> accountUpdateContext.address.equals(next)).findFirst();
          if (maybeFound.isPresent()) {
              AccountUpdateContext accountUpdateContext = maybeFound.get();
              if (accountUpdateContext.isMainPartTheSame) {
                  final Set<StorageSlotKey> slotsTouchedByBlockAndByAddress = getSlotsTouchedByBlockAndByAddress(Optional.of(blockAccumulator), accountUpdateContext.address);
                  final Set<StorageSlotKey> slotsTouchedByTransactionAndByAddress = getSlotsTouchedByTransactionAndByAddress(Optional.of(parallelizedTransactionContext.transactionAccumulator()), accountUpdateContext.address);
                  for (final StorageSlotKey touchedByTransactionAndByAddress : slotsTouchedByTransactionAndByAddress) {
                      if (slotsTouchedByBlockAndByAddress.contains(touchedByTransactionAndByAddress)) {
                          return true;
                      }
                  }
              } else {
                  return true;
              }
          }
      }
    return false;
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
              .forEach((address, diffBasedValue) -> {
                  addresses.add(address);
              });
          addresses.addAll(diffBasedWorldStateUpdateAccumulator.getDeletedAccountAddresses());
        });

    return addresses;
  }

    private Set<StorageSlotKey> getSlotsTouchedByTransactionAndByAddress(
            final Optional<DiffBasedWorldStateUpdateAccumulator<?>> accumulator,
            final Address address) {
        HashSet<StorageSlotKey> slots = new HashSet<>();
        accumulator.ifPresent(
                diffBasedWorldStateUpdateAccumulator -> {
                    final StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>> map = diffBasedWorldStateUpdateAccumulator
                            .getStorageToUpdate()
                            .get(address);
                    if(map!=null){
                        map.forEach((storageSlotKey, slot) -> slots.add(storageSlotKey));
                    }
                });
        return slots;
    }

  /**
   * Retrieves the set of addresses that were touched by all transactions within a block. This
   * method filters out addresses that were only read and not modified.
   *
   * @param accumulator An optional accumulator containing state changes made by the block.
   * @return A set of addresses that were modified by the block's transactions.
   */
  private Set<AccountUpdateContext> getAddressesTouchedByBlock(
      final Optional<DiffBasedWorldStateUpdateAccumulator<?>> accumulator) {
    HashSet<AccountUpdateContext> addresses = new HashSet<>();
    accumulator.ifPresent(
        diffBasedWorldStateUpdateAccumulator -> {
          diffBasedWorldStateUpdateAccumulator
              .getAccountsToUpdate()
              .forEach(
                  (address, diffBasedValue) -> {
                    if (!diffBasedValue.isUnchanged()) {
                      addresses.add(new AccountUpdateContext(address, isMainPartTheSame(diffBasedValue.getPrior(), diffBasedValue.getUpdated())));
                    }
                  });
            diffBasedWorldStateUpdateAccumulator.getDeletedAccountAddresses().forEach(address -> {
                addresses.add(new AccountUpdateContext(address, false));
            });
          ;
        });
    return addresses;
  }

    private Set<StorageSlotKey> getSlotsTouchedByBlockAndByAddress(
            final Optional<DiffBasedWorldStateUpdateAccumulator<?>> accumulator, final Address address) {
        HashSet<StorageSlotKey> slots = new HashSet<>();
        accumulator.ifPresent(
                diffBasedWorldStateUpdateAccumulator -> {
                    final StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>> map = diffBasedWorldStateUpdateAccumulator
                            .getStorageToUpdate()
                            .get(address);
                    if(map!=null){
                        map.forEach((storageSlotKey, slot) -> {
                                        if (!slot.isUnchanged()) {
                                            slots.add(storageSlotKey);
                                        }
                        });
                    }
                });
        return slots;
    }

    private boolean isMainPartTheSame(final DiffBasedAccount prior, final DiffBasedAccount next){
      return prior.getBalance().equals(next.getBalance()) && prior.getCodeHash().equals(next.getCodeHash())
              && prior.getNonce()==next.getNonce();
    }

    static class AccountUpdateContext {
      private final Address address;
      private  final boolean isMainPartTheSame;

        public AccountUpdateContext(final Address address, final boolean isMainPartTheSame) {
            this.address = address;
            this.isMainPartTheSame = isMainPartTheSame;
        }


        public AccountUpdateContext(final Address address) {
            this.address = address;
            this.isMainPartTheSame  = true;
        }

        public Address getAddress() {
            return address;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AccountUpdateContext that = (AccountUpdateContext) o;
            return Objects.equals(address, that.address);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(address);
        }
    }

}
