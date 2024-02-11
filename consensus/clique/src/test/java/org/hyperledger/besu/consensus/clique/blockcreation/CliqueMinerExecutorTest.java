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
package org.hyperledger.besu.consensus.clique.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.consensus.clique.CliqueBlockHeaderFunctions;
import org.hyperledger.besu.consensus.clique.CliqueBlockInterface;
import org.hyperledger.besu.consensus.clique.CliqueContext;
import org.hyperledger.besu.consensus.clique.CliqueExtraData;
import org.hyperledger.besu.consensus.clique.CliqueProtocolSchedule;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.cryptoservices.NodeKeyUtils;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.ImmutableMiningParameters;
import org.hyperledger.besu.ethereum.core.ImmutableMiningParameters.MutableInitValues;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionBroadcaster;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.testutil.DeterministicEthScheduler;
import org.hyperledger.besu.testutil.TestClock;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CliqueMinerExecutorTest {

  private static final int EPOCH_LENGTH = 10;
  private static final GenesisConfigOptions GENESIS_CONFIG_OPTIONS =
      GenesisConfigFile.fromConfig("{}").getConfigOptions();
  private final NodeKey proposerNodeKey = NodeKeyUtils.generate();
  private final Random random = new Random(21341234L);
  private Address localAddress;
  private final List<Address> validatorList = Lists.newArrayList();
  private ProtocolContext cliqueProtocolContext;
  private ProtocolSchedule cliqueProtocolSchedule;
  private EthContext cliqueEthContext;
  private BlockHeaderTestFixture blockHeaderBuilder;
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final CliqueBlockInterface blockInterface = new CliqueBlockInterface();
  private final EthScheduler ethScheduler = new DeterministicEthScheduler();

  @BeforeEach
  public void setup() {
    localAddress = Util.publicKeyToAddress(proposerNodeKey.getPublicKey());
    validatorList.add(localAddress);
    validatorList.add(AddressHelpers.calculateAddressWithRespectTo(localAddress, 1));
    validatorList.add(AddressHelpers.calculateAddressWithRespectTo(localAddress, 2));
    validatorList.add(AddressHelpers.calculateAddressWithRespectTo(localAddress, 3));

    final ValidatorProvider validatorProvider = mock(ValidatorProvider.class);
    when(validatorProvider.getValidatorsAfterBlock(any())).thenReturn(validatorList);

    final CliqueContext cliqueContext = new CliqueContext(validatorProvider, null, blockInterface);
    cliqueProtocolContext = new ProtocolContext(null, null, cliqueContext, Optional.empty());
    cliqueProtocolSchedule =
        CliqueProtocolSchedule.create(
            GENESIS_CONFIG_OPTIONS,
            new ForksSchedule<>(List.of()),
            proposerNodeKey,
            false,
            EvmConfiguration.DEFAULT);
    cliqueEthContext = mock(EthContext.class, RETURNS_DEEP_STUBS);
    blockHeaderBuilder = new BlockHeaderTestFixture();
  }

  @Test
  public void extraDataCreatedOnEpochBlocksContainsValidators() {
    final Bytes vanityData = generateRandomVanityData();

    final MiningParameters miningParameters = createMiningParameters(vanityData);

    final CliqueMinerExecutor executor =
        new CliqueMinerExecutor(
            cliqueProtocolContext,
            cliqueProtocolSchedule,
            createTransactionPool(),
            proposerNodeKey,
            miningParameters,
            mock(CliqueBlockScheduler.class),
            new EpochManager(EPOCH_LENGTH),
            true,
            ethScheduler);

    // NOTE: Passing in the *parent* block, so must be 1 less than EPOCH
    final BlockHeader header = blockHeaderBuilder.number(EPOCH_LENGTH - 1).buildHeader();

    final Bytes extraDataBytes = executor.calculateExtraData(header);

    final CliqueExtraData cliqueExtraData =
        CliqueExtraData.decode(
            blockHeaderBuilder
                .number(EPOCH_LENGTH)
                .extraData(extraDataBytes)
                .blockHeaderFunctions(new CliqueBlockHeaderFunctions())
                .buildHeader());

    assertThat(cliqueExtraData.getVanityData()).isEqualTo(vanityData);
    assertThat(cliqueExtraData.getValidators())
        .containsExactly(validatorList.toArray(new Address[0]));
  }

  @Test
  public void extraDataForNonEpochBlocksDoesNotContainValidaors() {
    final Bytes vanityData = generateRandomVanityData();

    final MiningParameters miningParameters = createMiningParameters(vanityData);

    final CliqueMinerExecutor executor =
        new CliqueMinerExecutor(
            cliqueProtocolContext,
            cliqueProtocolSchedule,
            createTransactionPool(),
            proposerNodeKey,
            miningParameters,
            mock(CliqueBlockScheduler.class),
            new EpochManager(EPOCH_LENGTH),
            true,
            ethScheduler);

    // Parent block was epoch, so the next block should contain no validators.
    final BlockHeader header = blockHeaderBuilder.number(EPOCH_LENGTH).buildHeader();

    final Bytes extraDataBytes = executor.calculateExtraData(header);

    final CliqueExtraData cliqueExtraData =
        CliqueExtraData.decode(
            blockHeaderBuilder
                .number(EPOCH_LENGTH)
                .extraData(extraDataBytes)
                .blockHeaderFunctions(new CliqueBlockHeaderFunctions())
                .buildHeader());

    assertThat(cliqueExtraData.getVanityData()).isEqualTo(vanityData);
    assertThat(cliqueExtraData.getValidators()).isEqualTo(Lists.newArrayList());
  }

  @Test
  public void shouldUseLatestVanityData() {
    final Bytes initialVanityData = generateRandomVanityData();
    final Bytes modifiedVanityData = generateRandomVanityData();

    final MiningParameters miningParameters = createMiningParameters(initialVanityData);

    final CliqueMinerExecutor executor =
        new CliqueMinerExecutor(
            cliqueProtocolContext,
            cliqueProtocolSchedule,
            createTransactionPool(),
            proposerNodeKey,
            miningParameters,
            mock(CliqueBlockScheduler.class),
            new EpochManager(EPOCH_LENGTH),
            true,
            ethScheduler);

    executor.setExtraData(modifiedVanityData);
    final Bytes extraDataBytes = executor.calculateExtraData(blockHeaderBuilder.buildHeader());

    final CliqueExtraData cliqueExtraData =
        CliqueExtraData.decode(
            blockHeaderBuilder
                .number(EPOCH_LENGTH)
                .extraData(extraDataBytes)
                .blockHeaderFunctions(new CliqueBlockHeaderFunctions())
                .buildHeader());
    assertThat(cliqueExtraData.getVanityData()).isEqualTo(modifiedVanityData);
  }

  private TransactionPool createTransactionPool() {
    final var conf = ImmutableTransactionPoolConfiguration.builder().txPoolMaxSize(1).build();

    when(cliqueEthContext.getEthPeers().subscribeConnect(any())).thenReturn(1L);

    final TransactionPool transactionPool =
        new TransactionPool(
            () ->
                new GasPricePendingTransactionsSorter(
                    conf,
                    TestClock.system(ZoneId.systemDefault()),
                    metricsSystem,
                    CliqueMinerExecutorTest::mockBlockHeader),
            cliqueProtocolSchedule,
            cliqueProtocolContext,
            mock(TransactionBroadcaster.class),
            cliqueEthContext,
            new TransactionPoolMetrics(metricsSystem),
            conf,
            null);

    transactionPool.setEnabled();
    return transactionPool;
  }

  private static BlockHeader mockBlockHeader() {
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBaseFee()).thenReturn(Optional.empty());
    return blockHeader;
  }

  private Bytes generateRandomVanityData() {
    final byte[] vanityData = new byte[32];
    random.nextBytes(vanityData);
    return Bytes.wrap(vanityData);
  }

  private static MiningParameters createMiningParameters(final Bytes vanityData) {
    return ImmutableMiningParameters.builder()
        .mutableInitValues(
            MutableInitValues.builder()
                .extraData(vanityData)
                .minTransactionGasPrice(Wei.ZERO)
                .coinbase(AddressHelpers.ofValue(1))
                .build())
        .build();
  }
}
