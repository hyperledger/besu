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
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.CheckpointConfigOptions;
import org.hyperledger.besu.config.EthashConfigOptions;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.cryptoservices.NodeKeyUtils;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.VariablesKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.bonsai.cache.CachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.forest.pruner.PrunerConfiguration;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.ImmutableDataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStatePreimageStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Clock;
import java.util.OptionalLong;

import com.google.common.collect.Range;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BesuControllerBuilderTest {

  private BesuControllerBuilder besuControllerBuilder;
  private static final NodeKey nodeKey = NodeKeyUtils.generate();

  @Mock GenesisConfigFile genesisConfigFile;
  @Mock GenesisConfigOptions genesisConfigOptions;
  @Mock EthashConfigOptions ethashConfigOptions;
  @Mock CheckpointConfigOptions checkpointConfigOptions;
  @Mock SynchronizerConfiguration synchronizerConfiguration;
  @Mock EthProtocolConfiguration ethProtocolConfiguration;
  @Mock PrivacyParameters privacyParameters;
  @Mock Clock clock;
  @Mock StorageProvider storageProvider;
  @Mock GasLimitCalculator gasLimitCalculator;
  @Mock WorldStateStorage worldStateStorage;
  @Mock WorldStateArchive worldStateArchive;
  @Mock BonsaiWorldStateKeyValueStorage bonsaiWorldStateStorage;
  @Mock WorldStatePreimageStorage worldStatePreimageStorage;
  private final TransactionPoolConfiguration poolConfiguration =
      TransactionPoolConfiguration.DEFAULT;
  private final MiningParameters miningParameters = MiningParameters.newDefault();

  private final ObservableMetricsSystem observableMetricsSystem = new NoOpMetricsSystem();

  BigInteger networkId = BigInteger.ONE;

  @TempDir Path tempDir;

  @BeforeEach
  public void setup() {
    when(genesisConfigFile.getParentHash()).thenReturn(Hash.ZERO.toHexString());
    when(genesisConfigFile.getDifficulty()).thenReturn(Bytes.of(0).toHexString());
    when(genesisConfigFile.getExtraData()).thenReturn(Bytes.EMPTY.toHexString());
    when(genesisConfigFile.getMixHash()).thenReturn(Hash.ZERO.toHexString());
    when(genesisConfigFile.getNonce()).thenReturn(Long.toHexString(1));
    when(genesisConfigFile.getConfigOptions(any())).thenReturn(genesisConfigOptions);
    when(genesisConfigFile.getConfigOptions()).thenReturn(genesisConfigOptions);
    when(genesisConfigOptions.getThanosBlockNumber()).thenReturn(OptionalLong.empty());
    when(genesisConfigOptions.getEthashConfigOptions()).thenReturn(ethashConfigOptions);
    when(genesisConfigOptions.getCheckpointOptions()).thenReturn(checkpointConfigOptions);
    when(ethashConfigOptions.getFixedDifficulty()).thenReturn(OptionalLong.empty());
    when(storageProvider.getStorageBySegmentIdentifier(any()))
        .thenReturn(new InMemoryKeyValueStorage());
    when(storageProvider.createBlockchainStorage(any(), any()))
        .thenReturn(
            new KeyValueStoragePrefixedKeyBlockchainStorage(
                new InMemoryKeyValueStorage(),
                new VariablesKeyValueStorage(new InMemoryKeyValueStorage()),
                new MainnetBlockHeaderFunctions()));
    when(synchronizerConfiguration.getDownloaderParallelism()).thenReturn(1);
    when(synchronizerConfiguration.getTransactionsParallelism()).thenReturn(1);
    when(synchronizerConfiguration.getComputationParallelism()).thenReturn(1);

    when(synchronizerConfiguration.getBlockPropagationRange()).thenReturn(Range.closed(1L, 2L));

    lenient()
        .when(storageProvider.createWorldStateStorage(DataStorageFormat.FOREST))
        .thenReturn(worldStateStorage);
    lenient()
        .when(storageProvider.createWorldStatePreimageStorage())
        .thenReturn(worldStatePreimageStorage);

    lenient().when(worldStateStorage.isWorldStateAvailable(any(), any())).thenReturn(true);
    lenient()
        .when(worldStatePreimageStorage.updater())
        .thenReturn(mock(WorldStatePreimageStorage.Updater.class));
    lenient().when(worldStateStorage.updater()).thenReturn(mock(WorldStateStorage.Updater.class));
    besuControllerBuilder = spy(visitWithMockConfigs(new MainnetBesuControllerBuilder()));
  }

  BesuControllerBuilder visitWithMockConfigs(final BesuControllerBuilder builder) {
    return builder
        .gasLimitCalculator(gasLimitCalculator)
        .genesisConfigFile(genesisConfigFile)
        .synchronizerConfiguration(synchronizerConfiguration)
        .ethProtocolConfiguration(ethProtocolConfiguration)
        .miningParameters(miningParameters)
        .metricsSystem(observableMetricsSystem)
        .privacyParameters(privacyParameters)
        .dataDirectory(tempDir)
        .clock(clock)
        .transactionPoolConfiguration(poolConfiguration)
        .nodeKey(nodeKey)
        .storageProvider(storageProvider)
        .evmConfiguration(EvmConfiguration.DEFAULT)
        .networkConfiguration(NetworkingConfiguration.create())
        .networkId(networkId);
  }

  @Test
  public void shouldDisablePruningIfBonsaiIsEnabled() {
    BonsaiWorldState mockWorldState = mock(BonsaiWorldState.class, Answers.RETURNS_DEEP_STUBS);
    doReturn(worldStateArchive)
        .when(besuControllerBuilder)
        .createWorldStateArchive(
            any(WorldStateStorage.class), any(Blockchain.class), any(CachedMerkleTrieLoader.class));
    doReturn(mockWorldState).when(worldStateArchive).getMutable();

    when(storageProvider.createWorldStateStorage(DataStorageFormat.BONSAI))
        .thenReturn(bonsaiWorldStateStorage);
    besuControllerBuilder
        .isPruningEnabled(true)
        .dataStorageConfiguration(
            ImmutableDataStorageConfiguration.builder()
                .dataStorageFormat(DataStorageFormat.BONSAI)
                .bonsaiMaxLayersToLoad(DataStorageConfiguration.DEFAULT_BONSAI_MAX_LAYERS_TO_LOAD)
                .build());
    besuControllerBuilder.build();

    verify(storageProvider, never())
        .getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.PRUNING_STATE);
  }

  @Test
  public void shouldUsePruningIfForestIsEnabled() {
    besuControllerBuilder
        .isPruningEnabled(true)
        .pruningConfiguration(new PrunerConfiguration(1, 2))
        .dataStorageConfiguration(
            ImmutableDataStorageConfiguration.builder()
                .dataStorageFormat(DataStorageFormat.FOREST)
                .bonsaiMaxLayersToLoad(DataStorageConfiguration.DEFAULT_BONSAI_MAX_LAYERS_TO_LOAD)
                .build());
    besuControllerBuilder.build();

    verify(storageProvider).getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.PRUNING_STATE);
  }
}
