/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.WorldStateHealerHelper.throwingWorldStateHealerSupplier;
import static org.hyperledger.besu.plugin.services.storage.WorldStateQueryParams.withBlockHeaderAndNoUpdateNodeHead;
import static org.hyperledger.besu.plugin.services.storage.WorldStateQueryParams.withBlockHeaderAndUpdateNodeHead;
import static org.hyperledger.besu.plugin.services.storage.WorldStateQueryParams.withStateRootAndBlockHashAndUpdateNodeHead;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.BonsaiCachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.BonsaiCachedWorldStorageManager;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.CodeCache;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.TrieLogLayer;
import org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.TrieLogManager;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BonsaiWorldStateProviderTest {
  final BlockHeaderTestFixture blockBuilder = new BlockHeaderTestFixture();

  @Mock Blockchain blockchain;

  @Mock StorageProvider storageProvider;

  @Mock SegmentedKeyValueStorage segmentedKeyValueStorage;
  @Mock KeyValueStorage trieLogStorage;
  @Mock SegmentedKeyValueStorageTransaction segmentedKeyValueStorageTransaction;
  BonsaiWorldStateProvider bonsaiWorldStateArchive;

  @Mock BonsaiCachedWorldStorageManager cachedWorldStorageManager;
  @Mock TrieLogManager trieLogManager;

  @BeforeEach
  public void setUp() {
    when(storageProvider.getStorageBySegmentIdentifiers(anyList()))
        .thenReturn(segmentedKeyValueStorage);
    when(segmentedKeyValueStorage.startTransaction())
        .thenReturn(segmentedKeyValueStorageTransaction);
    when(storageProvider.getStorageBySegmentIdentifier(any())).thenReturn(trieLogStorage);
    when(trieLogStorage.startTransaction()).thenReturn(mock(KeyValueStorageTransaction.class));
  }

  @Test
  void testGetMutableReturnPersistedStateWhenNeeded() {

    bonsaiWorldStateArchive =
        new BonsaiWorldStateProvider(
            cachedWorldStorageManager,
            trieLogManager,
            new BonsaiWorldStateKeyValueStorage(
                storageProvider,
                new NoOpMetricsSystem(),
                DataStorageConfiguration.DEFAULT_BONSAI_CONFIG),
            blockchain,
            new BonsaiCachedMerkleTrieLoader(new NoOpMetricsSystem()),
            EvmConfiguration.DEFAULT,
            throwingWorldStateHealerSupplier(),
            new CodeCache());

    final BlockHeader genesis = blockBuilder.number(0).buildHeader();
    final BlockHeader blockHeader1 =
        blockBuilder.number(1).parentHash(genesis.getHash()).buildHeader();

    bonsaiWorldStateArchive.getWorldState().persist(genesis);
    bonsaiWorldStateArchive.getWorldState().persist(blockHeader1);

    when(blockchain.getBlockHeader(genesis.getHash())).thenReturn(Optional.of(genesis));
    when(blockchain.getBlockHeader(blockHeader1.getHash())).thenReturn(Optional.of(blockHeader1));

    TrieLogLayer trieLogLayer1 = mock(TrieLogLayer.class);
    when(trieLogLayer1.getBlockHash()).thenReturn(blockHeader1.getHash());
    doAnswer(__ -> Optional.of(trieLogLayer1))
        .when(trieLogManager)
        .getTrieLogLayer(eq(blockHeader1.getHash()));

    final Optional<BonsaiWorldState> worldStateGenesis =
        bonsaiWorldStateArchive
            .getWorldState(withBlockHeaderAndUpdateNodeHead(genesis))
            .map(BonsaiWorldState.class::cast);
    assertThat(worldStateGenesis.get().getWorldStateBlockHash()).isEqualTo(genesis.getBlockHash());

    final Optional<BonsaiWorldState> worldState1 =
        bonsaiWorldStateArchive
            .getWorldState(withBlockHeaderAndUpdateNodeHead(blockHeader1))
            .map(BonsaiWorldState.class::cast);
    assertThat(worldState1.get().getWorldStateBlockHash()).isEqualTo(blockHeader1.getBlockHash());
  }

  @Test
  void testGetMutableReturnEmptyWhenLoadMoreThanLimitLayersBack() {
    bonsaiWorldStateArchive =
        new BonsaiWorldStateProvider(
            new BonsaiWorldStateKeyValueStorage(
                storageProvider,
                new NoOpMetricsSystem(),
                DataStorageConfiguration.DEFAULT_BONSAI_CONFIG),
            blockchain,
            Optional.of(512L),
            new BonsaiCachedMerkleTrieLoader(new NoOpMetricsSystem()),
            null,
            EvmConfiguration.DEFAULT,
            throwingWorldStateHealerSupplier(),
            new CodeCache());

    final BlockHeader genesis = blockBuilder.number(0).buildHeader();
    final BlockHeader blockHeader512 =
        blockBuilder.number(512).parentHash(genesis.getHash()).buildHeader();

    when(blockchain.getBlockHeader(genesis.getHash())).thenReturn(Optional.of(genesis));
    when(blockchain.getBlockHeader(blockHeader512.getHash()))
        .thenReturn(Optional.of(blockHeader512));

    when(blockchain.getChainHeadHeader()).thenReturn(blockHeader512);
    assertThat(bonsaiWorldStateArchive.getWorldState(withBlockHeaderAndNoUpdateNodeHead(genesis)))
        .isEmpty();
    verify(cachedWorldStorageManager, Mockito.never()).getWorldState(any(Hash.class));
  }

  @Test
  void testGetMutableWhenLoadLessThanLimitLayersBack() {

    bonsaiWorldStateArchive =
        new BonsaiWorldStateProvider(
            cachedWorldStorageManager,
            trieLogManager,
            new BonsaiWorldStateKeyValueStorage(
                storageProvider,
                new NoOpMetricsSystem(),
                DataStorageConfiguration.DEFAULT_BONSAI_CONFIG),
            blockchain,
            new BonsaiCachedMerkleTrieLoader(new NoOpMetricsSystem()),
            EvmConfiguration.DEFAULT,
            throwingWorldStateHealerSupplier(),
            new CodeCache());

    final BlockHeader genesis = blockBuilder.number(0).buildHeader();
    final BlockHeader blockHeader511 =
        blockBuilder.number(511).parentHash(genesis.getHash()).buildHeader();

    when(blockchain.getBlockHeader(genesis.getHash())).thenReturn(Optional.of(genesis));
    when(blockchain.getBlockHeader(blockHeader511.getHash()))
        .thenReturn(Optional.of(blockHeader511));

    bonsaiWorldStateArchive.getWorldState().persist(blockHeader511);

    final BonsaiWorldState mockWorldState0 = mock(BonsaiWorldState.class);
    when(mockWorldState0.blockHash()).thenReturn(genesis.getHash());
    when(mockWorldState0.freezeStorage()).thenReturn(mockWorldState0);
    when(cachedWorldStorageManager.getWorldState(genesis.getHash()))
        .thenReturn(Optional.of(mockWorldState0));

    when(trieLogManager.getMaxLayersToLoad()).thenReturn(Long.valueOf(512));
    when(blockchain.getChainHeadHeader()).thenReturn(blockHeader511);
    assertThat(bonsaiWorldStateArchive.getWorldState(withBlockHeaderAndNoUpdateNodeHead(genesis)))
        .containsInstanceOf(BonsaiWorldState.class);
  }

  @Test
  void testGetMutableWithStorageInconsistencyRollbackTheState() {

    var worldStateKeyValueStorage =
        new BonsaiWorldStateKeyValueStorage(
            storageProvider,
            new NoOpMetricsSystem(),
            DataStorageConfiguration.DEFAULT_BONSAI_CONFIG);
    bonsaiWorldStateArchive =
        spy(
            new BonsaiWorldStateProvider(
                cachedWorldStorageManager,
                trieLogManager,
                worldStateKeyValueStorage,
                blockchain,
                new BonsaiCachedMerkleTrieLoader(new NoOpMetricsSystem()),
                EvmConfiguration.DEFAULT,
                throwingWorldStateHealerSupplier(),
                new CodeCache()));

    final BlockHeader genesis = blockBuilder.number(0).buildHeader();
    final BlockHeader blockHeader1 =
        blockBuilder.number(1).parentHash(genesis.getHash()).buildHeader();

    when(blockchain.getBlockHeader(genesis.getHash())).thenReturn(Optional.of(genesis));
    when(blockchain.getBlockHeader(blockHeader1.getHash())).thenReturn(Optional.of(blockHeader1));

    bonsaiWorldStateArchive.getWorldState().persist(genesis);
    bonsaiWorldStateArchive.getWorldState().persist(blockHeader1);

    TrieLogLayer trieLogLayer1 = mock(TrieLogLayer.class);
    when(trieLogLayer1.getBlockHash()).thenReturn(blockHeader1.getHash());
    doAnswer(__ -> Optional.of(trieLogLayer1))
        .when(trieLogManager)
        .getTrieLogLayer(eq(blockHeader1.getHash()));
    assertThat(
            bonsaiWorldStateArchive.getWorldState(
                withStateRootAndBlockHashAndUpdateNodeHead(null, genesis.getHash())))
        .containsInstanceOf(BonsaiWorldState.class);

    // verify is trying to get the trie log layer to rollback
    verify(trieLogManager).getTrieLogLayer(blockHeader1.getBlockHash());
  }

  //    @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  void testGetMutableWithStorageConsistencyNotRollbackTheState() {

    var worldStateKeyValueStorage =
        new BonsaiWorldStateKeyValueStorage(
            storageProvider,
            new NoOpMetricsSystem(),
            DataStorageConfiguration.DEFAULT_BONSAI_CONFIG);
    bonsaiWorldStateArchive =
        spy(
            new BonsaiWorldStateProvider(
                cachedWorldStorageManager,
                trieLogManager,
                worldStateKeyValueStorage,
                blockchain,
                new BonsaiCachedMerkleTrieLoader(new NoOpMetricsSystem()),
                EvmConfiguration.DEFAULT,
                throwingWorldStateHealerSupplier(),
                new CodeCache()));

    final BlockHeader genesis = blockBuilder.number(0).buildHeader();
    final BlockHeader blockHeader1 =
        blockBuilder.number(1).parentHash(genesis.getHash()).buildHeader();

    when(blockchain.getBlockHeader(genesis.getHash())).thenReturn(Optional.of(genesis));
    when(blockchain.getBlockHeader(blockHeader1.getHash())).thenReturn(Optional.of(blockHeader1));

    bonsaiWorldStateArchive.getWorldState().persist(genesis);
    bonsaiWorldStateArchive.getWorldState().persist(blockHeader1);

    assertThat(
            bonsaiWorldStateArchive.getWorldState(
                withStateRootAndBlockHashAndUpdateNodeHead(null, blockHeader1.getHash())))
        .containsInstanceOf(BonsaiWorldState.class);

    // verify is not trying to get the trie log layer to rollback when block is present
    verify(trieLogManager, Mockito.never()).getTrieLogLayer(any());
  }

  @Test
  void testGetMutableWithStorageConsistencyToRollbackAndRollForwardTheState() {

    var worldStateKeyValueStorage =
        new BonsaiWorldStateKeyValueStorage(
            storageProvider,
            new NoOpMetricsSystem(),
            DataStorageConfiguration.DEFAULT_BONSAI_CONFIG);

    bonsaiWorldStateArchive =
        spy(
            new BonsaiWorldStateProvider(
                cachedWorldStorageManager,
                trieLogManager,
                worldStateKeyValueStorage,
                blockchain,
                new BonsaiCachedMerkleTrieLoader(new NoOpMetricsSystem()),
                EvmConfiguration.DEFAULT,
                throwingWorldStateHealerSupplier(),
                new CodeCache()));

    final BlockHeader genesis = blockBuilder.number(0).buildHeader();
    final BlockHeader blockHeader1 =
        blockBuilder.number(1).timestamp(1).parentHash(genesis.getHash()).buildHeader();
    final BlockHeader blockHeader1Reorg =
        blockBuilder.number(1).timestamp(2).parentHash(genesis.getHash()).buildHeader();

    when(blockchain.getBlockHeader(genesis.getHash())).thenReturn(Optional.of(genesis));
    when(blockchain.getBlockHeader(blockHeader1.getHash())).thenReturn(Optional.of(blockHeader1));
    when(blockchain.getBlockHeader(blockHeader1Reorg.getHash()))
        .thenReturn(Optional.of(blockHeader1Reorg));

    bonsaiWorldStateArchive.getWorldState().persist(genesis);
    bonsaiWorldStateArchive.getWorldState().persist(blockHeader1);

    TrieLogLayer trieLogLayerA = mock(TrieLogLayer.class);
    when(trieLogLayerA.getBlockHash()).thenReturn(blockHeader1.getBlockHash());
    doAnswer(__ -> Optional.of(trieLogLayerA))
        .when(trieLogManager)
        .getTrieLogLayer(eq(blockHeader1.getHash()));

    TrieLogLayer trieLogLayerB = mock(TrieLogLayer.class);
    when(trieLogLayerB.getBlockHash()).thenReturn(blockHeader1Reorg.getBlockHash());
    doAnswer(__ -> Optional.of(trieLogLayerB))
        .when(trieLogManager)
        .getTrieLogLayer(eq(blockHeader1Reorg.getHash()));

    assertThat(
            bonsaiWorldStateArchive.getWorldState(
                withStateRootAndBlockHashAndUpdateNodeHead(null, blockHeader1Reorg.getHash())))
        .containsInstanceOf(BonsaiWorldState.class);

    // verify is trying to get the trie log layers to rollback and roll forward
    verify(trieLogManager).getTrieLogLayer(blockHeader1.getHash());
    verify(trieLogManager).getTrieLogLayer(blockHeader1Reorg.getHash());
  }
}
