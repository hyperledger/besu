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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.EthashConfigOptions;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.Keccak256ConfigOptions;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.ImmutableDataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.PrunerConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStatePreimageStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.math.BigInteger;
import java.time.Clock;
import java.util.OptionalLong;

import com.google.common.collect.Range;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BesuControllerBuilderTest {

  private BesuControllerBuilder besuControllerBuilder;

  @Mock GenesisConfigFile genesisConfigFile;
  @Mock GenesisConfigOptions genesisConfigOptions;
  @Mock EthashConfigOptions ethashConfigOptions;
  @Mock Keccak256ConfigOptions keccak256ConfigOptions;
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

  @Rule public final TemporaryFolder tempDirRule = new TemporaryFolder();

  @Before
  public void setup() {
    when(genesisConfigFile.getParentHash()).thenReturn(Hash.ZERO.toHexString());
    when(genesisConfigFile.getDifficulty()).thenReturn(Bytes.of(0).toHexString());
    when(genesisConfigFile.getExtraData()).thenReturn(Bytes.EMPTY.toHexString());
    when(genesisConfigFile.getMixHash()).thenReturn(Hash.ZERO.toHexString());
    when(genesisConfigFile.getNonce()).thenReturn(Long.toHexString(1));
    when(genesisConfigFile.getConfigOptions(any())).thenReturn(genesisConfigOptions);
    when(genesisConfigOptions.getThanosBlockNumber()).thenReturn(OptionalLong.empty());
    when(genesisConfigOptions.getEthashConfigOptions()).thenReturn(ethashConfigOptions);
    when(ethashConfigOptions.getFixedDifficulty()).thenReturn(OptionalLong.empty());
    when(genesisConfigOptions.getKeccak256ConfigOptions()).thenReturn(keccak256ConfigOptions);
    when(keccak256ConfigOptions.getFixedDifficulty()).thenReturn(OptionalLong.empty());
    when(storageProvider.getStorageBySegmentIdentifier(any()))
        .thenReturn(new InMemoryKeyValueStorage());
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
    when(storageProvider.isWorldStateIterable()).thenReturn(true);

    when(worldStateStorage.isWorldStateAvailable(any(), any())).thenReturn(true);
    when(worldStatePreimageStorage.updater())
        .thenReturn(mock(WorldStatePreimageStorage.Updater.class));
    when(worldStateStorage.updater()).thenReturn(mock(WorldStateStorage.Updater.class));

    besuControllerBuilder = visitWithMockConfigs(new MainnetBesuControllerBuilder());
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
        .dataDirectory(tempDirRule.getRoot().toPath())
        .clock(clock)
        .transactionPoolConfiguration(poolConfiguration)
        .nodeKey(nodeKey)
        .storageProvider(storageProvider)
        .evmConfiguration(EvmConfiguration.DEFAULT)
        .networkId(networkId);
  }

  @Test
  public void shouldDisablePruningIfBonsaiIsEnabled() {
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
