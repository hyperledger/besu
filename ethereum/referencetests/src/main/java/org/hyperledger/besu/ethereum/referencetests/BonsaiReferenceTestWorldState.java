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
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiPreImageProxy;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiWorldStateLayerStorage;
import org.hyperledger.besu.ethereum.trie.bonsai.trielog.TrieLogAddedEvent;
import org.hyperledger.besu.ethereum.trie.bonsai.trielog.TrieLogManager;
import org.hyperledger.besu.ethereum.trie.bonsai.trielog.TrieLogPruner;
import org.hyperledger.besu.ethereum.trie.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.bonsai.worldview.BonsaiWorldStateUpdateAccumulator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class BonsaiReferenceTestWorldState extends BonsaiWorldState
    implements ReferenceTestWorldState {

  private final BonsaiReferenceTestWorldStateStorage refTestStorage;
  private final BonsaiPreImageProxy preImageProxy;
  private final EvmConfiguration evmConfiguration;

  protected BonsaiReferenceTestWorldState(
      final BonsaiReferenceTestWorldStateStorage worldStateStorage,
      final CachedMerkleTrieLoader cachedMerkleTrieLoader,
      final CachedWorldStorageManager cachedWorldStorageManager,
      final TrieLogManager trieLogManager,
      final BonsaiPreImageProxy preImageProxy,
      final EvmConfiguration evmConfiguration) {
    super(
        worldStateStorage,
        cachedMerkleTrieLoader,
        cachedWorldStorageManager,
        trieLogManager,
        evmConfiguration);
    this.refTestStorage = worldStateStorage;
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
    var layerCopy = new BonsaiReferenceTestWorldStateStorage(worldStateStorage, preImageProxy);
    return new BonsaiReferenceTestWorldState(
        layerCopy,
        cachedMerkleTrieLoader,
        cachedWorldStorageManager,
        trieLogManager,
        preImageProxy,
        evmConfiguration);
  }

  /**
   * TrieLog is an important part of Bonsai, so it's important to verify the generation of the
   * TrieLog by performing rollbacks and rollforwards.
   *
   * @param blockHeader header of the block to import
   */
  private void validateStateRolling(final BlockHeader blockHeader) {
    if (blockHeader != null) {
      final Hash parentStateRoot = getWorldStateRootHash();
      final BonsaiReferenceTestUpdateAccumulator originalUpdater =
          ((BonsaiReferenceTestUpdateAccumulator) updater()).createDetachedAccumulator();

      validatePersistedStateRolling(parentStateRoot, originalUpdater, blockHeader);
      validateFrozenStateRolling(parentStateRoot, originalUpdater, blockHeader);
    }
  }

  private void validateFrozenStateRolling(
      final Hash parentStateRoot,
      final BonsaiReferenceTestUpdateAccumulator originalUpdater,
      final BlockHeader blockHeader) {
    // generate trie from frozen state
    generateTrieLogFromState(blockHeader, originalUpdater, true);
    final TrieLog trieLogFromFrozenState =
        trieLogManager
            .getTrieLogLayer(blockHeader.getBlockHash())
            .orElseThrow(() -> new RuntimeException("trielog not found during test"));
    // trying rollback rollfoward with frozen state
    validateTrieLog(parentStateRoot, blockHeader, trieLogFromFrozenState);
  }

  private void validatePersistedStateRolling(
      final Hash parentStateRoot,
      final BonsaiReferenceTestUpdateAccumulator originalUpdater,
      final BlockHeader blockHeader) {
    // generate trie from persisted state
    generateTrieLogFromState(blockHeader, originalUpdater, false);
    final TrieLog trieLogFromPersistedState =
        trieLogManager
            .getTrieLogLayer(blockHeader.getBlockHash())
            .orElseThrow(() -> new RuntimeException("trielog not found during test"));
    // trying rollback rollfoward with persisted state
    validateTrieLog(parentStateRoot, blockHeader, trieLogFromPersistedState);
  }

  private void validateTrieLog(
      final Hash parentStateRoot, final BlockHeader blockHeader, final TrieLog trieLog) {

    try (var bonsaiWorldState = createBonsaiWorldState(false)) {
      BonsaiWorldStateUpdateAccumulator updaterForState =
          (BonsaiWorldStateUpdateAccumulator) bonsaiWorldState.updater();
      updaterForState.rollForward(trieLog);
      updaterForState.commit();
      bonsaiWorldState.persist(blockHeader);
      Hash generatedRootHash = bonsaiWorldState.rootHash();
      if (!bonsaiWorldState.rootHash().equals(blockHeader.getStateRoot())) {
        throw new RuntimeException(
            "state root becomes invalid following a rollForward %s != %s"
                .formatted(blockHeader.getStateRoot(), generatedRootHash));
      }

      updaterForState = (BonsaiWorldStateUpdateAccumulator) bonsaiWorldState.updater();
      updaterForState.rollBack(trieLog);
      updaterForState.commit();
      bonsaiWorldState.persist(null);
      generatedRootHash = bonsaiWorldState.rootHash();
      if (!bonsaiWorldState.rootHash().equals(parentStateRoot)) {
        throw new RuntimeException(
            "state root becomes invalid following a rollBackward %s != %s"
                .formatted(parentStateRoot, generatedRootHash));
      }
    }
  }

  private void generateTrieLogFromState(
      final BlockHeader blockHeader,
      final BonsaiReferenceTestUpdateAccumulator originalUpdater,
      final boolean isFrozen) {
    // generate trielog
    BonsaiReferenceTestUpdateAccumulator updaterForState =
        originalUpdater.createDetachedAccumulator();
    try (var bonsaiWorldState = createBonsaiWorldState(isFrozen)) {
      bonsaiWorldState.setAccumulator(updaterForState);
      updaterForState.commit();
      bonsaiWorldState.persist(blockHeader);
    }
  }

  private BonsaiWorldState createBonsaiWorldState(final boolean isFrozen) {
    BonsaiWorldState bonsaiWorldState =
        new BonsaiWorldState(
            new BonsaiWorldStateLayerStorage(worldStateStorage),
            cachedMerkleTrieLoader,
            cachedWorldStorageManager,
            trieLogManager,
            evmConfiguration);
    if (isFrozen) {
      bonsaiWorldState.freeze(); // freeze state
    }
    return bonsaiWorldState;
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

  @Override
  public void persist(final BlockHeader blockHeader) {
    validateStateRolling(blockHeader);
    super.persist(blockHeader);
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

    final BonsaiReferenceTestWorldStateStorage worldStateStorage =
        new BonsaiReferenceTestWorldStateStorage(
            new BonsaiWorldStateKeyValueStorage(
                new InMemoryKeyValueStorageProvider(), metricsSystem),
            preImageProxy);

    final NoOpCachedWorldStorageManager noOpCachedWorldStorageManager =
        new NoOpCachedWorldStorageManager();

    final BonsaiReferenceTestWorldState worldState =
        new BonsaiReferenceTestWorldState(
            worldStateStorage,
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

  static class NoOpCachedWorldStorageManager extends CachedWorldStorageManager {

    public NoOpCachedWorldStorageManager() {
      super(
          null,
          new BonsaiWorldStateKeyValueStorage(
              new InMemoryKeyValueStorageProvider(), new NoOpMetricsSystem()),
          new NoOpMetricsSystem());
    }

    @SuppressWarnings({"UnsynchronizedOverridesSynchronized", "squid:S3551"})
    @Override
    public void addCachedLayer(
        final BlockHeader blockHeader,
        final Hash worldStateRootHash,
        final BonsaiWorldState forWorldState) {
      // reference test world states are not cached
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
      // reference test world states are not re-used
    }
  }

  static class NoOpTrieLogManager extends TrieLogManager {

    private final Cache<Hash, byte[]> trieLogCache =
        CacheBuilder.newBuilder().maximumSize(5).build();

    public NoOpTrieLogManager() {
      super(null, null, 0, null, TrieLogPruner.noOpTrieLogPruner());
    }

    @SuppressWarnings({"UnsynchronizedOverridesSynchronized", "squid:S3551"})
    @Override
    public void saveTrieLog(
        final BonsaiWorldStateUpdateAccumulator localUpdater,
        final Hash forWorldStateRootHash,
        final BlockHeader forBlockHeader,
        final BonsaiWorldState forWorldState) {
      // notify trie log added observers, synchronously
      TrieLog trieLog = trieLogFactory.create(localUpdater, forBlockHeader);
      trieLogCache.put(forBlockHeader.getHash(), trieLogFactory.serialize(trieLog));
      trieLogObservers.forEach(o -> o.onTrieLogAdded(new TrieLogAddedEvent(trieLog)));
    }

    @Override
    public long getMaxLayersToLoad() {
      return 0;
    }

    @Override
    public Optional<TrieLog> getTrieLogLayer(final Hash blockHash) {
      final byte[] trielog = trieLogCache.getIfPresent(blockHash);
      trieLogCache.invalidate(blockHash); // remove trielog from the cache
      return Optional.ofNullable(trieLogFactory.deserialize(trielog));
    }
  }

  @Override
  protected Hash hashAndSavePreImage(final Bytes value) {
    // by default do not save has preImages
    return preImageProxy.hashAndSavePreImage(value);
  }
}
