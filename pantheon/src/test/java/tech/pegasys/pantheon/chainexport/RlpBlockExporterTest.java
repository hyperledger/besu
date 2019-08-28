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
package tech.pegasys.pantheon.chainexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tech.pegasys.pantheon.chainimport.RlpBlockImporter;
import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider;
import tech.pegasys.pantheon.ethereum.core.MiningParametersTestBuilder;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.eth.EthProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPoolConfiguration;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import tech.pegasys.pantheon.ethereum.util.RawBlockIterator;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.testutil.BlockTestUtil;
import tech.pegasys.pantheon.testutil.TestClock;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link BlockExporter}. */
public final class RlpBlockExporterTest {

  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();
  private static Blockchain blockchain;
  private static long chainHead;
  private static ProtocolSchedule<?> protocolSchedule;

  @BeforeClass
  public static void setupBlockchain() throws IOException {
    final PantheonController<?> controller = createController();
    final Path blocks = folder.newFile("1000.blocks").toPath();
    BlockTestUtil.write1000Blocks(blocks);
    blockchain = importBlocks(controller, blocks);
    chainHead = blockchain.getChainHeadBlockNumber();
    protocolSchedule = controller.getProtocolSchedule();
  }

  private static Blockchain importBlocks(
      final PantheonController<?> controller, final Path blocksFile) throws IOException {
    final RlpBlockImporter blockImporter = new RlpBlockImporter();

    blockImporter.importBlockchain(blocksFile, controller);
    return controller.getProtocolContext().getBlockchain();
  }

  private static PantheonController<?> createController() throws IOException {
    final Path dataDir = folder.newFolder().toPath();
    return new PantheonController.Builder()
        .fromGenesisConfig(GenesisConfigFile.mainnet())
        .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
        .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
        .storageProvider(new InMemoryStorageProvider())
        .networkId(BigInteger.ONE)
        .miningParameters(new MiningParametersTestBuilder().enabled(false).build())
        .nodeKeys(KeyPair.generate())
        .metricsSystem(new NoOpMetricsSystem())
        .privacyParameters(PrivacyParameters.DEFAULT)
        .dataDirectory(dataDir)
        .clock(TestClock.fixed())
        .transactionPoolConfiguration(TransactionPoolConfiguration.builder().build())
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
