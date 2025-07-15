/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.eth.sync.fullsync.era1prepipeline;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestBuilder;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.sync.fullsync.SyncTerminationCondition;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.pipeline.Pipeline;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Era1ImportPrepipelineFactoryTest {
  private static Path testFilePath;

  protected EthProtocolManager ethProtocolManager;
  protected EthContext ethContext;

  protected MutableBlockchain localBlockchain;

  private Era1ImportPrepipelineFactory era1ImportPrepipelineFactory;

  @BeforeAll
  public static void setupClass() throws URISyntaxException {
    testFilePath =
        Path.of(
                Era1FileSourceTest.class
                    .getClassLoader()
                    .getResource("mainnet-00000-5ec1ffb8.era1")
                    .toURI())
            .getParent();
  }

  @BeforeEach
  public void setupTest() {
    BlockchainSetupUtil localBlockchainSetup = BlockchainSetupUtil.forMainnet();
    localBlockchain = localBlockchainSetup.getBlockchain();

    ProtocolSchedule protocolSchedule = localBlockchainSetup.getProtocolSchedule();
    ProtocolContext protocolContext = localBlockchainSetup.getProtocolContext();
    ethProtocolManager =
        EthProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(localBlockchain)
            .setEthScheduler(new EthScheduler(1, 1, 1, 1, new NoOpMetricsSystem()))
            .setWorldStateArchive(localBlockchainSetup.getWorldArchive())
            .setTransactionPool(localBlockchainSetup.getTransactionPool())
            .setEthereumWireProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .build();
    ethContext = ethProtocolManager.ethContext();
    MetricsSystem metricsSystem = new NoOpMetricsSystem();

    era1ImportPrepipelineFactory =
        new Era1ImportPrepipelineFactory(
            metricsSystem,
            testFilePath,
            protocolSchedule,
            protocolContext,
            ethContext,
            SyncTerminationCondition.never());
  }

  @AfterEach
  public void tearDown() {
    if (ethProtocolManager != null) {
      ethProtocolManager.stop();
    }
  }

  @Test
  public void test() throws ExecutionException, InterruptedException, TimeoutException {
    Pipeline<Path> pipeline =
        era1ImportPrepipelineFactory.createFileImportPipelineForCurrentBlockNumber(0);
    CompletableFuture<Void> pipelineFuture = ethContext.getScheduler().startPipeline(pipeline);
    pipelineFuture.get();

    Assertions.assertEquals(8191, localBlockchain.getChainHeadBlockNumber());
  }
}
