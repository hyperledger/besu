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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.mainnet.staterootcommitter.StateRootCommitter;
import org.hyperledger.besu.ethereum.mainnet.staterootcommitter.StateRootCommitterImplSync;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.WorldStateConfig;
import org.hyperledger.besu.ethereum.worldstate.WorldStateKeyValueStorage;
import org.hyperledger.besu.evm.worldstate.MutableWorldView;
import org.hyperledger.besu.evm.worldstate.WorldState;

public interface MutableWorldState extends WorldState, MutableWorldView {

  /**
   * Persist accumulated changes to underlying storage.
   *
   * @param blockHeader If persisting for an imported block, the block hash of the world state this
   *     represents. If this does not represent a forward transition from one block to the next
   *     `null` should be passed in.
   */
  void persist(BlockHeader blockHeader, StateRootCommitter committer);

  default void persist(final BlockHeader blockHeader) {
    persist(blockHeader, new StateRootCommitterImplSync());
  }

  default Hash calculateOrReadRootHash(
      final WorldStateKeyValueStorage.Updater stateUpdater,
      final BlockHeader blockHeader,
      final WorldStateConfig cfg) {
    throw new UnsupportedOperationException("calculateOrReadRootHash is not supported");
  }

  default MutableWorldState freezeStorage() {
    // no op
    throw new UnsupportedOperationException("cannot freeze");
  }

  default MutableWorldState disableTrie() {
    // no op
    throw new UnsupportedOperationException("cannot disable trie");
  }
}
