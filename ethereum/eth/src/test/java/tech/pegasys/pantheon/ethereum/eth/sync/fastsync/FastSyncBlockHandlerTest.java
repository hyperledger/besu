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
import static tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode.LIGHT;
import static tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode.LIGHT_SKIP_DETACHED;

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
import tech.pegasys.pantheon.ethereum.eth.sync.ValidationPolicy;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;

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
  private static final HeaderValidationMode VALIDATION_MODE = LIGHT_SKIP_DETACHED;
  private static final HeaderValidationMode OMMER_VALIDATION_MODE = LIGHT;

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
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final ValidationPolicy validationPolicy = mock(ValidationPolicy.class);
  private final ValidationPolicy ommerValidationPolicy = mock(ValidationPolicy.class);

  private final FastSyncBlockHandler<Void> blockHandler =
      new FastSyncBlockHandler<>(
          protocolSchedule,
          protocolContext,
          ethContext,
          metricsSystem,
          validationPolicy,
          ommerValidationPolicy);

  @Before
  public void setUp() {
    when(protocolSchedule.getByBlockNumber(anyLong())).thenReturn(protocolSpec);
    when(protocolSpec.getBlockImporter()).thenReturn(blockImporter);
    when(validationPolicy.getValidationModeForNextBlock()).thenReturn(VALIDATION_MODE);
    when(ommerValidationPolicy.getValidationModeForNextBlock()).thenReturn(OMMER_VALIDATION_MODE);
  }

  @After
  public void checkWorldStateIsUnused() {
    verifyZeroInteractions(worldStateArchive);
  }

  @Test
  public void shouldFastImportBlocks() {
    when(blockImporter.fastImportBlock(
            protocolContext, BLOCK, emptyList(), VALIDATION_MODE, OMMER_VALIDATION_MODE))
        .thenReturn(true);
    final List<BlockWithReceipts> blocksWithReceipts =
        singletonList(new BlockWithReceipts(BLOCK, emptyList()));

    final CompletableFuture<List<BlockWithReceipts>> result =
        blockHandler.validateAndImportBlocks(blocksWithReceipts);

    assertThat(result).isCompleted();
    verify(blockImporter)
        .fastImportBlock(
            protocolContext, BLOCK, emptyList(), VALIDATION_MODE, OMMER_VALIDATION_MODE);
  }

  @Test
  public void shouldReturnExceptionallyCompletedFutureWhenBlockImportFails() {
    when(blockImporter.fastImportBlock(
            protocolContext, BLOCK, emptyList(), VALIDATION_MODE, OMMER_VALIDATION_MODE))
        .thenReturn(false);

    final CompletableFuture<List<BlockWithReceipts>> result =
        blockHandler.validateAndImportBlocks(
            singletonList(new BlockWithReceipts(BLOCK, emptyList())));

    assertThat(result).isCompletedExceptionally();
  }

  @Test
  public void shouldNotContinueImportingBlocksAfterValidationFailure() {
    when(blockImporter.fastImportBlock(
            protocolContext, BLOCK, emptyList(), VALIDATION_MODE, OMMER_VALIDATION_MODE))
        .thenReturn(true);
    when(blockImporter.fastImportBlock(
            protocolContext, BLOCK2, emptyList(), VALIDATION_MODE, OMMER_VALIDATION_MODE))
        .thenReturn(false);

    final CompletableFuture<List<BlockWithReceipts>> result =
        blockHandler.validateAndImportBlocks(
            asList(
                new BlockWithReceipts(BLOCK, emptyList()),
                new BlockWithReceipts(BLOCK2, emptyList()),
                new BlockWithReceipts(BLOCK3, emptyList())));

    assertThat(result).isCompletedExceptionally();

    verify(blockImporter)
        .fastImportBlock(
            protocolContext, BLOCK, emptyList(), VALIDATION_MODE, OMMER_VALIDATION_MODE);
    verify(blockImporter)
        .fastImportBlock(
            protocolContext, BLOCK2, emptyList(), VALIDATION_MODE, OMMER_VALIDATION_MODE);
    verify(blockImporter, never())
        .fastImportBlock(
            protocolContext, BLOCK3, emptyList(), VALIDATION_MODE, OMMER_VALIDATION_MODE);
  }
}
