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
import org.hyperledger.besu.evm.worldstate.MutableWorldView;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.plugin.data.BlockHeader;

/**
 * Represents a mutable view of the Ethereum world state.
 *
 * <p>A world state refers to the mapping between addresses and account data, including balances,
 * storage, code, and other fields. This interface provides the ability to persist modifications,
 * calculate the state root hash, and manage underlying trie storage mechanisms.
 */
public interface MutableWorldState extends WorldState, MutableWorldView {

  /**
   * Persist accumulated changes to underlying storage.
   *
   * @param blockHeader If persisting for an imported block, the block hash of the world state this
   *     represents. If this does not represent a forward transition from one block to the next
   *     `null` should be passed in.
   * @param committer An implementation of {@link StateRootCommitter} responsible for recomputing
   *     the state root and committing the state changes to storage.
   */
  void persist(BlockHeader blockHeader, StateRootCommitter committer);

  /**
   * Persists the accumulated changes in the world state using the given {@link BlockHeader} and a
   * default synchronous {@link StateRootCommitter} implementation.
   *
   * @param blockHeader the block header representing the imported block for which the world state
   *     changes are being persisted; if this does not represent a forward transition from one block
   *     to the next, {@code null} should be passed
   */
  void persist(final BlockHeader blockHeader);

  /**
   * Calculates or reads the state root hash for the specified world state using the provided {@link
   * WorldStateKeyValueStorage.Updater}, {@link BlockHeader}, and {@link WorldStateConfig}.
   *
   * @param stateUpdater the updater for the world state key-value storage
   * @param blockHeader the block header for the computation
   * @param cfg the world state configuration
   * @return the computed or retrieved {@link Hash} representing the state root
   * @throws UnsupportedOperationException if the operation is not supported by the implementation
   */
  default Hash calculateOrReadRootHash(
      final WorldStateKeyValueStorage.Updater stateUpdater,
      final BlockHeader blockHeader,
      final WorldStateConfig cfg) {
    throw new UnsupportedOperationException("calculateOrReadRootHash is not supported");
  }

  /**
   * Returns an immutable (frozen) view of the current world state storage.
   *
   * @return an immutable {@link MutableWorldState}
   * @throws UnsupportedOperationException if the operation is not supported by the implementation
   */
  default MutableWorldState freezeStorage() {
    // no op
    throw new UnsupportedOperationException("cannot freeze");
  }

  /**
   * Disables the use of the state trie in the world state.
   *
   * @return a {@link MutableWorldState} with the trie disabled
   * @throws UnsupportedOperationException if the operation is not supported by the implementation
   */
  default MutableWorldState disableTrie() {
    // no op
    throw new UnsupportedOperationException("cannot disable trie");
  }
}
