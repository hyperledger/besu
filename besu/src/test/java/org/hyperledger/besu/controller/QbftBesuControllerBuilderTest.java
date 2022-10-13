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
import static org.mockito.ArgumentMatchers.anyString;
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
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.NodeKeyUtils;
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
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.WorldStatePreimageStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.math.BigInteger;
import java.time.Clock;
import java.util.List;

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
public class QbftBesuControllerBuilderTest {

  private BesuControllerBuilder qbftBesuControllerBuilder;

  @Mock private GenesisConfigFile genesisConfigFile;
  @Mock private GenesisConfigOptions genesisConfigOptions;
  @Mock private SynchronizerConfiguration synchronizerConfiguration;
  @Mock private EthProtocolConfiguration ethProtocolConfiguration;
  @Mock CheckpointConfigOptions checkpointConfigOptions;
  @Mock private MiningParameters miningParameters;
  @Mock private ObservableMetricsSystem observableMetricsSystem;
  @Mock private PrivacyParameters privacyParameters;
  @Mock private Clock clock;
  @Mock private TransactionPoolConfiguration poolConfiguration;
  @Mock private StorageProvider storageProvider;
  @Mock private GasLimitCalculator gasLimitCalculator;
  @Mock private WorldStateStorage worldStateStorage;
  @Mock private WorldStatePreimageStorage worldStatePreimageStorage;
  private static final BigInteger networkId = BigInteger.ONE;
  private static final NodeKey nodeKey = NodeKeyUtils.generate();

  @Rule public final TemporaryFolder tempDirRule = new TemporaryFolder();

  @Before
  public void setup() {
    // besu controller setup
    when(genesisConfigFile.getParentHash()).thenReturn(Hash.ZERO.toHexString());
    when(genesisConfigFile.getDifficulty()).thenReturn(Bytes.of(0).toHexString());
    when(genesisConfigFile.getExtraData()).thenReturn(Bytes.EMPTY.toHexString());
    when(genesisConfigFile.getMixHash()).thenReturn(Hash.ZERO.toHexString());
    when(genesisConfigFile.getNonce()).thenReturn(Long.toHexString(1));
    when(genesisConfigFile.getConfigOptions(any())).thenReturn(genesisConfigOptions);
    when(genesisConfigOptions.getCheckpointOptions()).thenReturn(checkpointConfigOptions);
    when(storageProvider.createBlockchainStorage(any()))
        .thenReturn(
            new KeyValueStoragePrefixedKeyBlockchainStorage(
                new InMemoryKeyValueStorage(), new MainnetBlockHeaderFunctions()));
    when(storageProvider.createWorldStateStorage(DataStorageFormat.FOREST))
        .thenReturn(worldStateStorage);
    when(worldStateStorage.isWorldStateAvailable(any(), any())).thenReturn(true);
    when(worldStateStorage.updater()).thenReturn(mock(WorldStateStorage.Updater.class));
    when(worldStatePreimageStorage.updater())
        .thenReturn(mock(WorldStatePreimageStorage.Updater.class));
    when(storageProvider.createWorldStatePreimageStorage()).thenReturn(worldStatePreimageStorage);
    when(synchronizerConfiguration.getDownloaderParallelism()).thenReturn(1);
    when(synchronizerConfiguration.getTransactionsParallelism()).thenReturn(1);
    when(synchronizerConfiguration.getComputationParallelism()).thenReturn(1);
    when(observableMetricsSystem.createLabelledCounter(
            any(), anyString(), anyString(), anyString()))
        .thenReturn(labels -> null);
    when(synchronizerConfiguration.getBlockPropagationRange()).thenReturn(Range.closed(1L, 2L));

    // qbft prepForBuild setup
    when(genesisConfigOptions.getQbftConfigOptions())
        .thenReturn(new MutableQbftConfigOptions(JsonQbftConfigOptions.DEFAULT));
    when(genesisConfigOptions.getTransitions()).thenReturn(mock(TransitionsConfigOptions.class));
    when(genesisConfigFile.getExtraData())
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
            .dataDirectory(tempDirRule.getRoot().toPath())
            .clock(clock)
            .transactionPoolConfiguration(poolConfiguration)
            .nodeKey(nodeKey)
            .storageProvider(storageProvider)
            .gasLimitCalculator(gasLimitCalculator)
            .evmConfiguration(EvmConfiguration.DEFAULT);
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
