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
package org.hyperledger.besu.chainexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.chainimport.RlpBlockImporter;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.util.RawBlockIterator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.testutil.BlockTestUtil;
import org.hyperledger.besu.testutil.TestClock;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link BlockExporter}. */
@RunWith(MockitoJUnitRunner.class)
public final class RlpBlockExporterTest {

  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();
  private static Blockchain blockchain;
  private static long chainHead;
  private static ProtocolSchedule protocolSchedule;

  @BeforeClass
  public static void setupBlockchain() throws IOException {
    final BesuController controller = createController();
    final Path blocks = folder.newFile("1000.blocks").toPath();
    BlockTestUtil.write1000Blocks(blocks);
    blockchain = importBlocks(controller, blocks);
    chainHead = blockchain.getChainHeadBlockNumber();
    protocolSchedule = controller.getProtocolSchedule();
  }

  private static Blockchain importBlocks(final BesuController controller, final Path blocksFile)
      throws IOException {
    final RlpBlockImporter blockImporter = new RlpBlockImporter();

    blockImporter.importBlockchain(blocksFile, controller, false);
    return controller.getProtocolContext().getBlockchain();
  }

  private static BesuController createController() throws IOException {
    final Path dataDir = folder.newFolder().toPath();
    return new BesuController.Builder()
        .fromGenesisConfig(GenesisConfigFile.mainnet())
        .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
        .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
        .storageProvider(new InMemoryKeyValueStorageProvider())
        .networkId(BigInteger.ONE)
        .miningParameters(new MiningParameters.Builder().miningEnabled(false).build())
        .nodeKey(NodeKeyUtils.generate())
        .metricsSystem(new NoOpMetricsSystem())
        .privacyParameters(PrivacyParameters.DEFAULT)
        .dataDirectory(dataDir)
        .clock(TestClock.fixed())
        .transactionPoolConfiguration(TransactionPoolConfiguration.DEFAULT)
        .gasLimitCalculator(GasLimitCalculator.constant())
        .evmConfiguration(EvmConfiguration.DEFAULT)
        .build();
  }

  @Test
  public void exportBlocks_noBounds() throws IOException {
    final File outputPath = folder.newFile();
    final RlpBlockExporter exporter = new RlpBlockExporter(blockchain);
    exporter.exportBlocks(outputPath, Optional.empty(), Optional.empty());

    // Iterate over blocks and check that they match expectations
    final RawBlockIterator blockIterator = getBlockIterator(outputPath.toPath());
    long currentBlockNumber = 0;
    while (blockIterator.hasNext()) {
      final Block actual = blockIterator.next();
      final Block expected = getBlock(blockchain, currentBlockNumber);
      assertThat(actual).isEqualTo(expected);
      currentBlockNumber++;
    }

    // Check that we iterated to the end of the chain
    assertThat(currentBlockNumber).isEqualTo(chainHead + 1L);
  }

  @Test
  public void exportBlocks_withLowerBound() throws IOException {
    final File outputPath = folder.newFile();
    final RlpBlockExporter exporter = new RlpBlockExporter(blockchain);

    final long lowerBound = 990;
    exporter.exportBlocks(outputPath, Optional.of(lowerBound), Optional.empty());

    // Iterate over blocks and check that they match expectations
    final RawBlockIterator blockIterator = getBlockIterator(outputPath.toPath());
    long currentBlockNumber = lowerBound;
    while (blockIterator.hasNext()) {
      final Block actual = blockIterator.next();
      final Block expected = getBlock(blockchain, currentBlockNumber);
      assertThat(actual).isEqualTo(expected);
      currentBlockNumber++;
    }

    // Check that we iterated to the end of the chain
    assertThat(currentBlockNumber).isEqualTo(chainHead + 1L);
  }

  @Test
  public void exportBlocks_withUpperBound() throws IOException {
    final File outputPath = folder.newFile();
    final RlpBlockExporter exporter = new RlpBlockExporter(blockchain);

    final long upperBound = 10;
    exporter.exportBlocks(outputPath, Optional.empty(), Optional.of(upperBound));

    // Iterate over blocks and check that they match expectations
    final RawBlockIterator blockIterator = getBlockIterator(outputPath.toPath());
    long currentBlockNumber = 0;
    while (blockIterator.hasNext()) {
      final Block actual = blockIterator.next();
      final Block expected = getBlock(blockchain, currentBlockNumber);
      assertThat(actual).isEqualTo(expected);
      currentBlockNumber++;
    }

    // Check that we iterated to the end of the chain
    assertThat(currentBlockNumber).isEqualTo(upperBound);
  }

  @Test
  public void exportBlocks_withUpperAndLowerBounds() throws IOException {
    final File outputPath = folder.newFile();
    final RlpBlockExporter exporter = new RlpBlockExporter(blockchain);

    final long lowerBound = 5;
    final long upperBound = 10;
    exporter.exportBlocks(outputPath, Optional.of(lowerBound), Optional.of(upperBound));

    // Iterate over blocks and check that they match expectations
    final RawBlockIterator blockIterator = getBlockIterator(outputPath.toPath());
    long currentBlockNumber = lowerBound;
    while (blockIterator.hasNext()) {
      final Block actual = blockIterator.next();
      final Block expected = getBlock(blockchain, currentBlockNumber);
      assertThat(actual).isEqualTo(expected);
      currentBlockNumber++;
    }

    // Check that we iterated to the end of the chain
    assertThat(currentBlockNumber).isEqualTo(upperBound);
  }

  @Test
  public void exportBlocks_withRangeBeyondChainHead() throws IOException {
    final File outputPath = folder.newFile();
    final RlpBlockExporter exporter = new RlpBlockExporter(blockchain);

    final long lowerBound = chainHead - 10;
    final long upperBound = chainHead + 10;
    exporter.exportBlocks(outputPath, Optional.of(lowerBound), Optional.of(upperBound));

    // Iterate over blocks and check that they match expectations
    final RawBlockIterator blockIterator = getBlockIterator(outputPath.toPath());
    long currentBlockNumber = lowerBound;
    while (blockIterator.hasNext()) {
      final Block actual = blockIterator.next();
      final Block expected = getBlock(blockchain, currentBlockNumber);
      assertThat(actual).isEqualTo(expected);
      currentBlockNumber++;
    }

    // Check that we iterated to the end of the chain
    assertThat(currentBlockNumber).isEqualTo(chainHead + 1L);
  }

  @Test
  public void exportBlocks_negativeStartNumber() throws IOException {
    final File outputPath = folder.newFile();
    final RlpBlockExporter exporter = new RlpBlockExporter(blockchain);

    assertThatThrownBy(() -> exporter.exportBlocks(outputPath, Optional.of(-1L), Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("greater than 0");
  }

  @Test
  public void exportBlocks_negativeEndNumber() throws IOException {
    final File outputPath = folder.newFile();
    final RlpBlockExporter exporter = new RlpBlockExporter(blockchain);

    assertThatThrownBy(() -> exporter.exportBlocks(outputPath, Optional.empty(), Optional.of(-1L)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("greater than 0");
  }

  @Test
  public void exportBlocks_outOfOrderBounds() throws IOException {
    final File outputPath = folder.newFile();
    final RlpBlockExporter exporter = new RlpBlockExporter(blockchain);

    assertThatThrownBy(() -> exporter.exportBlocks(outputPath, Optional.of(10L), Optional.of(2L)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Start block must be less than end block");
  }

  private RawBlockIterator getBlockIterator(final Path blocks) throws IOException {
    return new RawBlockIterator(
        blocks,
        rlp ->
            BlockHeader.readFrom(rlp, ScheduleBasedBlockHeaderFunctions.create(protocolSchedule)));
  }

  private Block getBlock(final Blockchain blockchain, final long blockNumber) {
    final BlockHeader header = blockchain.getBlockHeader(blockNumber).get();
    final BlockBody body = blockchain.getBlockBody(header.getHash()).get();
    return new Block(header, body);
  }
}
