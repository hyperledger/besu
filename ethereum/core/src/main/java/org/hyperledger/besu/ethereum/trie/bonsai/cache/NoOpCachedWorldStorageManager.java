/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.trie.bonsai.cache;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.bonsai.worldview.BonsaiWorldState;

import java.util.Optional;
import java.util.function.Function;

public class NoOpCachedWorldStorageManager extends CachedWorldStorageManager {

  public NoOpCachedWorldStorageManager(
      final BonsaiWorldStateKeyValueStorage bonsaiWorldStateKeyValueStorage) {
    super(null, bonsaiWorldStateKeyValueStorage);
  }

  @Override
  public synchronized void addCachedLayer(
      final BlockHeader blockHeader,
      final Hash worldStateRootHash,
      final BonsaiWorldState forWorldState) {
    // no cache
  }

  @Override
  public boolean containWorldStateStorage(final Hash blockHash) {
    return false;
  }

  @Override
  public Optional<BonsaiWorldState> getWorldState(final Hash blockHash) {
    return Optional.empty();
  }

  @Override
  public Optional<BonsaiWorldState> getNearestWorldState(final BlockHeader blockHeader) {
    return Optional.empty();
  }

  @Override
  public Optional<BonsaiWorldState> getHeadWorldState(
      final Function<Hash, Optional<BlockHeader>> hashBlockHeaderFunction) {
    return Optional.empty();
  }

  @Override
  public void reset() {
    // world states are not re-used
  }
}
