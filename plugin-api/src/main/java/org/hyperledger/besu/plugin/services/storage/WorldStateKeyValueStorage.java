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
package org.hyperledger.besu.plugin.services.storage;

import java.util.Collection;

import org.apache.tuweni.bytes.Bytes32;

/**
 * Defines the storage interface for Ethereum world state data backed by a key-value store.
 *
 * <p>The world state represents the complete mapping of account addresses to account state
 * (balance, nonce, code, and storage) at a given block. This interface abstracts over the
 * underlying storage format, supporting both the legacy {@link DataStorageFormat#FOREST FOREST}
 * format (which retains all historical trie nodes) and the more space-efficient {@link
 * DataStorageFormat#BONSAI BONSAI} format (which stores a single trie and uses trie logs to
 * reconstruct historical states).
 *
 * <p>Writes are performed through a transaction-scoped {@link Updater} obtained via {@link
 * #updater()}, which must be {@link Updater#commit() committed} to persist changes. Listeners can
 * be registered via implementations to observe trie node insertions through the {@link
 * NodesAddedListener} callback.
 */
public interface WorldStateKeyValueStorage {

  /**
   * Returns the storage format used by this world state storage.
   *
   * <p>The format determines how trie data is organised on disk. {@link DataStorageFormat#FOREST}
   * retains every historical trie node, while {@link DataStorageFormat#BONSAI} keeps only the
   * current state and reconstructs history from trie logs.
   *
   * @return the {@link DataStorageFormat} of the underlying storage
   */
  DataStorageFormat getDataStorageFormat();

  /**
   * Creates and returns a new {@link Updater} for batching write operations against this storage.
   *
   * <p>The caller is responsible for invoking {@link Updater#commit()} to atomically persist all
   * staged changes. Uncommitted updaters are discarded without side effects.
   *
   * @return a new {@link Updater} scoped to a single write transaction
   */
  Updater updater();

  /**
   * Removes all entries from this world state storage.
   *
   * <p>This is a destructive, irreversible operation intended for use during resynchronisation or
   * storage reset. After this call the storage is empty and any previously written state is lost.
   */
  void clear();

  /**
   * Callback interface for observing trie node insertions into world state storage.
   *
   * <p>Implementations can register a {@code NodesAddedListener} with the underlying storage to
   * receive notifications whenever a batch of trie nodes is persisted. This is useful for cache
   * invalidation, metrics collection, or triggering downstream processing.
   */
  interface NodesAddedListener {

    /**
     * Invoked after one or more trie nodes have been added to the world state storage.
     *
     * @param nodeHash the collection of {@link Bytes32} hashes identifying the nodes that were
     *     added; never {@code null} but may be empty
     */
    void onNodesAdded(Collection<Bytes32> nodeHash);
  }

  /**
   * A write transaction for batching mutations to world state storage.
   *
   * <p>An {@code Updater} is obtained via {@link WorldStateKeyValueStorage#updater()} and
   * accumulates put and delete operations in memory. Changes are not visible to readers until
   * {@link #commit()} is called.
   */
  interface Updater {

    /**
     * Atomically persists all staged write operations to the underlying storage.
     *
     * <p>After this method returns, all changes accumulated in this updater are durable and visible
     * to subsequent reads. The updater should not be used after committing.
     */
    void commit();
  }
}
