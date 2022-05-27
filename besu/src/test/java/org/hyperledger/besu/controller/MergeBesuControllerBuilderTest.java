/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.LondonFeeMarket;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStatePreimageStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.math.BigInteger;
import java.time.Clock;
import java.util.Collections;
import java.util.Optional;
import java.util.OptionalLong;

import com.google.common.collect.Range;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MergeBesuControllerBuilderTest {

  private MergeBesuControllerBuilder besuControllerBuilder;

  @Mock GenesisConfigFile genesisConfigFile;
  @Mock GenesisConfigOptions genesisConfigOptions;
  @Mock SynchronizerConfiguration synchronizerConfiguration;
  @Mock EthProtocolConfiguration ethProtocolConfiguration;
  @Mock MiningParameters miningParameters;
  @Mock ObservableMetricsSystem observableMetricsSystem;
  @Mock PrivacyParameters privacyParameters;
  @Mock Clock clock;
  @Mock TransactionPoolConfiguration poolConfiguration;
  @Mock NodeKey nodeKey;
  @Mock StorageProvider storageProvider;
  @Mock GasLimitCalculator gasLimitCalculator;
  @Mock WorldStateStorage worldStateStorage;
  @Mock WorldStatePreimageStorage worldStatePreimageStorage;

  BigInteger networkId = BigInteger.ONE;
  private final BlockHeaderTestFixture headerGenerator = new BlockHeaderTestFixture();
  private final BaseFeeMarket feeMarket = new LondonFeeMarket(0, Optional.of(Wei.of(42)));

  @Rule public final TemporaryFolder tempDirRule = new TemporaryFolder();

  @Before
  public void setup() {
    when(genesisConfigFile.getParentHash()).thenReturn(Hash.ZERO.toHexString());
    when(genesisConfigFile.getDifficulty()).thenReturn(Bytes.of(0).toHexString());
    when(genesisConfigFile.getExtraData()).thenReturn(Bytes.EMPTY.toHexString());
    when(genesisConfigFile.getMixHash()).thenReturn(Hash.ZERO.toHexString());
    when(genesisConfigFile.getNonce()).thenReturn(Long.toHexString(1));
    when(genesisConfigFile.getConfigOptions(any())).thenReturn(genesisConfigOptions);
    when(genesisConfigOptions.getTerminalTotalDifficulty())
        .thenReturn((Optional.of(UInt256.valueOf(100L))));
    when(genesisConfigOptions.getThanosBlockNumber()).thenReturn(OptionalLong.empty());
    when(genesisConfigOptions.getTerminalBlockHash()).thenReturn(Optional.of(Hash.ZERO));
    when(genesisConfigOptions.getTerminalBlockNumber()).thenReturn(OptionalLong.of(1L));
    when(storageProvider.createBlockchainStorage(any()))
        .thenReturn(
            new KeyValueStoragePrefixedKeyBlockchainStorage(
                new InMemoryKeyValueStorage(), new MainnetBlockHeaderFunctions()));
    when(synchronizerConfiguration.getDownloaderParallelism()).thenReturn(1);
    when(synchronizerConfiguration.getTransactionsParallelism()).thenReturn(1);
    when(synchronizerConfiguration.getComputationParallelism()).thenReturn(1);

    when(synchronizerConfiguration.getBlockPropagationRange()).thenReturn(Range.closed(1L, 2L));

    when(observableMetricsSystem.createLabelledCounter(
            any(), anyString(), anyString(), anyString()))
        .thenReturn(labels -> null);

    when(storageProvider.createWorldStateStorage(DataStorageFormat.FOREST))
        .thenReturn(worldStateStorage);
    when(storageProvider.createWorldStatePreimageStorage()).thenReturn(worldStatePreimageStorage);

    when(worldStateStorage.isWorldStateAvailable(any(), any())).thenReturn(true);
    when(worldStatePreimageStorage.updater())
        .thenReturn(mock(WorldStatePreimageStorage.Updater.class));
    when(worldStateStorage.updater()).thenReturn(mock(WorldStateStorage.Updater.class));

    besuControllerBuilder = visitWithMockConfigs(new MergeBesuControllerBuilder());
  }

  MergeBesuControllerBuilder visitWithMockConfigs(final MergeBesuControllerBuilder builder) {
    return (MergeBesuControllerBuilder)
        builder
            .gasLimitCalculator(gasLimitCalculator)
            .genesisConfigFile(genesisConfigFile)
            .synchronizerConfiguration(synchronizerConfiguration)
            .ethProtocolConfiguration(ethProtocolConfiguration)
            .miningParameters(miningParameters)
            .metricsSystem(observableMetricsSystem)
            .privacyParameters(privacyParameters)
            .dataDirectory(tempDirRule.getRoot().toPath())
            .clock(clock)
            .transactionPoolConfiguration(poolConfiguration)
            .nodeKey(nodeKey)
            .storageProvider(storageProvider)
            .evmConfiguration(EvmConfiguration.DEFAULT)
            .networkId(networkId);
  }

  @Test
  public void assertTerminalTotalDifficultyInMergeContext() {
    when(genesisConfigOptions.getTerminalTotalDifficulty())
        .thenReturn(Optional.of(UInt256.valueOf(1500L)));

    Difficulty terminalTotalDifficulty =
        visitWithMockConfigs(new MergeBesuControllerBuilder())
            .build()
            .getProtocolContext()
            .getConsensusContext(MergeContext.class)
            .getTerminalTotalDifficulty();

    assertThat(terminalTotalDifficulty).isEqualTo(Difficulty.of(1500L));
  }

  @Test
  public void assertConfiguredBlock() {
    Blockchain mockChain = mock(Blockchain.class);
    when(mockChain.getBlockHeader(anyLong())).thenReturn(Optional.of(mock(BlockHeader.class)));
    MergeContext mergeContext =
        besuControllerBuilder.createConsensusContext(
            mockChain,
            mock(WorldStateArchive.class),
            this.besuControllerBuilder.createProtocolSchedule());
    assertThat(mergeContext).isNotNull();
    assertThat(mergeContext.getTerminalPoWBlock()).isPresent();
  }

  @Test
  public void assertBuiltContextMonitorsTTD() {
    final GenesisState genesisState =
        GenesisState.fromConfig(
            genesisConfigFile, this.besuControllerBuilder.createProtocolSchedule());
    final MutableBlockchain blockchain = createInMemoryBlockchain(genesisState.getBlock());
    final MergeContext mergeContext =
        spy(
            besuControllerBuilder.createConsensusContext(
                blockchain,
                mock(WorldStateArchive.class),
                this.besuControllerBuilder.createProtocolSchedule()));
    assertThat(mergeContext).isNotNull();
    Difficulty over = Difficulty.of(10000L);
    Difficulty under = Difficulty.of(10L);

    BlockHeader parent =
        headerGenerator
            .difficulty(under)
            .parentHash(genesisState.getBlock().getHash())
            .number(genesisState.getBlock().getHeader().getNumber() + 1)
            .gasLimit(genesisState.getBlock().getHeader().getGasLimit())
            .stateRoot(genesisState.getBlock().getHeader().getStateRoot())
            .buildHeader();
    blockchain.appendBlock(new Block(parent, BlockBody.empty()), Collections.emptyList());

    BlockHeader terminal =
        headerGenerator
            .difficulty(over)
            .parentHash(parent.getHash())
            .number(parent.getNumber() + 1)
            .gasLimit(parent.getGasLimit())
            .stateRoot(parent.getStateRoot())
            .buildHeader();

    blockchain.appendBlock(new Block(terminal, BlockBody.empty()), Collections.emptyList());
    assertThat(mergeContext.isPostMerge()).isTrue();
  }

  @Test
  public void assertNoFinalizedBlockWhenNotStored() {
    Blockchain mockChain = mock(Blockchain.class);
    when(mockChain.getFinalized()).thenReturn(Optional.empty());
    MergeContext mergeContext =
        besuControllerBuilder.createConsensusContext(
            mockChain,
            mock(WorldStateArchive.class),
            this.besuControllerBuilder.createProtocolSchedule());
    assertThat(mergeContext).isNotNull();
    assertThat(mergeContext.getFinalized()).isEmpty();
  }

  @Test
  public void assertFinalizedBlockIsPresentWhenStored() {
    final BlockHeader finalizedHeader = finalizedBlockHeader();

    final Blockchain mockChain = mock(Blockchain.class);
    when(mockChain.getFinalized()).thenReturn(Optional.of(finalizedHeader.getHash()));
    when(mockChain.getBlockHeader(finalizedHeader.getHash()))
        .thenReturn(Optional.of(finalizedHeader));
    MergeContext mergeContext =
        besuControllerBuilder.createConsensusContext(
            mockChain,
            mock(WorldStateArchive.class),
            this.besuControllerBuilder.createProtocolSchedule());
    assertThat(mergeContext).isNotNull();
    assertThat(mergeContext.getFinalized().get()).isEqualTo(finalizedHeader);
  }

  private BlockHeader finalizedBlockHeader() {
    final long blockNumber = 42;
    final Hash magicHash = Hash.wrap(Bytes32.leftPad(Bytes.ofUnsignedInt(42)));

    return headerGenerator
        .difficulty(Difficulty.MAX_VALUE)
        .parentHash(magicHash)
        .number(blockNumber)
        .baseFeePerGas(feeMarket.computeBaseFee(blockNumber, Wei.of(0x3b9aca00), 0, 15000000l))
        .gasLimit(30000000l)
        .stateRoot(magicHash)
        .buildHeader();
  }
}
