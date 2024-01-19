/*
 * Copyright contributors to Hyperledger Besu.
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

package org.hyperledger.besu.cli.subcommands.storage;

import static java.util.Collections.singletonList;
import static org.hyperledger.besu.ethereum.worldstate.DataStorageFormat.BONSAI;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.bonsai.trielog.TrieLogFactoryImpl;
import org.hyperledger.besu.ethereum.trie.bonsai.trielog.TrieLogLayer;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.ImmutableDataStorageConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrieLogHelperTest {

  private static final StorageProvider storageProvider = new InMemoryKeyValueStorageProvider();
  private static BonsaiWorldStateKeyValueStorage inMemoryWorldState;

  @Mock private MutableBlockchain blockchain;

  static BlockHeader blockHeader1;
  static BlockHeader blockHeader2;
  static BlockHeader blockHeader3;
  static BlockHeader blockHeader4;
  static BlockHeader blockHeader5;

  @BeforeEach
  public void setup() throws IOException {

    blockHeader1 = new BlockHeaderTestFixture().number(1).buildHeader();
    blockHeader2 = new BlockHeaderTestFixture().number(2).buildHeader();
    blockHeader3 = new BlockHeaderTestFixture().number(3).buildHeader();
    blockHeader4 = new BlockHeaderTestFixture().number(4).buildHeader();
    blockHeader5 = new BlockHeaderTestFixture().number(5).buildHeader();

    inMemoryWorldState =
        new BonsaiWorldStateKeyValueStorage(
            storageProvider, new NoOpMetricsSystem(), DataStorageConfiguration.DEFAULT_CONFIG);

    createTrieLog(blockHeader1);

    var updater = inMemoryWorldState.updater();
    updater
        .getTrieLogStorageTransaction()
        .put(blockHeader1.getHash().toArrayUnsafe(), createTrieLog(blockHeader1));
    updater
        .getTrieLogStorageTransaction()
        .put(blockHeader2.getHash().toArrayUnsafe(), createTrieLog(blockHeader2));
    updater
        .getTrieLogStorageTransaction()
        .put(blockHeader3.getHash().toArrayUnsafe(), createTrieLog(blockHeader3));
    updater
        .getTrieLogStorageTransaction()
        .put(blockHeader4.getHash().toArrayUnsafe(), createTrieLog(blockHeader4));
    updater
        .getTrieLogStorageTransaction()
        .put(blockHeader5.getHash().toArrayUnsafe(), createTrieLog(blockHeader5));
    updater.getTrieLogStorageTransaction().commit();
  }

  private static byte[] createTrieLog(final BlockHeader blockHeader) {
    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.setBlockHash(blockHeader.getBlockHash());
    final BytesValueRLPOutput rlpLog = new BytesValueRLPOutput();
    TrieLogFactoryImpl.writeTo(trieLogLayer, rlpLog);
    return rlpLog.encoded().toArrayUnsafe();
  }

  void mockBlockchainBase() {
    when(blockchain.getChainHeadBlockNumber()).thenReturn(5L);
    when(blockchain.getFinalized()).thenReturn(Optional.of(blockHeader3.getBlockHash()));
    when(blockchain.getBlockHeader(any(Hash.class))).thenReturn(Optional.of(blockHeader3));
  }

  @Test
  public void prune(final @TempDir Path dataDir) throws IOException {
    Files.createDirectories(dataDir.resolve("database"));

    DataStorageConfiguration dataStorageConfiguration =
        ImmutableDataStorageConfiguration.builder()
            .dataStorageFormat(BONSAI)
            .bonsaiMaxLayersToLoad(2L)
            .unstable(
                ImmutableDataStorageConfiguration.Unstable.builder()
                    .bonsaiTrieLogRetentionThreshold(3)
                    .build()
                    .withBonsaiTrieLogRetentionThreshold(3))
            .build();

    mockBlockchainBase();
    when(blockchain.getBlockHeader(5)).thenReturn(Optional.of(blockHeader5));
    when(blockchain.getBlockHeader(4)).thenReturn(Optional.of(blockHeader4));
    when(blockchain.getBlockHeader(3)).thenReturn(Optional.of(blockHeader3));

    // assert trie logs that will be pruned exist before prune call
    assertArrayEquals(
        inMemoryWorldState.getTrieLog(blockHeader1.getHash()).get(), createTrieLog(blockHeader1));
    assertArrayEquals(
        inMemoryWorldState.getTrieLog(blockHeader2.getHash()).get(), createTrieLog(blockHeader2));
    assertArrayEquals(
        inMemoryWorldState.getTrieLog(blockHeader3.getHash()).get(), createTrieLog(blockHeader3));

    TrieLogHelper.prune(dataStorageConfiguration, inMemoryWorldState, blockchain, dataDir);

    // assert pruned trie logs are not in the DB
    assertEquals(inMemoryWorldState.getTrieLog(blockHeader1.getHash()), Optional.empty());
    assertEquals(inMemoryWorldState.getTrieLog(blockHeader2.getHash()), Optional.empty());

    // assert retained trie logs are in the DB
    assertArrayEquals(
        inMemoryWorldState.getTrieLog(blockHeader3.getHash()).get(), createTrieLog(blockHeader3));
    assertArrayEquals(
        inMemoryWorldState.getTrieLog(blockHeader4.getHash()).get(), createTrieLog(blockHeader4));
    assertArrayEquals(
        inMemoryWorldState.getTrieLog(blockHeader5.getHash()).get(), createTrieLog(blockHeader5));
  }

  @Test
  public void cantPruneIfNoFinalizedIsFound(final @TempDir Path dataDir) {
    DataStorageConfiguration dataStorageConfiguration =
        ImmutableDataStorageConfiguration.builder()
            .dataStorageFormat(BONSAI)
            .bonsaiMaxLayersToLoad(2L)
            .unstable(
                ImmutableDataStorageConfiguration.Unstable.builder()
                    .bonsaiTrieLogRetentionThreshold(2)
                    .build()
                    .withBonsaiTrieLogRetentionThreshold(2))
            .build();

    when(blockchain.getChainHeadBlockNumber()).thenReturn(5L);
    when(blockchain.getFinalized()).thenReturn(Optional.empty());

    assertThrows(
        RuntimeException.class,
        () ->
            TrieLogHelper.prune(dataStorageConfiguration, inMemoryWorldState, blockchain, dataDir));
  }

  @Test
  public void cantPruneIfUserRetainsMoreLayerThanExistingChainLength(final @TempDir Path dataDir) {
    DataStorageConfiguration dataStorageConfiguration =
        ImmutableDataStorageConfiguration.builder()
            .dataStorageFormat(BONSAI)
            .bonsaiMaxLayersToLoad(2L)
            .unstable(
                ImmutableDataStorageConfiguration.Unstable.builder()
                    .bonsaiTrieLogRetentionThreshold(10)
                    .build()
                    .withBonsaiTrieLogRetentionThreshold(10))
            .build();

    when(blockchain.getChainHeadBlockNumber()).thenReturn(5L);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            TrieLogHelper.prune(dataStorageConfiguration, inMemoryWorldState, blockchain, dataDir));
  }

  @Test
  public void cantPruneIfUserRequiredFurtherThanFinalized(final @TempDir Path dataDir) {

    DataStorageConfiguration dataStorageConfiguration =
        ImmutableDataStorageConfiguration.builder()
            .dataStorageFormat(BONSAI)
            .bonsaiMaxLayersToLoad(2L)
            .unstable(
                ImmutableDataStorageConfiguration.Unstable.builder()
                    .bonsaiTrieLogRetentionThreshold(2)
                    .build()
                    .withBonsaiTrieLogRetentionThreshold(2))
            .build();

    mockBlockchainBase();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            TrieLogHelper.prune(dataStorageConfiguration, inMemoryWorldState, blockchain, dataDir));
  }

  @Test
  public void exceptionWhileSavingFileStopsPruneProcess(final @TempDir Path dataDir) {

    DataStorageConfiguration dataStorageConfiguration =
        ImmutableDataStorageConfiguration.builder()
            .dataStorageFormat(BONSAI)
            .bonsaiMaxLayersToLoad(2L)
            .unstable(
                ImmutableDataStorageConfiguration.Unstable.builder()
                    .bonsaiTrieLogRetentionThreshold(2)
                    .build()
                    .withBonsaiTrieLogRetentionThreshold(2))
            .build();

    assertThrows(
        RuntimeException.class,
        () ->
            TrieLogHelper.prune(
                dataStorageConfiguration,
                inMemoryWorldState,
                blockchain,
                dataDir.resolve("unknownPath")));

    // assert all trie logs are still in the DB
    assertArrayEquals(
        inMemoryWorldState.getTrieLog(blockHeader1.getHash()).get(), createTrieLog(blockHeader1));
    assertArrayEquals(
        inMemoryWorldState.getTrieLog(blockHeader2.getHash()).get(), createTrieLog(blockHeader2));
    assertArrayEquals(
        inMemoryWorldState.getTrieLog(blockHeader3.getHash()).get(), createTrieLog(blockHeader3));
    assertArrayEquals(
        inMemoryWorldState.getTrieLog(blockHeader4.getHash()).get(), createTrieLog(blockHeader4));
    assertArrayEquals(
        inMemoryWorldState.getTrieLog(blockHeader5.getHash()).get(), createTrieLog(blockHeader5));
  }

  @Test
  public void exportedTrieMatchesDbTrieLog(final @TempDir Path dataDir) throws IOException {
    TrieLogHelper.exportTrieLog(
        inMemoryWorldState,
        singletonList(blockHeader1.getHash()),
        dataDir.resolve("trie-log-dump"));

    var trieLog =
        TrieLogHelper.readTrieLogsAsRlpFromFile(dataDir.resolve("trie-log-dump").toString())
            .entrySet()
            .stream()
            .findFirst()
            .get();

    assertArrayEquals(trieLog.getKey(), blockHeader1.getHash().toArrayUnsafe());
    assertArrayEquals(
        trieLog.getValue(), inMemoryWorldState.getTrieLog(blockHeader1.getHash()).get());
  }

  @Test
  public void exportedMultipleTriesMatchDbTrieLogs(final @TempDir Path dataDir) throws IOException {
    TrieLogHelper.exportTrieLog(
        inMemoryWorldState,
        List.of(blockHeader1.getHash(), blockHeader2.getHash(), blockHeader3.getHash()),
        dataDir.resolve("trie-log-dump"));

    var trieLogs =
        TrieLogHelper.readTrieLogsAsRlpFromFile(dataDir.resolve("trie-log-dump").toString())
            .entrySet()
            .stream()
            .collect(Collectors.toMap(e -> Bytes.wrap(e.getKey()), Map.Entry::getValue));

    assertArrayEquals(
        trieLogs.get(blockHeader1.getHash()),
        inMemoryWorldState.getTrieLog(blockHeader1.getHash()).get());
    assertArrayEquals(
        trieLogs.get(blockHeader2.getHash()),
        inMemoryWorldState.getTrieLog(blockHeader2.getHash()).get());
    assertArrayEquals(
        trieLogs.get(blockHeader3.getHash()),
        inMemoryWorldState.getTrieLog(blockHeader3.getHash()).get());
  }

  @Test
  public void importedTrieLogMatchesDbTrieLog(final @TempDir Path dataDir) throws IOException {
    StorageProvider tempStorageProvider = new InMemoryKeyValueStorageProvider();
    BonsaiWorldStateKeyValueStorage inMemoryWorldState2 =
        new BonsaiWorldStateKeyValueStorage(tempStorageProvider, new NoOpMetricsSystem());

    TrieLogHelper.exportTrieLog(
        inMemoryWorldState,
        singletonList(blockHeader1.getHash()),
        dataDir.resolve("trie-log-dump"));

    var trieLog =
        TrieLogHelper.readTrieLogsAsRlpFromFile(dataDir.resolve("trie-log-dump").toString());
    var updater = inMemoryWorldState2.updater();

    trieLog.forEach((k, v) -> updater.getTrieLogStorageTransaction().put(k, v));

    updater.getTrieLogStorageTransaction().commit();

    assertArrayEquals(
        inMemoryWorldState2.getTrieLog(blockHeader1.getHash()).get(),
        inMemoryWorldState.getTrieLog(blockHeader1.getHash()).get());
  }

  @Test
  public void importedMultipleTriesMatchDbTrieLogs(final @TempDir Path dataDir) throws IOException {
    StorageProvider tempStorageProvider = new InMemoryKeyValueStorageProvider();
    BonsaiWorldStateKeyValueStorage inMemoryWorldState2 =
        new BonsaiWorldStateKeyValueStorage(tempStorageProvider, new NoOpMetricsSystem());

    TrieLogHelper.exportTrieLog(
        inMemoryWorldState,
        List.of(blockHeader1.getHash(), blockHeader2.getHash(), blockHeader3.getHash()),
        dataDir.resolve("trie-log-dump"));

    var trieLog =
        TrieLogHelper.readTrieLogsAsRlpFromFile(dataDir.resolve("trie-log-dump").toString());
    var updater = inMemoryWorldState2.updater();

    trieLog.forEach((k, v) -> updater.getTrieLogStorageTransaction().put(k, v));

    updater.getTrieLogStorageTransaction().commit();

    assertArrayEquals(
        inMemoryWorldState2.getTrieLog(blockHeader1.getHash()).get(),
        inMemoryWorldState.getTrieLog(blockHeader1.getHash()).get());
    assertArrayEquals(
        inMemoryWorldState2.getTrieLog(blockHeader2.getHash()).get(),
        inMemoryWorldState.getTrieLog(blockHeader2.getHash()).get());
    assertArrayEquals(
        inMemoryWorldState2.getTrieLog(blockHeader3.getHash()).get(),
        inMemoryWorldState.getTrieLog(blockHeader3.getHash()).get());
  }
}
