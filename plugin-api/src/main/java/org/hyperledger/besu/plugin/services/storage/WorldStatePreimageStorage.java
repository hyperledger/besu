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
package org.hyperledger.besu.plugin.services.storage;

import org.hyperledger.besu.datatypes.Address;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * WorldStatePreimageStorage defines the contract for storing and retrieving preimages used in the
 * Ethereum world state. It allows fetching preimages for storage and account trie keys as well as
 * supporting transactional updates through an Updater.
 */
public interface WorldStatePreimageStorage {

  /**
   * Retrieves the preimage of a storage trie key.
   *
   * @param trieKey the trie key for which the preimage is requested
   * @return an {@link Optional} containing the preimage as {@link UInt256} if present, otherwise
   *     empty
   */
  Optional<UInt256> getStorageTrieKeyPreimage(Bytes32 trieKey);

  /**
   * Retrieves the preimage of an account trie key.
   *
   * @param trieKey the trie key for which the account preimage is requested
   * @return an {@link Optional} containing the preimage as {@link Address} if present, otherwise
   *     empty
   */
  Optional<Address> getAccountTrieKeyPreimage(Bytes32 trieKey);

  /**
   * Creates and returns an {@link Updater} for updating this world state preimage storage.
   *
   * @return a new updater instance
   */
  Updater updater();

  /**
   * Updater interface for staging preimage entries before committing them to the
   * WorldStatePreimageStorage.
   *
   * <p>Allows for atomic updates of trie key preimages for both account and storage tries,
   * supporting commit and rollback operations.
   */
  interface Updater {
    /**
     * Puts a storage trie key preimage into the storage.
     *
     * @param trieKey the trie key
     * @param preimage the corresponding preimage value
     * @return this updater instance
     */
    Updater putStorageTrieKeyPreimage(Bytes32 trieKey, UInt256 preimage);

    /**
     * Puts an account trie key preimage into the storage.
     *
     * @param trieKey the trie key
     * @param preimage the corresponding account preimage value
     * @return this updater instance
     */
    Updater putAccountTrieKeyPreimage(Bytes32 trieKey, Address preimage);

    /** Commits the pending updates to the preimage storage. */
    void commit();

    /** Rolls back any uncommitted updates. */
    void rollback();
  }
}
