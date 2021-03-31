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
package org.hyperledger.besu.ethereum.eth.sync;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer.Responder;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;

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
  private static ProtocolSchedule protocolSchedule;
  private static ProtocolContext protocolContext;
  private static final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private static TransactionPool transactionPool;
  private EthProtocolManager ethProtocolManager;
  private Responder responder;
  private RespondingEthPeer respondingPeer;

  @BeforeClass
  public static void setUpClass() {
    final BlockchainSetupUtil blockchainSetupUtil =
        BlockchainSetupUtil.forTesting(DataStorageFormat.FOREST);
    blockchainSetupUtil.importAllBlocks();
    blockchain = blockchainSetupUtil.getBlockchain();
    transactionPool = blockchainSetupUtil.getTransactionPool();
    protocolSchedule = blockchainSetupUtil.getProtocolSchedule();
    protocolContext = blockchainSetupUtil.getProtocolContext();
  }

  @Before
  public void setUpTest() {
    ethProtocolManager =
        EthProtocolManagerTestUtil.create(
            blockchain,
            () -> false,
            protocolContext.getWorldStateArchive(),
            transactionPool,
            EthProtocolConfiguration.defaultConfig());
    responder =
        RespondingEthPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool);
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
