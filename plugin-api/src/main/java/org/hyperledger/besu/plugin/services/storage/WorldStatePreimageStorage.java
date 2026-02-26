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
 * Stores and retrieves preimage mappings for the Ethereum world-state trie.
 *
 * <p>The Ethereum state trie keys values at positions derived by hashing the natural identifiers:
 * account addresses are hashed with keccak-256 to produce account trie keys, and storage slot
 * indices are hashed to produce storage trie keys. A <em>preimage</em> is the original, unhashed
 * value that maps to a given trie key. Retaining these mappings allows tooling and clients to
 * resolve a raw trie key back to its human-readable identifier without re-scanning the entire
 * state.
 *
 * <p>Reads are performed directly via {@link #getStorageTrieKeyPreimage(Bytes32)} and {@link
 * #getAccountTrieKeyPreimage(Bytes32)}. Writes are batched through a transaction-scoped {@link
 * Updater} obtained from {@link #updater()}, which must be {@link Updater#commit() committed} or
 * {@link Updater#rollback() rolled back} to finalise or discard the changes.
 */
public interface WorldStatePreimageStorage {

  /**
   * Retrieves the storage slot index that was hashed to produce the given storage trie key.
   *
   * <p>Storage slot values in the trie are keyed by {@code keccak256(slot_index)}. This method
   * performs the reverse lookup, returning the original {@link UInt256} slot index for the supplied
   * 32-byte trie key, if it was previously recorded.
   *
   * @param trieKey the 32-byte keccak-256 hash used as the storage trie key; must not be {@code
   *     null}
   * @return an {@link Optional} containing the {@link UInt256} slot index preimage, or {@link
   *     Optional#empty()} if no mapping has been stored for this key
   */
  Optional<UInt256> getStorageTrieKeyPreimage(Bytes32 trieKey);

  /**
   * Retrieves the account address that was hashed to produce the given account trie key.
   *
   * <p>Accounts in the state trie are keyed by {@code keccak256(address)}. This method performs the
   * reverse lookup, returning the original {@link Address} for the supplied 32-byte trie key, if it
   * was previously recorded.
   *
   * @param trieKey the 32-byte keccak-256 hash used as the account trie key; must not be {@code
   *     null}
   * @return an {@link Optional} containing the {@link Address} preimage, or {@link
   *     Optional#empty()} if no mapping has been stored for this key
   */
  Optional<Address> getAccountTrieKeyPreimage(Bytes32 trieKey);

  /**
   * Creates and returns a new {@link Updater} for batching preimage write operations.
   *
   * <p>The caller must invoke {@link Updater#commit()} to persist staged changes, or {@link
   * Updater#rollback()} to discard them. An updater should not be reused after either operation.
   *
   * @return a new {@link Updater} scoped to a single write transaction
   */
  Updater updater();

  /**
   * A write transaction for batching preimage insertions into {@link WorldStatePreimageStorage}.
   *
   * <p>Accumulated puts are held in memory and are not visible to readers until {@link #commit()}
   * is called. All staged changes can be abandoned without side effects by calling {@link
   * #rollback()}.
   */
  interface Updater {

    /**
     * Stages a mapping from a storage trie key to its original slot index preimage.
     *
     * <p>Records the association {@code trieKey → preimage} so that a subsequent call to {@link
     * WorldStatePreimageStorage#getStorageTrieKeyPreimage(Bytes32)} with the same {@code trieKey}
     * will return this {@code preimage} after the transaction is committed.
     *
     * @param trieKey the 32-byte keccak-256 hash of the slot index; must not be {@code null}
     * @param preimage the original {@link UInt256} storage slot index; must not be {@code null}
     * @return this updater, to allow method chaining
     */
    Updater putStorageTrieKeyPreimage(Bytes32 trieKey, UInt256 preimage);

    /**
     * Stages a mapping from an account trie key to its original address preimage.
     *
     * <p>Records the association {@code trieKey → preimage} so that a subsequent call to {@link
     * WorldStatePreimageStorage#getAccountTrieKeyPreimage(Bytes32)} with the same {@code trieKey}
     * will return this {@code preimage} after the transaction is committed.
     *
     * @param trieKey the 32-byte keccak-256 hash of the address; must not be {@code null}
     * @param preimage the original {@link Address}; must not be {@code null}
     * @return this updater, to allow method chaining
     */
    Updater putAccountTrieKeyPreimage(Bytes32 trieKey, Address preimage);

    /**
     * Atomically persists all staged preimage mappings to the underlying storage.
     *
     * <p>After this method returns, all mappings accumulated in this updater are durable and
     * visible to subsequent reads. The updater must not be used after committing.
     */
    void commit();

    /**
     * Discards all staged preimage mappings without writing to the underlying storage.
     *
     * <p>After this method returns, the transaction is abandoned and the storage is unchanged. The
     * updater must not be used after rolling back.
     */
    void rollback();
  }
}
