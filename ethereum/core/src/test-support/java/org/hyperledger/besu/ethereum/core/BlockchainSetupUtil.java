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
package org.hyperledger.besu.ethereum.core;

import static org.assertj.core.util.Preconditions.checkArgument;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createBonsaiInMemoryWorldStateArchive;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.ethereum.ConsensusContext;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.util.RawBlockIterator;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.testutil.BlockTestUtil;
import org.hyperledger.besu.testutil.BlockTestUtil.ChainResources;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.junit.rules.TemporaryFolder;

public class BlockchainSetupUtil {
  private final GenesisState genesisState;
  private final MutableBlockchain blockchain;
  private final ProtocolContext protocolContext;
  private final ProtocolSchedule protocolSchedule;
  private final WorldStateArchive worldArchive;
  private final TransactionPool transactionPool;
  private final List<Block> blocks;
  private final EthScheduler scheduler;
  private long maxBlockNumber;

  private BlockchainSetupUtil(
      final GenesisState genesisState,
      final MutableBlockchain blockchain,
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final WorldStateArchive worldArchive,
      final TransactionPool transactionPool,
      final List<Block> blocks,
      final EthScheduler scheduler) {
    this.genesisState = genesisState;
    this.blockchain = blockchain;
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.worldArchive = worldArchive;
    this.transactionPool = transactionPool;
    this.blocks = blocks;
    this.scheduler = scheduler;
  }

  public Blockchain importAllBlocks() {
    importBlocks(blocks);
    return blockchain;
  }

  public void importFirstBlocks(final int count) {
    importBlocks(blocks.subList(0, count));
  }

  public void importBlockAtIndex(final int index) {
    importBlocks(Collections.singletonList(blocks.get(index)));
  }

  public Block getBlock(final int index) {
    checkArgument(index < blocks.size(), "Invalid block index");
    return blocks.get(index);
  }

  public List<Block> getBlocks() {
    return blocks;
  }

  public int blockCount() {
    return blocks.size();
  }

  public static BlockchainSetupUtil forTesting(final DataStorageFormat storageFormat) {
    return createForEthashChain(BlockTestUtil.getTestChainResources(), storageFormat);
  }

  public static BlockchainSetupUtil forMainnet() {
    return createForEthashChain(BlockTestUtil.getMainnetResources(), DataStorageFormat.FOREST);
  }

  public static BlockchainSetupUtil forOutdatedFork() {
    return createForEthashChain(BlockTestUtil.getOutdatedForkResources(), DataStorageFormat.FOREST);
  }

  public static BlockchainSetupUtil forUpgradedFork() {
    return createForEthashChain(BlockTestUtil.getUpgradedForkResources(), DataStorageFormat.FOREST);
  }

  public static BlockchainSetupUtil createForEthashChain(
      final ChainResources chainResources, final DataStorageFormat storageFormat) {
    return create(
        chainResources,
        storageFormat,
        BlockchainSetupUtil::mainnetProtocolScheduleProvider,
        BlockchainSetupUtil::mainnetProtocolContextProvider,
        new EthScheduler(1, 1, 1, 1, new NoOpMetricsSystem()));
  }

  private static ProtocolSchedule mainnetProtocolScheduleProvider(
      final GenesisConfigFile genesisConfigFile) {
    return MainnetProtocolSchedule.fromConfig(
        genesisConfigFile.getConfigOptions(), EvmConfiguration.DEFAULT);
  }

  private static ProtocolContext mainnetProtocolContextProvider(
      final MutableBlockchain blockchain, final WorldStateArchive worldStateArchive) {
    return new ProtocolContext(
        blockchain,
        worldStateArchive,
        new ConsensusContext() {
          @Override
          public <C extends ConsensusContext> C as(final Class<C> klass) {
            return null;
          }
        });
  }

  private static BlockchainSetupUtil create(
      final ChainResources chainResources,
      final DataStorageFormat storageFormat,
      final ProtocolScheduleProvider protocolScheduleProvider,
      final ProtocolContextProvider protocolContextProvider,
      final EthScheduler scheduler) {
    final TemporaryFolder temp = new TemporaryFolder();
    try {
      temp.create();
      final String genesisJson = Resources.toString(chainResources.getGenesisURL(), Charsets.UTF_8);

      final GenesisConfigFile genesisConfigFile = GenesisConfigFile.fromConfig(genesisJson);
      final ProtocolSchedule protocolSchedule = protocolScheduleProvider.get(genesisConfigFile);

      final GenesisState genesisState = GenesisState.fromJson(genesisJson, protocolSchedule);
      final MutableBlockchain blockchain = createInMemoryBlockchain(genesisState.getBlock());
      final WorldStateArchive worldArchive =
          storageFormat == DataStorageFormat.BONSAI
              ? createBonsaiInMemoryWorldStateArchive(blockchain)
              : createInMemoryWorldStateArchive();
      final TransactionPool transactionPool = mock(TransactionPool.class);

      genesisState.writeStateTo(worldArchive.getMutable());
      final ProtocolContext protocolContext = protocolContextProvider.get(blockchain, worldArchive);

      final Path blocksPath = Path.of(chainResources.getBlocksURL().toURI());
      final List<Block> blocks = new ArrayList<>();
      final BlockHeaderFunctions blockHeaderFunctions =
          ScheduleBasedBlockHeaderFunctions.create(protocolSchedule);
      try (final RawBlockIterator iterator =
          new RawBlockIterator(
              blocksPath, rlp -> BlockHeader.readFrom(rlp, blockHeaderFunctions))) {
        while (iterator.hasNext()) {
          blocks.add(iterator.next());
        }
      }
      return new BlockchainSetupUtil(
          genesisState,
          blockchain,
          protocolContext,
          protocolSchedule,
          worldArchive,
          transactionPool,
          blocks,
          scheduler);
    } catch (final IOException | URISyntaxException ex) {
      throw new IllegalStateException(ex);
    } finally {
      temp.delete();
    }
  }

  public long getMaxBlockNumber() {
    return maxBlockNumber;
  }

  public GenesisState getGenesisState() {
    return genesisState;
  }

  public MutableBlockchain getBlockchain() {
    return blockchain;
  }

  public ProtocolContext getProtocolContext() {
    return protocolContext;
  }

  public ProtocolSchedule getProtocolSchedule() {
    return protocolSchedule;
  }

  public WorldStateArchive getWorldArchive() {
    return worldArchive;
  }

  public EthScheduler getScheduler() {
    return scheduler;
  }

  public TransactionPool getTransactionPool() {
    return transactionPool;
  }

  private void importBlocks(final List<Block> blocks) {
    for (final Block block : blocks) {
      if (block.getHeader().getNumber() == BlockHeader.GENESIS_BLOCK_NUMBER) {
        continue;
      }
      final ProtocolSpec protocolSpec =
          protocolSchedule.getByBlockNumber(block.getHeader().getNumber());
      final BlockImporter blockImporter = protocolSpec.getBlockImporter();
      final boolean result =
          blockImporter.importBlock(protocolContext, block, HeaderValidationMode.FULL);
      if (!result) {
        throw new IllegalStateException("Unable to import block " + block.getHeader().getNumber());
      }
    }
    this.maxBlockNumber = blockchain.getChainHeadBlockNumber();
  }

  private interface ProtocolScheduleProvider {
    ProtocolSchedule get(GenesisConfigFile genesisConfig);
  }

  private interface ProtocolContextProvider {
    ProtocolContext get(MutableBlockchain blockchain, WorldStateArchive worldStateArchive);
  }
}
