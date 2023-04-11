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
 *
 */
package org.hyperledger.besu.ethereum.bonsai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage.WORLD_BLOCK_HASH_KEY;
import static org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage.WORLD_ROOT_HASH_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.cache.CachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.trielog.TrieLogLayer;
import org.hyperledger.besu.ethereum.bonsai.trielog.TrieLogManager;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SnappableKeyValueStorage;

import java.util.Optional;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BonsaiWorldStateArchiveTest {

  final BlockHeaderTestFixture blockBuilder = new BlockHeaderTestFixture();

  @Mock Blockchain blockchain;

  @Mock StorageProvider storageProvider;

  @Mock SnappableKeyValueStorage keyValueStorage;

  BonsaiWorldStateProvider bonsaiWorldStateArchive;

  @Mock TrieLogManager trieLogManager;

  @Before
  public void setUp() {
    when(storageProvider.getStorageBySegmentIdentifier(any(KeyValueSegmentIdentifier.class)))
        .thenReturn(keyValueStorage);
  }

  @Test
  public void testGetMutableReturnPersistedStateWhenNeeded() {
    final BlockHeader chainHead = blockBuilder.number(0).buildHeader();

    when(keyValueStorage.get(WORLD_ROOT_HASH_KEY))
        .thenReturn(Optional.of(chainHead.getStateRoot().toArrayUnsafe()));
    when(keyValueStorage.get(WORLD_BLOCK_HASH_KEY))
        .thenReturn(Optional.of(chainHead.getHash().toArrayUnsafe()));
    when(keyValueStorage.get(WORLD_ROOT_HASH_KEY))
        .thenReturn(Optional.of(chainHead.getStateRoot().toArrayUnsafe()));
    when(keyValueStorage.get(WORLD_BLOCK_HASH_KEY))
        .thenReturn(Optional.of(chainHead.getHash().toArrayUnsafe()));
    bonsaiWorldStateArchive =
        new BonsaiWorldStateProvider(
            trieLogManager,
            new BonsaiWorldStateKeyValueStorage(storageProvider, new NoOpMetricsSystem()),
            blockchain,
            new CachedMerkleTrieLoader(new NoOpMetricsSystem()));

    assertThat(bonsaiWorldStateArchive.getMutable(chainHead, true))
        .containsInstanceOf(BonsaiWorldState.class);
  }

  @Test
  public void testGetMutableReturnEmptyWhenLoadMoreThanLimitLayersBack() {
    bonsaiWorldStateArchive =
        new BonsaiWorldStateProvider(
            new BonsaiWorldStateKeyValueStorage(storageProvider, new NoOpMetricsSystem()),
            blockchain,
            Optional.of(512L),
            new CachedMerkleTrieLoader(new NoOpMetricsSystem()),
            new NoOpMetricsSystem());
    final BlockHeader blockHeader = blockBuilder.number(0).buildHeader();
    final BlockHeader chainHead = blockBuilder.number(512).buildHeader();
    when(blockchain.getChainHeadHeader()).thenReturn(chainHead);
    assertThat(bonsaiWorldStateArchive.getMutable(blockHeader, false)).isEmpty();
    verify(trieLogManager, Mockito.never()).getWorldState(any(Hash.class));
  }

  @Test
  public void testGetMutableWhenLoadLessThanLimitLayersBack() {

    bonsaiWorldStateArchive =
        new BonsaiWorldStateProvider(
            trieLogManager,
            new BonsaiWorldStateKeyValueStorage(storageProvider, new NoOpMetricsSystem()),
            blockchain,
            new CachedMerkleTrieLoader(new NoOpMetricsSystem()));
    final BlockHeader blockHeader = blockBuilder.number(0).buildHeader();
    final BlockHeader chainHead = blockBuilder.number(511).buildHeader();
    final BonsaiWorldState mockWorldState = mock(BonsaiWorldState.class);
    when(mockWorldState.blockHash()).thenReturn(blockHeader.getHash());
    when(mockWorldState.freeze()).thenReturn(mockWorldState);

    when(trieLogManager.getMaxLayersToLoad()).thenReturn(Long.valueOf(512));
    when(trieLogManager.getWorldState(blockHeader.getHash()))
        .thenReturn(Optional.of(mockWorldState));
    when(blockchain.getChainHeadHeader()).thenReturn(chainHead);
    assertThat(bonsaiWorldStateArchive.getMutable(blockHeader, false))
        .containsInstanceOf(BonsaiWorldState.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  public void testGetMutableWithStorageInconsistencyRollbackTheState() {

    when(keyValueStorage.startTransaction()).thenReturn(mock(KeyValueStorageTransaction.class));
    when(trieLogManager.getTrieLogLayer(any())).thenReturn(Optional.of(mock(TrieLogLayer.class)));

    var worldStateStorage =
        new BonsaiWorldStateKeyValueStorage(storageProvider, new NoOpMetricsSystem());
    bonsaiWorldStateArchive =
        spy(
            new BonsaiWorldStateProvider(
                trieLogManager,
                worldStateStorage,
                blockchain,
                new CachedMerkleTrieLoader(new NoOpMetricsSystem())));
    final BlockHeader blockHeader = blockBuilder.number(0).buildHeader();

    when(blockchain.getBlockHeader(eq(blockHeader.getHash()))).thenReturn(Optional.of(blockHeader));

    assertThat(bonsaiWorldStateArchive.getMutable(null, blockHeader.getHash()))
        .containsInstanceOf(BonsaiWorldState.class);

    // verify is trying to get the trie log layer to rollback
    verify(trieLogManager).getTrieLogLayer(Hash.ZERO);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  public void testGetMutableWithStorageConsistencyNotRollbackTheState() {

    when(keyValueStorage.startTransaction()).thenReturn(mock(KeyValueStorageTransaction.class));

    var worldStateStorage =
        new BonsaiWorldStateKeyValueStorage(storageProvider, new NoOpMetricsSystem());
    bonsaiWorldStateArchive =
        spy(
            new BonsaiWorldStateProvider(
                trieLogManager,
                worldStateStorage,
                blockchain,
                new CachedMerkleTrieLoader(new NoOpMetricsSystem())));

    final BlockHeader blockHeader = blockBuilder.number(0).buildHeader();

    when(blockchain.getBlockHeader(eq(blockHeader.getHash()))).thenReturn(Optional.of(blockHeader));
    when(blockchain.getBlockHeader(eq(Hash.ZERO))).thenReturn(Optional.of(blockHeader));

    assertThat(bonsaiWorldStateArchive.getMutable(null, blockHeader.getHash()))
        .containsInstanceOf(BonsaiWorldState.class);

    // verify is not trying to get the trie log layer to rollback when block is present
    verify(trieLogManager, Mockito.never()).getTrieLogLayer(any());
  }

  @SuppressWarnings({"unchecked"})
  @Test
  public void testGetMutableWithStorageConsistencyToRollbackAndRollForwardTheState() {
    when(keyValueStorage.startTransaction()).thenReturn(mock(KeyValueStorageTransaction.class));
    final BlockHeader genesis = blockBuilder.number(0).buildHeader();
    final BlockHeader blockHeaderChainA =
        blockBuilder.number(1).timestamp(1).parentHash(genesis.getHash()).buildHeader();
    final BlockHeader blockHeaderChainB =
        blockBuilder.number(1).timestamp(2).parentHash(genesis.getHash()).buildHeader();

    when(trieLogManager.getTrieLogLayer(any())).thenReturn(Optional.of(mock(TrieLogLayer.class)));

    var worldStateStorage =
        new BonsaiWorldStateKeyValueStorage(storageProvider, new NoOpMetricsSystem());

    bonsaiWorldStateArchive =
        spy(
            new BonsaiWorldStateProvider(
                trieLogManager,
                worldStateStorage,
                blockchain,
                new CachedMerkleTrieLoader(new NoOpMetricsSystem())));

    // initial persisted state hash key
    when(blockchain.getBlockHeader(eq(Hash.ZERO))).thenReturn(Optional.of(blockHeaderChainA));
    when(blockchain.getBlockHeader(eq(blockHeaderChainB.getHash())))
        .thenReturn(Optional.of(blockHeaderChainB));
    when(blockchain.getBlockHeader(eq(genesis.getHash()))).thenReturn(Optional.of(genesis));

    assertThat(bonsaiWorldStateArchive.getMutable(null, blockHeaderChainB.getHash()))
        .containsInstanceOf(BonsaiWorldState.class);

    // verify is trying to get the trie log layers to rollback and roll forward
    verify(trieLogManager).getTrieLogLayer(eq(blockHeaderChainA.getHash()));
    verify(trieLogManager).getTrieLogLayer(eq(blockHeaderChainB.getHash()));
  }

  @SuppressWarnings({"unchecked"})
  @Test
  // TODO: refactor to test original intent
  @Ignore("needs refactor, getMutable(hash, hash) cannot trigger saveTrieLog")
  public void testGetMutableWithRollbackNotOverrideTrieLogLayer() {
    final KeyValueStorageTransaction keyValueStorageTransaction =
        mock(KeyValueStorageTransaction.class);
    when(keyValueStorage.startTransaction()).thenReturn(keyValueStorageTransaction);
    final BlockHeader genesis = blockBuilder.number(0).buildHeader();
    final BlockHeader blockHeaderChainA =
        blockBuilder.number(1).timestamp(1).parentHash(genesis.getHash()).buildHeader();
    final BlockHeader blockHeaderChainB =
        blockBuilder.number(1).timestamp(2).parentHash(genesis.getHash()).buildHeader();

    when(trieLogManager.getTrieLogLayer(any(Hash.class)))
        .thenReturn(Optional.of(mock(TrieLogLayer.class)));

    bonsaiWorldStateArchive =
        spy(
            new BonsaiWorldStateProvider(
                trieLogManager,
                new BonsaiWorldStateKeyValueStorage(storageProvider, new NoOpMetricsSystem()),
                blockchain,
                new CachedMerkleTrieLoader(new NoOpMetricsSystem())));

    // initial persisted state hash key
    when(blockchain.getBlockHeader(eq(Hash.ZERO))).thenReturn(Optional.of(blockHeaderChainA));
    // fake trie log layer
    final BytesValueRLPOutput rlpLogBlockB = new BytesValueRLPOutput();
    final TrieLogLayer trieLogLayerBlockB = new TrieLogLayer();
    trieLogLayerBlockB.setBlockHash(blockHeaderChainB.getHash());
    trieLogLayerBlockB.writeTo(rlpLogBlockB);
    when(keyValueStorage.get(blockHeaderChainB.getHash().toArrayUnsafe()))
        .thenReturn(Optional.of(rlpLogBlockB.encoded().toArrayUnsafe()));

    when(blockchain.getBlockHeader(eq(blockHeaderChainB.getHash())))
        .thenReturn(Optional.of(blockHeaderChainB));
    when(blockchain.getBlockHeader(eq(genesis.getHash()))).thenReturn(Optional.of(genesis));

    assertThat(bonsaiWorldStateArchive.getMutable(null, blockHeaderChainB.getHash()))
        .containsInstanceOf(BonsaiWorldState.class);

    // verify is not persisting if already present
    verify(keyValueStorageTransaction, never())
        .put(eq(blockHeaderChainA.getHash().toArrayUnsafe()), any());
    verify(keyValueStorageTransaction, never())
        .put(eq(blockHeaderChainB.getHash().toArrayUnsafe()), any());
  }
}
