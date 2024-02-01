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
package org.hyperledger.besu.ethereum.trie.diffbased.verkle.cache;

import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.diffbased.common.cache.DiffBasedCachedWorldStorageManager;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.VerkleWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.VerkleLayeredWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.VerkleSnapshotWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.VerkleWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.worldview.VerkleWorldState;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

public class VerkleCachedWorldStorageManager extends DiffBasedCachedWorldStorageManager {

  public VerkleCachedWorldStorageManager(
      final DiffBasedWorldStateProvider archive,
      final DiffBasedWorldStateKeyValueStorage worldStateKeyValueStorage) {
    super(archive, worldStateKeyValueStorage);
  }

  @Override
  public DiffBasedWorldState createWorldState(
      final DiffBasedWorldStateProvider archive,
      final DiffBasedWorldStateKeyValueStorage worldStateKeyValueStorage,
      final EvmConfiguration evmConfiguration) {
    return new VerkleWorldState(
        (VerkleWorldStateProvider) archive,
        (VerkleWorldStateKeyValueStorage) worldStateKeyValueStorage,
        evmConfiguration);
  }

  @Override
  public DiffBasedWorldStateKeyValueStorage createLayeredKeyValueStorage(
      final DiffBasedWorldStateKeyValueStorage worldStateKeyValueStorage) {
    return new VerkleLayeredWorldStateKeyValueStorage(
        (VerkleWorldStateKeyValueStorage) worldStateKeyValueStorage);
  }

  @Override
  public DiffBasedWorldStateKeyValueStorage createSnapshotKeyValueStorage(
      final DiffBasedWorldStateKeyValueStorage worldStateKeyValueStorage) {
    return new VerkleSnapshotWorldStateKeyValueStorage(
        (VerkleWorldStateKeyValueStorage) worldStateKeyValueStorage);
  }
}
