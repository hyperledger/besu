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
 * WorldStateKeyValueStorage defines the contract for interacting with the underlying key-value
 * storage used for world state data in Ethereum. This interface includes methods to get the storage
 * format, create updaters, clear the storage, and listen for added nodes.
 */
public interface WorldStateKeyValueStorage {

  /**
   * Returns the data storage format used by this world state key-value storage.
   *
   * @return the data storage format
   */
  DataStorageFormat getDataStorageFormat();

  /**
   * Creates and returns an {@link Updater} for updating this world state key-value storage.
   *
   * @return a new updater instance
   */
  Updater updater();

  /** Clears all data from this world state key-value storage. */
  void clear();

  /**
   * Listener interface for receiving notifications when new node hashes are added to the world
   * state storage.
   *
   * <p>Implementations of this interface can be registered to react to new state trie nodes being
   * persisted, which is useful for caching, tracking changes, or additional processing on node
   * additions.
   */
  interface NodesAddedListener {
    /**
     * Invoked when nodes are added to the storage.
     *
     * @param nodeHash the collection of node hashes that were added
     */
    void onNodesAdded(Collection<Bytes32> nodeHash);
  }

  /**
   * Updater interface for batching changes to the world state key-value storage.
   *
   * <p>This interface supports staging of multiple world state changes before atomically committing
   * them to storage, ensuring consistency and rollback capabilities for state update operations.
   */
  interface Updater {
    /** Commits the pending updates to the world state key-value storage. */
    void commit();
  }
}
