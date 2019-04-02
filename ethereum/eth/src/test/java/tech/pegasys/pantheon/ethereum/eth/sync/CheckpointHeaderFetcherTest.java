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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer.Responder;
import tech.pegasys.pantheon.ethereum.eth.manager.ethtaskutils.BlockchainSetupUtil;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CheckpointHeaderFetcherTest {
  private static Blockchain blockchain;
  private static ProtocolSchedule<Void> protocolSchedule;
  private static ProtocolContext<Void> protocolContext;
  private static final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  @Mock private UnaryOperator<List<BlockHeader>> filter;
  private EthProtocolManager ethProtocolManager;
  private CheckpointHeaderFetcher checkpointHeaderFetcher;

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
    final EthContext ethContext = ethProtocolManager.ethContext();
    checkpointHeaderFetcher =
        new CheckpointHeaderFetcher(
            SynchronizerConfiguration.builder()
                .downloaderChainSegmentSize(5)
                .downloaderHeadersRequestSize(3)
                .build(),
            protocolSchedule,
            ethContext,
            filter,
            metricsSystem);
  }

  @Test
  public void shouldRequestHeadersFromPeerAndExcludeExistingHeader() {
    when(filter.apply(any())).thenAnswer(invocation -> invocation.getArgument(0));
    final Responder responder =
        RespondingEthPeer.blockchainResponder(blockchain, protocolContext.getWorldStateArchive());
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    final CompletableFuture<List<BlockHeader>> result =
        checkpointHeaderFetcher.getNextCheckpointHeaders(respondingPeer.getEthPeer(), header(1));

    assertThat(result).isNotDone();

    respondingPeer.respond(responder);

    assertThat(result).isCompletedWithValue(asList(header(6), header(11), header(16)));
  }

  @Test
  public void shouldApplyFilterToDownloadedCheckpoints() {
    final List<BlockHeader> filteredResult = asList(header(7), header(9));
    final List<BlockHeader> unfilteredResult = asList(header(6), header(11), header(16));
    when(filter.apply(unfilteredResult)).thenReturn(filteredResult);
    final Responder responder =
        RespondingEthPeer.blockchainResponder(blockchain, protocolContext.getWorldStateArchive());
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    final CompletableFuture<List<BlockHeader>> result =
        checkpointHeaderFetcher.getNextCheckpointHeaders(respondingPeer.getEthPeer(), header(1));

    assertThat(result).isNotDone();

    respondingPeer.respond(responder);

    assertThat(result).isCompletedWithValue(filteredResult);
  }

  private BlockHeader header(final long blockNumber) {
    return blockchain.getBlockHeader(blockNumber).get();
  }
}
