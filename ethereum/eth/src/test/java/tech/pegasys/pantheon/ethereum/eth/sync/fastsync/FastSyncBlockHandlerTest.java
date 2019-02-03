/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.sync.fastsync;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode.LIGHT_SKIP_DETACHED;
import static tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem.NO_OP_LABELLED_TIMER;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.eth.manager.DeterministicEthScheduler;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthMessages;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeers;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.OperationTimer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FastSyncBlockHandlerTest {
  private static final String PROTOCOL_NAME = "ETH";
  private static final BlockBody EMPTY_BODY = new BlockBody(emptyList(), emptyList());
  private static final BlockHeader HEADER = new BlockHeaderTestFixture().buildHeader();
  private static final Block BLOCK = new Block(HEADER, EMPTY_BODY);
  private static final Block BLOCK2 =
      new Block(new BlockHeaderTestFixture().number(2).buildHeader(), EMPTY_BODY);
  private static final Block BLOCK3 =
      new Block(new BlockHeaderTestFixture().number(3).buildHeader(), EMPTY_BODY);

  @SuppressWarnings("unchecked")
  private final ProtocolSchedule<Void> protocolSchedule = mock(ProtocolSchedule.class);

  @SuppressWarnings("unchecked")
  private final ProtocolSpec<Void> protocolSpec = mock(ProtocolSpec.class);

  @SuppressWarnings("unchecked")
  private final BlockImporter<Void> blockImporter = mock(BlockImporter.class);

  private final MutableBlockchain blockchain = mock(MutableBlockchain.class);
  private final WorldStateArchive worldStateArchive = mock(WorldStateArchive.class);
  private final ProtocolContext<Void> protocolContext =
      new ProtocolContext<>(blockchain, worldStateArchive, null);
  private final EthContext ethContext =
      new EthContext(
          PROTOCOL_NAME,
          new EthPeers(PROTOCOL_NAME),
          new EthMessages(),
          new DeterministicEthScheduler());
  private final LabelledMetric<OperationTimer> ethTasksTimer = NO_OP_LABELLED_TIMER;

  private final FastSyncBlockHandler<Void> blockHandler =
      new FastSyncBlockHandler<>(protocolSchedule, protocolContext, ethContext, ethTasksTimer);

  @Before
  public void setUp() {
    when(protocolSchedule.getByBlockNumber(anyLong())).thenReturn(protocolSpec);
    when(protocolSpec.getBlockImporter()).thenReturn(blockImporter);
  }

  @After
  public void checkWorldStateIsUnused() {
    verifyZeroInteractions(worldStateArchive);
  }

  @Test
  public void shouldFastImportBlocks() {
    when(blockImporter.fastImportBlock(protocolContext, BLOCK, emptyList(), LIGHT_SKIP_DETACHED))
        .thenReturn(true);
    final List<BlockWithReceipts> blocksWithReceipts =
        singletonList(new BlockWithReceipts(BLOCK, emptyList()));

    final CompletableFuture<List<BlockWithReceipts>> result =
        blockHandler.validateAndImportBlocks(blocksWithReceipts);

    assertThat(result).isCompleted();
    verify(blockImporter).fastImportBlock(protocolContext, BLOCK, emptyList(), LIGHT_SKIP_DETACHED);
  }

  @Test
  public void shouldReturnExceptionallyCompletedFutureWhenBlockImportFails() {
    when(blockImporter.fastImportBlock(protocolContext, BLOCK, emptyList(), LIGHT_SKIP_DETACHED))
        .thenReturn(false);

    final CompletableFuture<List<BlockWithReceipts>> result =
        blockHandler.validateAndImportBlocks(
            singletonList(new BlockWithReceipts(BLOCK, emptyList())));

    assertThat(result).isCompletedExceptionally();
  }

  @Test
  public void shouldNotContinueImportingBlocksAfterValidationFailure() {
    when(blockImporter.fastImportBlock(protocolContext, BLOCK, emptyList(), LIGHT_SKIP_DETACHED))
        .thenReturn(true);
    when(blockImporter.fastImportBlock(protocolContext, BLOCK2, emptyList(), LIGHT_SKIP_DETACHED))
        .thenReturn(false);

    final CompletableFuture<List<BlockWithReceipts>> result =
        blockHandler.validateAndImportBlocks(
            asList(
                new BlockWithReceipts(BLOCK, emptyList()),
                new BlockWithReceipts(BLOCK2, emptyList()),
                new BlockWithReceipts(BLOCK3, emptyList())));

    assertThat(result).isCompletedExceptionally();

    verify(blockImporter).fastImportBlock(protocolContext, BLOCK, emptyList(), LIGHT_SKIP_DETACHED);
    verify(blockImporter)
        .fastImportBlock(protocolContext, BLOCK2, emptyList(), LIGHT_SKIP_DETACHED);
    verify(blockImporter, never())
        .fastImportBlock(protocolContext, BLOCK3, emptyList(), LIGHT_SKIP_DETACHED);
  }
}
