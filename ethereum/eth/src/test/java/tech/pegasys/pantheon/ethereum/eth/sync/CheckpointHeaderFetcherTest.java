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
package tech.pegasys.pantheon.ethereum.eth.sync;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockchainSetupUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer.Responder;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CheckpointHeaderFetcherTest {

  private static final int SEGMENT_SIZE = 5;
  private static Blockchain blockchain;
  private static ProtocolSchedule<Void> protocolSchedule;
  private static ProtocolContext<Void> protocolContext;
  private static final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private EthProtocolManager ethProtocolManager;
  private Responder responder;
  private RespondingEthPeer respondingPeer;

  @BeforeClass
  public static void setUpClass() {
    final BlockchainSetupUtil<Void> blockchainSetupUtil = BlockchainSetupUtil.forTesting();
    blockchainSetupUtil.importAllBlocks();
    blockchain = blockchainSetupUtil.getBlockchain();
    protocolSchedule = blockchainSetupUtil.getProtocolSchedule();
    protocolContext = blockchainSetupUtil.getProtocolContext();
  }

  @Before
  public void setUpTest() {
    ethProtocolManager =
        EthProtocolManagerTestUtil.create(
            blockchain, protocolContext.getWorldStateArchive(), () -> false);
    responder =
        RespondingEthPeer.blockchainResponder(blockchain, protocolContext.getWorldStateArchive());
    respondingPeer =
        EthProtocolManagerTestUtil.createPeer(
            ethProtocolManager, blockchain.getChainHeadBlockNumber());
  }

  @Test
  public void shouldRequestHeadersFromPeerAndExcludeExistingHeader() {
    final CheckpointHeaderFetcher checkpointHeaderFetcher =
        createCheckpointHeaderFetcher(Optional.empty());

    final CompletableFuture<List<BlockHeader>> result =
        checkpointHeaderFetcher.getNextCheckpointHeaders(respondingPeer.getEthPeer(), header(1));

    assertThat(result).isNotDone();

    respondingPeer.respond(responder);

    assertThat(result).isCompletedWithValue(asList(header(6), header(11), header(16)));
  }

  @Test
  public void shouldNotRequestHeadersBeyondTargetWhenTargetIsMultipleOfSegmentSize() {
    final CheckpointHeaderFetcher checkpointHeaderFetcher =
        createCheckpointHeaderFetcher(Optional.of(header(11)));

    final CompletableFuture<List<BlockHeader>> result =
        checkpointHeaderFetcher.getNextCheckpointHeaders(respondingPeer.getEthPeer(), header(1));

    respondingPeer.respond(responder);

    assertThat(result).isCompletedWithValue(asList(header(6), header(11)));
  }

  @Test
  public void shouldNotRequestHeadersBeyondTargetWhenTargetIsNotAMultipleOfSegmentSize() {
    final CheckpointHeaderFetcher checkpointHeaderFetcher =
        createCheckpointHeaderFetcher(Optional.of(header(15)));

    final CompletableFuture<List<BlockHeader>> result =
        checkpointHeaderFetcher.getNextCheckpointHeaders(respondingPeer.getEthPeer(), header(1));

    respondingPeer.respond(responder);

    assertThat(result).isCompletedWithValue(asList(header(6), header(11)));
  }

  @Test
  public void shouldReturnOnlyTargetHeaderWhenLastHeaderIsTheCheckpointBeforeTarget() {
    final CheckpointHeaderFetcher checkpointHeaderFetcher =
        createCheckpointHeaderFetcher(Optional.of(header(15)));

    final CompletableFuture<List<BlockHeader>> result =
        checkpointHeaderFetcher.getNextCheckpointHeaders(respondingPeer.getEthPeer(), header(11));

    assertThat(result).isCompletedWithValue(singletonList(header(15)));
  }

  @Test
  public void shouldReturnEmptyListWhenLastHeaderIsTarget() {
    final CheckpointHeaderFetcher checkpointHeaderFetcher =
        createCheckpointHeaderFetcher(Optional.of(header(15)));

    final CompletableFuture<List<BlockHeader>> result =
        checkpointHeaderFetcher.getNextCheckpointHeaders(respondingPeer.getEthPeer(), header(15));
    assertThat(result).isCompletedWithValue(emptyList());
  }

  @Test
  public void shouldReturnEmptyListWhenLastHeaderIsAfterTarget() {
    final CheckpointHeaderFetcher checkpointHeaderFetcher =
        createCheckpointHeaderFetcher(Optional.of(header(15)));

    final CompletableFuture<List<BlockHeader>> result =
        checkpointHeaderFetcher.getNextCheckpointHeaders(respondingPeer.getEthPeer(), header(16));
    assertThat(result).isCompletedWithValue(emptyList());
  }

  @Test
  public void nextCheckpointShouldEndAtChainHeadWhenNextCheckpointHeaderIsAfterHead() {
    final long remoteChainHeight = blockchain.getChainHeadBlockNumber();
    final CheckpointHeaderFetcher checkpointHeaderFetcher =
        createCheckpointHeaderFetcher(Optional.empty());

    assertThat(
            checkpointHeaderFetcher.nextCheckpointEndsAtChainHead(
                respondingPeer.getEthPeer(), header(remoteChainHeight - SEGMENT_SIZE + 1)))
        .isTrue();
  }

  @Test
  public void nextCheckpointShouldNotEndAtChainHeadWhenAFinalCheckpointHeaderIsSpecified() {
    final long remoteChainHeight = blockchain.getChainHeadBlockNumber();
    final CheckpointHeaderFetcher checkpointHeaderFetcher =
        createCheckpointHeaderFetcher(Optional.of(header(remoteChainHeight)));

    assertThat(
            checkpointHeaderFetcher.nextCheckpointEndsAtChainHead(
                respondingPeer.getEthPeer(), header(remoteChainHeight - SEGMENT_SIZE + 1)))
        .isFalse();
  }

  @Test
  public void shouldReturnRemoteChainHeadWhenNextCheckpointHeaderIsTheRemoteHead() {
    final long remoteChainHeight = blockchain.getChainHeadBlockNumber();
    final CheckpointHeaderFetcher checkpointHeaderFetcher =
        createCheckpointHeaderFetcher(Optional.empty());

    assertThat(
            checkpointHeaderFetcher.nextCheckpointEndsAtChainHead(
                respondingPeer.getEthPeer(), header(remoteChainHeight - SEGMENT_SIZE)))
        .isFalse();

    final CompletableFuture<List<BlockHeader>> result =
        checkpointHeaderFetcher.getNextCheckpointHeaders(
            respondingPeer.getEthPeer(), header(remoteChainHeight - SEGMENT_SIZE));

    respondingPeer.respond(responder);

    assertThat(result).isCompletedWithValue(singletonList(header(remoteChainHeight)));
  }

  private CheckpointHeaderFetcher createCheckpointHeaderFetcher(
      final Optional<BlockHeader> targetHeader) {
    final EthContext ethContext = ethProtocolManager.ethContext();
    return new CheckpointHeaderFetcher(
        SynchronizerConfiguration.builder()
            .downloaderChainSegmentSize(SEGMENT_SIZE)
            .downloaderHeadersRequestSize(3)
            .build(),
        protocolSchedule,
        ethContext,
        targetHeader,
        metricsSystem);
  }

  private BlockHeader header(final long blockNumber) {
    return blockchain.getBlockHeader(blockNumber).get();
  }
}
