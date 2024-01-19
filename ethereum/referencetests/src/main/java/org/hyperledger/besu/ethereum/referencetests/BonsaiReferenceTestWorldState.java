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
package org.hyperledger.besu.ethereum.referencetests;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.trie.bonsai.cache.CachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.trie.bonsai.cache.CachedWorldStorageManager;
import org.hyperledger.besu.ethereum.trie.bonsai.cache.NoOpCachedWorldStorageManager;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiPreImageProxy;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.bonsai.trielog.NoOpTrieLogManager;
import org.hyperledger.besu.ethereum.trie.bonsai.trielog.TrieLogManager;
import org.hyperledger.besu.ethereum.trie.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class BonsaiReferenceTestWorldState extends BonsaiWorldState
    implements ReferenceTestWorldState {

  private final BonsaiReferenceTestWorldStateStorage refTestStorage;
  private final BonsaiPreImageProxy preImageProxy;
  private final EvmConfiguration evmConfiguration;

  protected BonsaiReferenceTestWorldState(
      final BonsaiReferenceTestWorldStateStorage worldStateKeyValueStorage,
      final CachedMerkleTrieLoader cachedMerkleTrieLoader,
      final CachedWorldStorageManager cachedWorldStorageManager,
      final TrieLogManager trieLogManager,
      final BonsaiPreImageProxy preImageProxy,
      final EvmConfiguration evmConfiguration) {
    super(
        worldStateKeyValueStorage,
        cachedMerkleTrieLoader,
        cachedWorldStorageManager,
        trieLogManager,
        evmConfiguration);
    this.refTestStorage = worldStateKeyValueStorage;
    this.preImageProxy = preImageProxy;
    this.evmConfiguration = evmConfiguration;
    setAccumulator(
        new BonsaiReferenceTestUpdateAccumulator(
            this,
            (addr, value) ->
                cachedMerkleTrieLoader.preLoadAccount(
                    getWorldStateStorage(), worldStateRootHash, addr),
            (addr, value) ->
                cachedMerkleTrieLoader.preLoadStorageSlot(getWorldStateStorage(), addr, value),
            preImageProxy,
            evmConfiguration));
  }

  @Override
  public ReferenceTestWorldState copy() {
    var layerCopy =
        new BonsaiReferenceTestWorldStateStorage(worldStateKeyValueStorage, preImageProxy);
    return new BonsaiReferenceTestWorldState(
        layerCopy,
        cachedMerkleTrieLoader,
        cachedWorldStorageManager,
        trieLogManager,
        preImageProxy,
        evmConfiguration);
  }

  /**
   * For reference tests world state root validation is handled in the harness, this stubs out the
   * behavior to always pass.
   *
   * @param calculatedStateRoot state root calculated during bonsai persist step.
   * @param header supplied reference test block header.
   */
  @Override
  protected void verifyWorldStateRoot(final Hash calculatedStateRoot, final BlockHeader header) {
    // The test harness validates the root hash, no need to validate in-line for reference test
  }

  @JsonCreator
  public static BonsaiReferenceTestWorldState create(
      final Map<String, ReferenceTestWorldState.AccountMock> accounts) {
    return create(accounts, EvmConfiguration.DEFAULT);
  }

  @JsonCreator
  public static BonsaiReferenceTestWorldState create(
      final Map<String, ReferenceTestWorldState.AccountMock> accounts,
      final EvmConfiguration evmConfiguration) {
    final ObservableMetricsSystem metricsSystem = new NoOpMetricsSystem();
    final CachedMerkleTrieLoader cachedMerkleTrieLoader = new CachedMerkleTrieLoader(metricsSystem);
    final TrieLogManager trieLogManager = new NoOpTrieLogManager();
    final BonsaiPreImageProxy preImageProxy =
        new BonsaiPreImageProxy.BonsaiReferenceTestPreImageProxy();

    final BonsaiWorldStateKeyValueStorage bonsaiWorldStateKeyValueStorage =
        new BonsaiWorldStateKeyValueStorage(
            new InMemoryKeyValueStorageProvider(),
            metricsSystem,
            DataStorageConfiguration.DEFAULT_CONFIG);

    final BonsaiReferenceTestWorldStateStorage worldStateKeyValueStorage =
        new BonsaiReferenceTestWorldStateStorage(bonsaiWorldStateKeyValueStorage, preImageProxy);

    final NoOpCachedWorldStorageManager noOpCachedWorldStorageManager =
        new NoOpCachedWorldStorageManager(bonsaiWorldStateKeyValueStorage);

    final BonsaiReferenceTestWorldState worldState =
        new BonsaiReferenceTestWorldState(
            worldStateKeyValueStorage,
            cachedMerkleTrieLoader,
            noOpCachedWorldStorageManager,
            trieLogManager,
            preImageProxy,
            evmConfiguration);

    final WorldUpdater updater = worldState.updater();
    for (final Map.Entry<String, ReferenceTestWorldState.AccountMock> entry : accounts.entrySet()) {
      ReferenceTestWorldState.insertAccount(
          updater, Address.fromHexString(entry.getKey()), entry.getValue());
    }
    updater.commit();
    return worldState;
  }

  @Override
  public Stream<StreamableAccount> streamAccounts(final Bytes32 startKeyHash, final int limit) {
    return this.refTestStorage.streamAccounts(this, startKeyHash, limit);
  }

  @Override
  protected Hash hashAndSavePreImage(final Bytes value) {
    // by default do not save has preImages
    return preImageProxy.hashAndSavePreImage(value);
  }
}
