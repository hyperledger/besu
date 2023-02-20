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
package org.hyperledger.besu.ethereum.bonsai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage.WORLD_BLOCK_HASH_KEY;
import static org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage.WORLD_ROOT_HASH_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SnappableKeyValueStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BonsaiSnapshotWorldStateArchiveTest {

  final BlockHeaderTestFixture blockBuilder = new BlockHeaderTestFixture();

  @Mock Blockchain blockchain;

  @Mock StorageProvider storageProvider;

  @Mock SnappableKeyValueStorage keyValueStorage;

  BonsaiWorldStateProvider bonsaiWorldStateArchive;

  CachedMerkleTrieLoader cachedMerkleTrieLoader;

  @Before
  public void setUp() {
    when(storageProvider.getStorageBySegmentIdentifier(any(KeyValueSegmentIdentifier.class)))
        .thenReturn(keyValueStorage);
    cachedMerkleTrieLoader = new CachedMerkleTrieLoader(new NoOpMetricsSystem());
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
            new BonsaiWorldStateKeyValueStorage(storageProvider),
            blockchain,
            Optional.of(1L),
            true,
            cachedMerkleTrieLoader);

    assertThat(bonsaiWorldStateArchive.getMutable(null, chainHead.getHash(), true))
        .containsInstanceOf(BonsaiPersistedWorldState.class);
  }

  @Test
  public void testGetMutableReturnEmptyWhenLoadMoreThanLimitLayersBack() {
    bonsaiWorldStateArchive =
        new BonsaiWorldStateProvider(
            new BonsaiWorldStateKeyValueStorage(storageProvider),
            blockchain,
            Optional.of(512L),
            cachedMerkleTrieLoader);
    final BlockHeader blockHeader = blockBuilder.number(0).buildHeader();
    final BlockHeader chainHead = blockBuilder.number(512).buildHeader();
    when(blockchain.getBlockHeader(eq(blockHeader.getHash()))).thenReturn(Optional.of(blockHeader));
    when(blockchain.getChainHeadHeader()).thenReturn(chainHead);
    assertThat(bonsaiWorldStateArchive.getMutable(null, blockHeader.getHash(), false)).isEmpty();
  }

  @SuppressWarnings({"unchecked"})
  @Test
  public void testGetMutableWithRollbackNotOverrideTrieLogLayer() {
    final KeyValueStorageTransaction keyValueStorageTransaction =
        mock(KeyValueStorageTransaction.class);
    when(keyValueStorage.startTransaction()).thenReturn(keyValueStorageTransaction);
    final BlockHeader genesis = blockBuilder.number(0).buildHeader();
    final BlockHeader blockHeaderChainA =
        blockBuilder.number(1).timestamp(1).parentHash(genesis.getHash()).buildHeader();
    final BlockHeader blockHeaderChainB =
        blockBuilder.number(1).timestamp(2).parentHash(genesis.getHash()).buildHeader();

    final Map<Bytes32, CachedWorldState<BonsaiSnapshotWorldState>> worldStatesByHash =
        new HashMap<>();
    var mockCachedState =
        new CachedSnapshotWorldState(
            mock(BonsaiSnapshotWorldState.class, Answers.RETURNS_MOCKS),
            mock(TrieLogLayer.class, Answers.RETURNS_MOCKS),
            2);
    worldStatesByHash.put(blockHeaderChainA.getHash(), mockCachedState);
    worldStatesByHash.put(blockHeaderChainB.getHash(), mockCachedState);
    var worldStateStorage = new BonsaiWorldStateKeyValueStorage(storageProvider);
    bonsaiWorldStateArchive =
        spy(
            new BonsaiWorldStateProvider(
                new SnapshotTrieLogManager(blockchain, worldStateStorage, 12L, worldStatesByHash),
                worldStateStorage,
                blockchain,
                true,
                cachedMerkleTrieLoader));
    var worldState = (BonsaiPersistedWorldState) bonsaiWorldStateArchive.getMutable();
    var updater = spy(bonsaiWorldStateArchive.getUpdaterFromPersistedState(worldState));
    when(bonsaiWorldStateArchive.getUpdaterFromPersistedState(worldState)).thenReturn(updater);

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
        .containsInstanceOf(BonsaiPersistedWorldState.class);

    // verify is not persisting if already present
    verify(keyValueStorageTransaction, never())
        .put(eq(blockHeaderChainA.getHash().toArrayUnsafe()), any());
    verify(keyValueStorageTransaction, never())
        .put(eq(blockHeaderChainB.getHash().toArrayUnsafe()), any());
  }
}
