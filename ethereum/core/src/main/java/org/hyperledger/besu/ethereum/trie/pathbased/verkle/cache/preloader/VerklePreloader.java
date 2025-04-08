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
package org.hyperledger.besu.ethereum.trie.pathbased.verkle.cache.preloader;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;

import java.util.concurrent.CompletableFuture;

import org.apache.tuweni.bytes.Bytes;

public record VerklePreloader(StemPreloader stemPreloader) {

  /**
   * Asynchronously preloads stems and trie nodes for a given account address. This method is
   * designed to optimize the access to account-related stems and trie nodes by caching them ahead
   * of time, thus reducing the computational overhead during state root computation.
   *
   * @param account the address of the account for which stems are to be preloaded
   */
  public void preLoadAccount(final Address account) {
    CompletableFuture.runAsync(() -> stemPreloader.preloadAccountStem(account));
  }

  /**
   * Asynchronously preloads stems and trie nodes for a specific storage slot associated with an
   * account. This method enhances the efficiency of accessing storage-related stems and trie nodes
   * by ensuring they are cached in advance, thereby facilitating faster state root computation.
   *
   * @param account the address of the account associated with the storage slot
   * @param slotKey the key of the storage slot for which stems are to be preloaded
   */
  public void preLoadStorageSlot(final Address account, final StorageSlotKey slotKey) {
    CompletableFuture.runAsync(() -> stemPreloader.preloadSlotStems(account, slotKey));
  }

  /**
   * Asynchronously preloads stems and trie nodes for the code associated with an account.
   *
   * @param account the address of the account associated with the code
   * @param code the smart contract code for which stems are to be preloaded
   */
  public void preLoadCode(final Address account, final Bytes code) {
    CompletableFuture.runAsync(() -> stemPreloader.preloadCodeChunckStems(account, code));
  }

  /** Reset all the preloader caches */
  public void reset() {
    stemPreloader.reset();
  }
}
