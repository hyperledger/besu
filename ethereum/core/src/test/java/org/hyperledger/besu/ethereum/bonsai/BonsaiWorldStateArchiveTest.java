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
import static org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage.WORLD_BLOCK_HASH_KEY;
import static org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage.WORLD_ROOT_HASH_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BonsaiWorldStateArchiveTest {

  final BlockHeaderTestFixture blockBuilder = new BlockHeaderTestFixture();

  @Mock Blockchain blockchain;

  @Mock StorageProvider storageProvider;

  @Mock KeyValueStorage keyValueStorage;

  BonsaiWorldStateArchive bonsaiWorldStateArchive;

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
    bonsaiWorldStateArchive = new BonsaiWorldStateArchive(storageProvider, blockchain, 1);

    assertThat(bonsaiWorldStateArchive.getMutable(null, chainHead.getHash(), true))
        .containsInstanceOf(BonsaiPersistedWorldState.class);
  }

  @Test
  public void testGetMutableReturnEmptyWhenLoadMoreThanLimitLayersBack() {
    bonsaiWorldStateArchive = new BonsaiWorldStateArchive(storageProvider, blockchain, 512);
    final BlockHeader blockHeader = blockBuilder.number(0).buildHeader();
    final BlockHeader chainHead = blockBuilder.number(512).buildHeader();
    when(blockchain.getBlockHeader(eq(blockHeader.getHash()))).thenReturn(Optional.of(blockHeader));
    when(blockchain.getChainHeadHeader()).thenReturn(chainHead);
    assertThat(bonsaiWorldStateArchive.getMutable(null, blockHeader.getHash(), false)).isEmpty();
  }

  @Test
  public void testGetMutableWhenLoadLessThanLimitLayersBack() {
    bonsaiWorldStateArchive = new BonsaiWorldStateArchive(storageProvider, blockchain, 512);
    final BlockHeader blockHeader = blockBuilder.number(0).buildHeader();
    final BlockHeader chainHead = blockBuilder.number(511).buildHeader();

    final BytesValueRLPOutput rlpLog = new BytesValueRLPOutput();
    final TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.setBlockHash(blockHeader.getHash());
    trieLogLayer.writeTo(rlpLog);
    when(keyValueStorage.get(blockHeader.getHash().toArrayUnsafe()))
        .thenReturn(Optional.of(rlpLog.encoded().toArrayUnsafe()));

    when(blockchain.getBlockHeader(eq(blockHeader.getHash()))).thenReturn(Optional.of(blockHeader));
    when(blockchain.getChainHeadHeader()).thenReturn(chainHead);
    assertThat(bonsaiWorldStateArchive.getMutable(null, blockHeader.getHash(), false))
        .containsInstanceOf(BonsaiLayeredWorldState.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  public void testGetMutableWithStorageInconsistencyRollbackTheState() {

    when(keyValueStorage.startTransaction()).thenReturn(mock(KeyValueStorageTransaction.class));
    final Map layeredWorldStatesByHash = mock(HashMap.class);

    bonsaiWorldStateArchive =
        new BonsaiWorldStateArchive(storageProvider, blockchain, 12, layeredWorldStatesByHash);
    final BlockHeader blockHeader = blockBuilder.number(0).buildHeader();
    final BytesValueRLPOutput rlpLog = new BytesValueRLPOutput();
    final TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.setBlockHash(blockHeader.getHash());
    trieLogLayer.writeTo(rlpLog);

    when(blockchain.getBlockHeader(eq(blockHeader.getHash()))).thenReturn(Optional.of(blockHeader));

    assertThat(bonsaiWorldStateArchive.getMutable(null, blockHeader.getHash()))
        .containsInstanceOf(BonsaiPersistedWorldState.class);

    // verify is trying to get the trie log layer to rollback
    verify(layeredWorldStatesByHash).containsKey(Hash.ZERO);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  public void testGetMutableWithStorageConsistencyNotRollbackTheState() {

    when(keyValueStorage.startTransaction()).thenReturn(mock(KeyValueStorageTransaction.class));
    final Map layeredWorldStatesByHash = mock(HashMap.class);

    bonsaiWorldStateArchive =
        new BonsaiWorldStateArchive(storageProvider, blockchain, 12, layeredWorldStatesByHash);
    final BlockHeader blockHeader = blockBuilder.number(0).buildHeader();
    final BytesValueRLPOutput rlpLog = new BytesValueRLPOutput();
    final TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.setBlockHash(blockHeader.getHash());
    trieLogLayer.writeTo(rlpLog);

    when(blockchain.getBlockHeader(eq(blockHeader.getHash()))).thenReturn(Optional.of(blockHeader));
    when(blockchain.getBlockHeader(eq(Hash.ZERO))).thenReturn(Optional.of(blockHeader));

    assertThat(bonsaiWorldStateArchive.getMutable(null, blockHeader.getHash()))
        .containsInstanceOf(BonsaiPersistedWorldState.class);

    // verify is not trying to get the trie log layer to rollback when block is present
    verify(layeredWorldStatesByHash).entrySet();
    verifyNoMoreInteractions(layeredWorldStatesByHash);
  }
}
