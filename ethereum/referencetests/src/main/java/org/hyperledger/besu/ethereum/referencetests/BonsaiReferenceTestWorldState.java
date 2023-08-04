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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiAccount;
import org.hyperledger.besu.ethereum.bonsai.cache.CachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateLayerStorage;
import org.hyperledger.besu.ethereum.bonsai.trielog.TrieLogAddedEvent;
import org.hyperledger.besu.ethereum.bonsai.trielog.TrieLogFactoryImpl;
import org.hyperledger.besu.ethereum.bonsai.trielog.TrieLogManager;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogEvent;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogFactory;
import org.hyperledger.besu.util.Subscribers;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonCreator;

public class BonsaiReferenceTestWorldState extends BonsaiWorldState
    implements ReferenceTestWorldState {

  static final Map<Hash, Bytes> preImageCache = new HashMap<>();

  protected BonsaiReferenceTestWorldState(
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final CachedMerkleTrieLoader cachedMerkleTrieLoader,
      final TrieLogManager trieLogManager) {
    super(worldStateStorage, cachedMerkleTrieLoader, trieLogManager, BonsaiReferenceTestWorldState::preImageHasher);
    worldStateStorage.setPreImageMapper(hash -> Optional.ofNullable(preImageCache.get(hash)));
  }

  private static Hash preImageHasher(final Bytes key) {
    Hash hash = Hash.hash(key);
    preImageCache.putIfAbsent(hash, key);
    return hash;
  }

  @Override
  public ReferenceTestWorldState copy() {
    var layerCopy = new BonsaiWorldStateLayerStorage(worldStateStorage);
    layerCopy.setPreImageMapper(hash -> Optional.ofNullable(preImageCache.get(hash)));
    return new BonsaiReferenceTestWorldState(
        layerCopy,
        cachedMerkleTrieLoader,
        trieLogManager);
  }

  @JsonCreator
  public static BonsaiReferenceTestWorldState create(
      final Map<String, ReferenceTestWorldState.AccountMock> accounts) {
    final ObservableMetricsSystem metricsSystem = new NoOpMetricsSystem();
    final CachedMerkleTrieLoader cachedMerkleTrieLoader = new CachedMerkleTrieLoader(metricsSystem);
    final TrieLogManager trieLogManager = new NoOpTrieLogManager();
    final BonsaiWorldStateKeyValueStorage worldStateStorage =
        new BonsaiWorldStateKeyValueStorage(new InMemoryKeyValueStorageProvider(), metricsSystem);
    final BonsaiReferenceTestWorldState worldState =
        new BonsaiReferenceTestWorldState(
            worldStateStorage, cachedMerkleTrieLoader, trieLogManager);

    final WorldUpdater updater = worldState.updater();
    for (final Map.Entry<String, ReferenceTestWorldState.AccountMock> entry : accounts.entrySet()) {
      ReferenceTestWorldState.insertAccount(
          updater, Address.fromHexString(entry.getKey()), entry.getValue());
    }
    updater.commit();
    return worldState;
  }

  @Override
  public Stream<StreamableAccount> streamAccounts(final Bytes32 startKeyHash, final int limit){
    return worldStateStorage
        .streamFlatAccounts(
            //TODO: use a constant for the end range
            startKeyHash, Bytes32.fromHexStringLenient("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"), Long.MAX_VALUE)
        .entrySet()
        // map to addresses:
        .stream()
        .map(entry -> {
          Address address = Address.wrap(preImageCache.get(entry.getKey()));
          return new StreamableAccount(Optional.of(address),
              BonsaiAccount.fromRLP(this, address, entry.getValue(), false));
        })
        .sorted(Comparator.comparing(account -> account.getAddress().orElse(Address.ZERO)));
  }

  static class NoOpTrieLogManager implements TrieLogManager {
    private final Subscribers<TrieLogEvent.TrieLogObserver> trieLogObservers = Subscribers.create();
    private final TrieLogFactory trieLogFactory = new TrieLogFactoryImpl();

    @Override
    public void saveTrieLog(
        final BonsaiWorldStateUpdateAccumulator localUpdater,
        final Hash forWorldStateRootHash,
        final BlockHeader forBlockHeader,
        final BonsaiWorldState forWorldState) {
      // notify trie log added observers, synchronously
      TrieLog trieLog = trieLogFactory.create(localUpdater, forBlockHeader);
      trieLogObservers.forEach(o -> o.onTrieLogAdded(new TrieLogAddedEvent(trieLog)));
    }

    @Override
    public void addCachedLayer(
        final BlockHeader blockHeader,
        final Hash worldStateRootHash,
        final BonsaiWorldState forWorldState) {}

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
    public long getMaxLayersToLoad() {
      return 0;
    }

    @Override
    public void reset() {}

    @Override
    public Optional<? extends TrieLog> getTrieLogLayer(final Hash blockHash) {
      return Optional.empty();
    }

    @Override
    public synchronized long subscribe(final TrieLogEvent.TrieLogObserver sub) {
      return trieLogObservers.subscribe(sub);
    }

    @Override
    public synchronized void unsubscribe(final long id) {
      trieLogObservers.unsubscribe(id);
    }
  }
}
