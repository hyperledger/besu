/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.db;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.WorldState;
import tech.pegasys.pantheon.ethereum.trie.MerklePatriciaTrie;
import tech.pegasys.pantheon.ethereum.worldstate.DefaultMutableWorldState;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;

public class WorldStateArchive {
  private final WorldStateStorage storage;
  private static final Hash EMPTY_ROOT_HASH = Hash.wrap(MerklePatriciaTrie.EMPTY_TRIE_ROOT_HASH);

  public WorldStateArchive(final WorldStateStorage storage) {
    this.storage = storage;
  }

  public WorldState get(final Hash rootHash) {
    return getMutable(rootHash);
  }

  public MutableWorldState getMutable(final Hash rootHash) {
    return new DefaultMutableWorldState(rootHash, storage);
  }

  public WorldState get() {
    return get(EMPTY_ROOT_HASH);
  }

  public MutableWorldState getMutable() {
    return getMutable(EMPTY_ROOT_HASH);
  }
}
