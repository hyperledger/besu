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
package org.hyperledger.besu.ethereum.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator.BlockCreationResult;
import org.hyperledger.besu.ethereum.blockcreation.txselection.TransactionSelectionResults;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.ImmutableMiningParameters;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionBroadcaster;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.BaseFeePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.EpochCalculator;
import org.hyperledger.besu.ethereum.mainnet.PoWHasher;
import org.hyperledger.besu.ethereum.mainnet.PoWSolver;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.ethereum.mainnet.ValidationTestUtils;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.testutil.TestClock;
import org.hyperledger.besu.util.Subscribers;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.function.Function;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class PoWBlockCreatorTest extends AbstractBlockCreatorTest {

  private final Address BLOCK_1_COINBASE =
      Address.fromHexString("0x05a56e2d52c817161883f50c441c3228cfe54d9f");

  private static final long BLOCK_1_TIMESTAMP = Long.parseUnsignedLong("55ba4224", 16);

  private static final long BLOCK_1_NONCE = Long.parseLong("539bd4979fef1ec4", 16);

  private static final Bytes BLOCK_1_EXTRA_DATA =
      Bytes.fromHexString("0x476574682f76312e302e302f6c696e75782f676f312e342e32");
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  @Test
  void createMainnetBlock1() throws IOException {
    final GenesisConfigOptions genesisConfigOptions = GenesisConfigFile.DEFAULT.getConfigOptions();
    final ExecutionContextTestFixture executionContextTestFixture =
        ExecutionContextTestFixture.builder()
            .protocolSchedule(
                new ProtocolScheduleBuilder(
                        genesisConfigOptions,
                        BigInteger.valueOf(42),
                        ProtocolSpecAdapters.create(0, Function.identity()),
                        PrivacyParameters.DEFAULT,
                        false,
                        EvmConfiguration.DEFAULT)
                    .createProtocolSchedule())
            .build();

    final MiningParameters miningParameters =
        createMiningParameters(Lists.newArrayList(BLOCK_1_NONCE));

    final PoWSolver solver =
        new PoWSolver(
            miningParameters,
            PoWHasher.ETHASH_LIGHT,
            false,
            Subscribers.none(),
            new EpochCalculator.DefaultEpochCalculator());

    final TransactionPool transactionPool = createTransactionPool(executionContextTestFixture);

    final PoWBlockCreator blockCreator =
        new PoWBlockCreator(
            miningParameters,
            BLOCK_1_COINBASE,
            parent -> BLOCK_1_EXTRA_DATA,
            transactionPool,
            executionContextTestFixture.getProtocolContext(),
            executionContextTestFixture.getProtocolSchedule(),
            solver,
            executionContextTestFixture.getBlockchain().getChainHeadHeader());

    // A Hashrate should not exist in the block creator prior to creating a block
    assertThat(blockCreator.getHashesPerSecond()).isNotPresent();

    final BlockCreationResult blockResult = blockCreator.createBlock(BLOCK_1_TIMESTAMP);
    final Block actualBlock = blockResult.getBlock();
    final Block expectedBlock = ValidationTestUtils.readBlock(1);

    assertThat(actualBlock).isEqualTo(expectedBlock);
    assertThat(blockCreator.getHashesPerSecond()).isPresent();
    assertThat(blockResult.getTransactionSelectionResults())
        .isEqualTo(new TransactionSelectionResults());
  }

  @Test
  void createMainnetBlock1_fixedDifficulty1() {
    final GenesisConfigOptions genesisConfigOptions =
        GenesisConfigFile.fromConfig("{\"config\": {\"ethash\": {\"fixeddifficulty\":1}}}")
            .getConfigOptions();
    final ExecutionContextTestFixture executionContextTestFixture =
        ExecutionContextTestFixture.builder()
            .protocolSchedule(
                new ProtocolScheduleBuilder(
                        genesisConfigOptions,
                        BigInteger.valueOf(42),
                        ProtocolSpecAdapters.create(0, Function.identity()),
                        PrivacyParameters.DEFAULT,
                        false,
                        EvmConfiguration.DEFAULT)
                    .createProtocolSchedule())
            .build();

    final MiningParameters miningParameters =
        createMiningParameters(Lists.newArrayList(BLOCK_1_NONCE));

    final PoWSolver solver =
        new PoWSolver(
            miningParameters,
            PoWHasher.ETHASH_LIGHT,
            false,
            Subscribers.none(),
            new EpochCalculator.DefaultEpochCalculator());

    final TransactionPool transactionPool = createTransactionPool(executionContextTestFixture);

    final PoWBlockCreator blockCreator =
        new PoWBlockCreator(
            miningParameters,
            BLOCK_1_COINBASE,
            parent -> BLOCK_1_EXTRA_DATA,
            transactionPool,
            executionContextTestFixture.getProtocolContext(),
            executionContextTestFixture.getProtocolSchedule(),
            solver,
            executionContextTestFixture.getBlockchain().getChainHeadHeader());

    assertThat(blockCreator.createBlock(BLOCK_1_TIMESTAMP)).isNotNull();
    // If we weren't setting difficulty to 2^256-1 a difficulty of 1 would have caused a
    // IllegalArgumentException at the previous line, as 2^256 is 33 bytes.
  }

  @Test
  void rewardBeneficiary_zeroReward_skipZeroRewardsFalse() {
    final GenesisConfigOptions genesisConfigOptions =
        GenesisConfigFile.fromConfig("{\"config\": {\"ethash\": {\"fixeddifficulty\":1}}}")
            .getConfigOptions();
    ProtocolSchedule protocolSchedule =
        new ProtocolScheduleBuilder(
                genesisConfigOptions,
                BigInteger.valueOf(42),
                ProtocolSpecAdapters.create(0, Function.identity()),
                PrivacyParameters.DEFAULT,
                false,
                EvmConfiguration.DEFAULT)
            .createProtocolSchedule();
    final ExecutionContextTestFixture executionContextTestFixture =
        ExecutionContextTestFixture.builder().protocolSchedule(protocolSchedule).build();

    final MiningParameters miningParameters =
        createMiningParameters(Lists.newArrayList(BLOCK_1_NONCE));

    final PoWSolver solver =
        new PoWSolver(
            miningParameters,
            PoWHasher.ETHASH_LIGHT,
            false,
            Subscribers.none(),
            new EpochCalculator.DefaultEpochCalculator());

    final TransactionPool transactionPool = createTransactionPool(executionContextTestFixture);

    final PoWBlockCreator blockCreator =
        new PoWBlockCreator(
            miningParameters,
            BLOCK_1_COINBASE,
            parent -> BLOCK_1_EXTRA_DATA,
            transactionPool,
            executionContextTestFixture.getProtocolContext(),
            executionContextTestFixture.getProtocolSchedule(),
            solver,
            executionContextTestFixture.getBlockchain().getChainHeadHeader());

    final MutableWorldState mutableWorldState =
        executionContextTestFixture.getStateArchive().getMutable();
    assertThat(mutableWorldState.get(BLOCK_1_COINBASE)).isNull();

    final ProcessableBlockHeader header =
        BlockHeaderBuilder.create()
            .parentHash(Hash.ZERO)
            .coinbase(BLOCK_1_COINBASE)
            .difficulty(Difficulty.ONE)
            .number(1)
            .gasLimit(1)
            .timestamp(1)
            .buildProcessableBlockHeader();

    blockCreator.rewardBeneficiary(
        mutableWorldState,
        header,
        Collections.emptyList(),
        BLOCK_1_COINBASE,
        Wei.ZERO,
        false,
        protocolSchedule.getByBlockHeader(header));

    assertThat(mutableWorldState.get(BLOCK_1_COINBASE)).isNotNull();
    assertThat(mutableWorldState.get(BLOCK_1_COINBASE).getBalance()).isEqualTo(Wei.ZERO);
  }

  @Test
  void rewardBeneficiary_zeroReward_skipZeroRewardsTrue() {
    final GenesisConfigOptions genesisConfigOptions =
        GenesisConfigFile.fromConfig("{\"config\": {\"ethash\": {\"fixeddifficulty\":1}}}")
            .getConfigOptions();
    ProtocolSchedule protocolSchedule =
        new ProtocolScheduleBuilder(
                genesisConfigOptions,
                BigInteger.valueOf(42),
                ProtocolSpecAdapters.create(0, Function.identity()),
                PrivacyParameters.DEFAULT,
                false,
                EvmConfiguration.DEFAULT)
            .createProtocolSchedule();
    final ExecutionContextTestFixture executionContextTestFixture =
        ExecutionContextTestFixture.builder().protocolSchedule(protocolSchedule).build();

    final MiningParameters miningParameters =
        createMiningParameters(Lists.newArrayList(BLOCK_1_NONCE));

    final PoWSolver solver =
        new PoWSolver(
            miningParameters,
            PoWHasher.ETHASH_LIGHT,
            false,
            Subscribers.none(),
            new EpochCalculator.DefaultEpochCalculator());

    final TransactionPool transactionPool = createTransactionPool(executionContextTestFixture);

    final PoWBlockCreator blockCreator =
        new PoWBlockCreator(
            miningParameters,
            BLOCK_1_COINBASE,
            parent -> BLOCK_1_EXTRA_DATA,
            transactionPool,
            executionContextTestFixture.getProtocolContext(),
            executionContextTestFixture.getProtocolSchedule(),
            solver,
            executionContextTestFixture.getBlockchain().getChainHeadHeader());

    final MutableWorldState mutableWorldState =
        executionContextTestFixture.getStateArchive().getMutable();
    assertThat(mutableWorldState.get(BLOCK_1_COINBASE)).isNull();

    final ProcessableBlockHeader header =
        BlockHeaderBuilder.create()
            .parentHash(Hash.ZERO)
            .coinbase(BLOCK_1_COINBASE)
            .difficulty(Difficulty.ONE)
            .number(1)
            .gasLimit(1)
            .timestamp(1)
            .buildProcessableBlockHeader();

    blockCreator.rewardBeneficiary(
        mutableWorldState,
        header,
        Collections.emptyList(),
        BLOCK_1_COINBASE,
        Wei.ZERO,
        true,
        protocolSchedule.getByBlockHeader(header));

    assertThat(mutableWorldState.get(BLOCK_1_COINBASE)).isNull();
  }

  private TransactionPool createTransactionPool(
      final ExecutionContextTestFixture executionContextTestFixture) {
    final TransactionPoolConfiguration poolConf =
        ImmutableTransactionPoolConfiguration.builder().txPoolMaxSize(1).build();

    final BaseFeePendingTransactionsSorter pendingTransactions =
        new BaseFeePendingTransactionsSorter(
            poolConf,
            TestClock.fixed(),
            metricsSystem,
            executionContextTestFixture.getProtocolContext().getBlockchain()::getChainHeadHeader);

    final EthContext ethContext = mock(EthContext.class, RETURNS_DEEP_STUBS);
    when(ethContext.getEthPeers().subscribeConnect(any())).thenReturn(1L);

    final TransactionPool transactionPool =
        new TransactionPool(
            () -> pendingTransactions,
            executionContextTestFixture.getProtocolSchedule(),
            executionContextTestFixture.getProtocolContext(),
            mock(TransactionBroadcaster.class),
            ethContext,
            mock(MiningParameters.class),
            new TransactionPoolMetrics(metricsSystem),
            poolConf,
            null);
    transactionPool.setEnabled();

    return transactionPool;
  }

  private MiningParameters createMiningParameters(final Iterable<Long> nonceList) {
    return ImmutableMiningParameters.builder()
        .updatableInitValues(
            ImmutableMiningParameters.UpdatableInitValues.builder()
                .nonceGenerator(nonceList)
                .extraData(BLOCK_1_EXTRA_DATA)
                .minTransactionGasPrice(Wei.ONE)
                .build())
        .coinbase(BLOCK_1_COINBASE)
        .build();
  }
}
