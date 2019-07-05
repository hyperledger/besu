/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.util;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider;
import tech.pegasys.pantheon.ethereum.core.MiningParametersTestBuilder;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.eth.EthProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPoolConfiguration;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.testutil.BlockTestUtil;
import tech.pegasys.pantheon.testutil.TestClock;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link BlockExporter}. */
public final class BlockExporterTest {

  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();

  private static PantheonController<?> targetController;

  private final BlockExporter blockExporter = new BlockExporter();

  @BeforeClass
  public static void initPantheonController() throws IOException {
    final BlockImporter blockImporter = new BlockImporter();
    final Path dataDir = folder.newFolder().toPath();
    final Path source = dataDir.resolve("1000.blocks");
    BlockTestUtil.write1000Blocks(source);
    targetController =
        new PantheonController.Builder()
            .fromGenesisConfig(GenesisConfigFile.mainnet())
            .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
            .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .storageProvider(new InMemoryStorageProvider())
            .networkId(1)
            .miningParameters(new MiningParametersTestBuilder().enabled(false).build())
            .nodeKeys(SECP256K1.KeyPair.generate())
            .metricsSystem(new NoOpMetricsSystem())
            .privacyParameters(PrivacyParameters.DEFAULT)
            .dataDirectory(dataDir)
            .clock(TestClock.fixed())
            .transactionPoolConfiguration(TransactionPoolConfiguration.builder().build())
            .build();
    blockImporter.importBlockchain(source, targetController);
  }

  @Test
  public void callingBlockExporterWithOnlyStartBlockShouldReturnOneBlock() throws Exception {

    final long startBlock = 0L;

    final MutableBlockchain blockchain = targetController.getProtocolContext().getBlockchain();

    final Block blockFromBlockchain =
        blockchain.getBlockByHash(blockchain.getBlockHashByNumber(startBlock).get());

    BlockExporter.ExportResult exportResult =
        blockExporter.exportBlockchain(targetController, startBlock, null);

    assertThat(exportResult.blocks).contains(blockFromBlockchain);
  }

  @Test
  public void callingBlockExporterWithStartBlockAndBlockShouldReturnSeveralBlocks()
      throws Exception {

    final long startBlock = 0L;
    final long endBlock = 1L;

    final MutableBlockchain blockchain = targetController.getProtocolContext().getBlockchain();

    final Block blockFromBlockchain =
        blockchain.getBlockByHash(blockchain.getBlockHashByNumber(startBlock).get());

    final Block secondBlockFromBlockchain =
        blockchain.getBlockByHash(blockchain.getBlockHashByNumber(endBlock - 1).get());

    BlockExporter.ExportResult exportResult =
        blockExporter.exportBlockchain(targetController, startBlock, endBlock);

    assertThat(exportResult.blocks)
        .contains(blockFromBlockchain)
        .contains(secondBlockFromBlockchain);
  }
}
