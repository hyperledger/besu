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
 *
 */

package org.hyperledger.besu.ethereum.trie.diffbased.verkle;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.diffbased.common.trielog.TrieLogManager;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.cache.VerkleCachedWorldStorageManager;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.VerkleWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.worldview.VerkleWorldState;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;

public class VerkleWorldStateProvider extends DiffBasedWorldStateProvider {

  public VerkleWorldStateProvider(
      final VerkleWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Blockchain blockchain,
      final Optional<Long> maxLayersToLoad,
      final BesuContext pluginContext,
      final EvmConfiguration evmConfiguration) {
    super(
        DataStorageFormat.VERKLE,
        worldStateKeyValueStorage,
        blockchain,
        maxLayersToLoad,
        pluginContext);
    provideCachedWorldStorageManager(
        new VerkleCachedWorldStorageManager(this, worldStateKeyValueStorage));
    loadPersistedState(new VerkleWorldState(this, worldStateKeyValueStorage, evmConfiguration));
  }

  @VisibleForTesting
  VerkleWorldStateProvider(
      final VerkleCachedWorldStorageManager cachedWorldStorageManager,
      final TrieLogManager trieLogManager,
      final VerkleWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Blockchain blockchain,
      final EvmConfiguration evmConfiguration) {
    super(worldStateKeyValueStorage, blockchain, trieLogManager);
    provideCachedWorldStorageManager(cachedWorldStorageManager);
    loadPersistedState(new VerkleWorldState(this, worldStateKeyValueStorage, evmConfiguration));
  }
}
