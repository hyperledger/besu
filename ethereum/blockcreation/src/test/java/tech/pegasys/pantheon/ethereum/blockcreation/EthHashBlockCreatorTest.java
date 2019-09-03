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
package tech.pegasys.pantheon.ethereum.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderBuilder;
import tech.pegasys.pantheon.ethereum.core.ExecutionContextTestFixture;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.ProcessableBlockHeader;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPoolConfiguration;
import tech.pegasys.pantheon.ethereum.mainnet.EthHashSolver;
import tech.pegasys.pantheon.ethereum.mainnet.EthHasher.Light;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolScheduleBuilder;
import tech.pegasys.pantheon.ethereum.mainnet.ValidationTestUtils;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;
import tech.pegasys.pantheon.testutil.TestClock;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.function.Function;

import com.google.common.collect.Lists;
import org.junit.Test;

public class EthHashBlockCreatorTest {

  private final Address BLOCK_1_COINBASE =
      Address.fromHexString("0x05a56e2d52c817161883f50c441c3228cfe54d9f");

  private static final long BLOCK_1_TIMESTAMP = Long.parseUnsignedLong("55ba4224", 16);

  private static final long BLOCK_1_NONCE = Long.parseLong("539bd4979fef1ec4", 16);

  private static final BytesValue BLOCK_1_EXTRA_DATA =
      BytesValue.fromHexString("0x476574682f76312e302e302f6c696e75782f676f312e342e32");
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  @Test
  public void createMainnetBlock1() throws IOException {
    final ExecutionContextTestFixture executionContextTestFixture =
        ExecutionContextTestFixture.builder()
            .protocolSchedule(
                new ProtocolScheduleBuilder<>(
                        GenesisConfigFile.DEFAULT.getConfigOptions(),
                        BigInteger.valueOf(42),
                        Function.identity(),
                        PrivacyParameters.DEFAULT,
                        false)
                    .createProtocolSchedule())
            .build();

    final EthHashSolver solver = new EthHashSolver(Lists.newArrayList(BLOCK_1_NONCE), new Light());

    final PendingTransactions pendingTransactions =
        new PendingTransactions(
            TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
            1,
            TestClock.fixed(),
            metricsSystem);

    final EthHashBlockCreator blockCreator =
        new EthHashBlockCreator(
            BLOCK_1_COINBASE,
            parent -> BLOCK_1_EXTRA_DATA,
            pendingTransactions,
            executionContextTestFixture.getProtocolContext(),
            executionContextTestFixture.getProtocolSchedule(),
            gasLimit -> gasLimit,
            solver,
            Wei.ZERO,
            executionContextTestFixture.getBlockchain().getChainHeadHeader());

    // A Hashrate should not exist in the block creator prior to creating a block
    assertThat(blockCreator.getHashesPerSecond().isPresent()).isFalse();

    final Block actualBlock = blockCreator.createBlock(BLOCK_1_TIMESTAMP);
    final Block expectedBlock = ValidationTestUtils.readBlock(1);

    assertThat(actualBlock).isEqualTo(expectedBlock);
    assertThat(blockCreator.getHashesPerSecond().isPresent()).isTrue();
  }

  @Test
  public void createMainnetBlock1_fixedDifficulty1() {
    final ExecutionContextTestFixture executionContextTestFixture =
        ExecutionContextTestFixture.builder()
            .protocolSchedule(
                new ProtocolScheduleBuilder<>(
                        GenesisConfigFile.fromConfig(
                                "{\"config\": {\"ethash\": {\"fixeddifficulty\":1}}}")
                            .getConfigOptions(),
                        BigInteger.valueOf(42),
                        Function.identity(),
                        PrivacyParameters.DEFAULT,
                        false)
                    .createProtocolSchedule())
            .build();

    final EthHashSolver solver = new EthHashSolver(Lists.newArrayList(BLOCK_1_NONCE), new Light());

    final PendingTransactions pendingTransactions =
        new PendingTransactions(
            TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
            1,
            TestClock.fixed(),
            metricsSystem);

    final EthHashBlockCreator blockCreator =
        new EthHashBlockCreator(
            BLOCK_1_COINBASE,
            parent -> BLOCK_1_EXTRA_DATA,
            pendingTransactions,
            executionContextTestFixture.getProtocolContext(),
            executionContextTestFixture.getProtocolSchedule(),
            gasLimit -> gasLimit,
            solver,
            Wei.ZERO,
            executionContextTestFixture.getBlockchain().getChainHeadHeader());

    blockCreator.createBlock(BLOCK_1_TIMESTAMP);
    // If we weren't setting difficulty to 2^256-1 a difficulty of 1 would have caused a
    // IllegalArgumentException at the previous line, as 2^256 is 33 bytes.
  }

  @Test
  public void rewardBeneficiary_zeroReward_skipZeroRewardsFalse() {
    final ExecutionContextTestFixture executionContextTestFixture =
        ExecutionContextTestFixture.builder()
            .protocolSchedule(
                new ProtocolScheduleBuilder<>(
                        GenesisConfigFile.fromConfig(
                                "{\"config\": {\"ethash\": {\"fixeddifficulty\":1}}}")
                            .getConfigOptions(),
                        BigInteger.valueOf(42),
                        Function.identity(),
                        PrivacyParameters.DEFAULT,
                        false)
                    .createProtocolSchedule())
            .build();

    final EthHashSolver solver = new EthHashSolver(Lists.newArrayList(BLOCK_1_NONCE), new Light());

    final PendingTransactions pendingTransactions =
        new PendingTransactions(
            TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
            1,
            TestClock.fixed(),
            metricsSystem);

    final EthHashBlockCreator blockCreator =
        new EthHashBlockCreator(
            BLOCK_1_COINBASE,
            parent -> BLOCK_1_EXTRA_DATA,
            pendingTransactions,
            executionContextTestFixture.getProtocolContext(),
            executionContextTestFixture.getProtocolSchedule(),
            gasLimit -> gasLimit,
            solver,
            Wei.ZERO,
            executionContextTestFixture.getBlockchain().getChainHeadHeader());

    final MutableWorldState mutableWorldState =
        executionContextTestFixture.getStateArchive().getMutable();
    assertThat(mutableWorldState.get(BLOCK_1_COINBASE)).isNull();

    final ProcessableBlockHeader header =
        BlockHeaderBuilder.create()
            .parentHash(Hash.ZERO)
            .coinbase(BLOCK_1_COINBASE)
            .difficulty(UInt256.ONE)
            .number(1)
            .gasLimit(1)
            .timestamp(1)
            .buildProcessableBlockHeader();

    blockCreator.rewardBeneficiary(
        mutableWorldState, header, Collections.emptyList(), Wei.ZERO, false);

    assertThat(mutableWorldState.get(BLOCK_1_COINBASE)).isNotNull();
    assertThat(mutableWorldState.get(BLOCK_1_COINBASE).getBalance()).isEqualTo(Wei.ZERO);
  }

  @Test
  public void rewardBeneficiary_zeroReward_skipZeroRewardsTrue() {
    final ExecutionContextTestFixture executionContextTestFixture =
        ExecutionContextTestFixture.builder()
            .protocolSchedule(
                new ProtocolScheduleBuilder<>(
                        GenesisConfigFile.fromConfig(
                                "{\"config\": {\"ethash\": {\"fixeddifficulty\":1}}}")
                            .getConfigOptions(),
                        BigInteger.valueOf(42),
                        Function.identity(),
                        PrivacyParameters.DEFAULT,
                        false)
                    .createProtocolSchedule())
            .build();

    final EthHashSolver solver = new EthHashSolver(Lists.newArrayList(BLOCK_1_NONCE), new Light());

    final PendingTransactions pendingTransactions =
        new PendingTransactions(
            TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
            1,
            TestClock.fixed(),
            metricsSystem);

    final EthHashBlockCreator blockCreator =
        new EthHashBlockCreator(
            BLOCK_1_COINBASE,
            parent -> BLOCK_1_EXTRA_DATA,
            pendingTransactions,
            executionContextTestFixture.getProtocolContext(),
            executionContextTestFixture.getProtocolSchedule(),
            gasLimit -> gasLimit,
            solver,
            Wei.ZERO,
            executionContextTestFixture.getBlockchain().getChainHeadHeader());

    final MutableWorldState mutableWorldState =
        executionContextTestFixture.getStateArchive().getMutable();
    assertThat(mutableWorldState.get(BLOCK_1_COINBASE)).isNull();

    final ProcessableBlockHeader header =
        BlockHeaderBuilder.create()
            .parentHash(Hash.ZERO)
            .coinbase(BLOCK_1_COINBASE)
            .difficulty(UInt256.ONE)
            .number(1)
            .gasLimit(1)
            .timestamp(1)
            .buildProcessableBlockHeader();

    blockCreator.rewardBeneficiary(
        mutableWorldState, header, Collections.emptyList(), Wei.ZERO, true);

    assertThat(mutableWorldState.get(BLOCK_1_COINBASE)).isNull();
  }
}
