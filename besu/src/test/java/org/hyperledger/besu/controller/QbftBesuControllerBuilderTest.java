/*
 * Copyright Hyperledger Besu contributors.
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.CheckpointConfigOptions;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.JsonQbftConfigOptions;
import org.hyperledger.besu.config.TransitionsConfigOptions;
import org.hyperledger.besu.consensus.common.bft.BftContext;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.qbft.MutableQbftConfigOptions;
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.validator.ForkingValidatorProvider;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.cryptoservices.NodeKeyUtils;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.VariablesKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStatePreimageStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import com.google.common.collect.Range;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class QbftBesuControllerBuilderTest {

  private BesuControllerBuilder qbftBesuControllerBuilder;

  @Mock private GenesisConfigFile genesisConfigFile;
  @Mock private GenesisConfigOptions genesisConfigOptions;
  @Mock private SynchronizerConfiguration synchronizerConfiguration;
  @Mock private EthProtocolConfiguration ethProtocolConfiguration;
  @Mock CheckpointConfigOptions checkpointConfigOptions;
  @Mock private MiningParameters miningParameters;
  @Mock private PrivacyParameters privacyParameters;
  @Mock private Clock clock;
  @Mock private StorageProvider storageProvider;
  @Mock private GasLimitCalculator gasLimitCalculator;
  @Mock private WorldStateStorage worldStateStorage;
  @Mock private WorldStatePreimageStorage worldStatePreimageStorage;
  private static final BigInteger networkId = BigInteger.ONE;
  private static final NodeKey nodeKey = NodeKeyUtils.generate();
  private final TransactionPoolConfiguration poolConfiguration =
      TransactionPoolConfiguration.DEFAULT;
  private final ObservableMetricsSystem observableMetricsSystem = new NoOpMetricsSystem();

  @TempDir Path tempDir;

  @BeforeEach
  public void setup() {
    // besu controller setup
    lenient().when(genesisConfigFile.getParentHash()).thenReturn(Hash.ZERO.toHexString());
    lenient().when(genesisConfigFile.getDifficulty()).thenReturn(Bytes.of(0).toHexString());
    when(genesisConfigFile.getExtraData()).thenReturn(Bytes.EMPTY.toHexString());
    lenient().when(genesisConfigFile.getMixHash()).thenReturn(Hash.ZERO.toHexString());
    lenient().when(genesisConfigFile.getNonce()).thenReturn(Long.toHexString(1));
    lenient().when(genesisConfigFile.getConfigOptions(any())).thenReturn(genesisConfigOptions);
    lenient().when(genesisConfigFile.getConfigOptions()).thenReturn(genesisConfigOptions);
    lenient().when(genesisConfigOptions.getCheckpointOptions()).thenReturn(checkpointConfigOptions);
    lenient()
        .when(storageProvider.createBlockchainStorage(any(), any()))
        .thenReturn(
            new KeyValueStoragePrefixedKeyBlockchainStorage(
                new InMemoryKeyValueStorage(),
                new VariablesKeyValueStorage(new InMemoryKeyValueStorage()),
                new MainnetBlockHeaderFunctions()));
    lenient()
        .when(
            storageProvider.createWorldStateStorage(DataStorageConfiguration.DEFAULT_FOREST_CONFIG))
        .thenReturn(worldStateStorage);
    lenient().when(worldStateStorage.isWorldStateAvailable(any(), any())).thenReturn(true);
    lenient().when(worldStateStorage.updater()).thenReturn(mock(WorldStateStorage.Updater.class));
    lenient()
        .when(worldStatePreimageStorage.updater())
        .thenReturn(mock(WorldStatePreimageStorage.Updater.class));
    lenient()
        .when(storageProvider.createWorldStatePreimageStorage())
        .thenReturn(worldStatePreimageStorage);
    lenient().when(synchronizerConfiguration.getDownloaderParallelism()).thenReturn(1);
    lenient().when(synchronizerConfiguration.getTransactionsParallelism()).thenReturn(1);
    lenient().when(synchronizerConfiguration.getComputationParallelism()).thenReturn(1);

    lenient()
        .when(synchronizerConfiguration.getBlockPropagationRange())
        .thenReturn(Range.closed(1L, 2L));

    // qbft prepForBuild setup
    lenient()
        .when(genesisConfigOptions.getQbftConfigOptions())
        .thenReturn(new MutableQbftConfigOptions(JsonQbftConfigOptions.DEFAULT));
    lenient()
        .when(genesisConfigOptions.getTransitions())
        .thenReturn(mock(TransitionsConfigOptions.class));
    lenient()
        .when(genesisConfigFile.getExtraData())
        .thenReturn(
            QbftExtraDataCodec.createGenesisExtraDataString(List.of(Address.fromHexString("1"))));

    qbftBesuControllerBuilder =
        new QbftBesuControllerBuilder()
            .genesisConfigFile(genesisConfigFile)
            .synchronizerConfiguration(synchronizerConfiguration)
            .ethProtocolConfiguration(ethProtocolConfiguration)
            .networkId(networkId)
            .miningParameters(miningParameters)
            .metricsSystem(observableMetricsSystem)
            .privacyParameters(privacyParameters)
            .dataDirectory(tempDir)
            .clock(clock)
            .transactionPoolConfiguration(poolConfiguration)
            .dataStorageConfiguration(DataStorageConfiguration.DEFAULT_FOREST_CONFIG)
            .nodeKey(nodeKey)
            .storageProvider(storageProvider)
            .gasLimitCalculator(gasLimitCalculator)
            .evmConfiguration(EvmConfiguration.DEFAULT)
            .networkConfiguration(NetworkingConfiguration.create());
  }

  @Test
  public void forkingValidatorProviderIsAvailableOnBftContext() {
    final BesuController besuController = qbftBesuControllerBuilder.build();

    final ValidatorProvider validatorProvider =
        besuController
            .getProtocolContext()
            .getConsensusContext(BftContext.class)
            .getValidatorProvider();
    assertThat(validatorProvider).isInstanceOf(ForkingValidatorProvider.class);
  }

  @Test
  public void missingTransactionValidatorProviderThrowsError() {
    final ProtocolContext protocolContext = mock(ProtocolContext.class);
    when(protocolContext.getBlockchain()).thenReturn(mock(MutableBlockchain.class));

    assertThatThrownBy(
            () -> qbftBesuControllerBuilder.createAdditionalJsonRpcMethodFactory(protocolContext))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("transactionValidatorProvider should have been initialised");
  }
}
