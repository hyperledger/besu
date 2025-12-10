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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.data.BlockHeader;

/**
 * The StateRootCommitter interface defines the contract for computing and committing the state root
 * for the Ethereum world state after processing a block.
 *
 * <p>Implementations of this interface are responsible for processing the {@link
 * MutableWorldState}, calculating the new state root hash, and persisting any required changes into
 * storage.
 */
public interface StateRootCommitter {

  /**
   * Computes the state root hash and commits the changes to the world state storage.
   *
   * @param worldState the mutable world state to be updated
   * @param stateUpdater the updater for the world state key-value storage
   * @param blockHeader the block header representing the current block
   * @param worldStateConfig the world state configuration
   * @return the computed state root {@link Hash}
   */
  Hash computeRootAndCommit(
      MutableWorldState worldState,
      WorldStateKeyValueStorage.Updater stateUpdater,
      BlockHeader blockHeader,
      WorldStateConfig worldStateConfig);

  /**
   * Cancels any ongoing root computation or commit operations, if supported by the implementation.
   * Default implementation does nothing.
   */
  default void cancel() {}
}
