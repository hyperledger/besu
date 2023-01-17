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
package org.hyperledger.besu.ethereum.blockcreation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.SealableBlockHeader;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.ethereum.mainnet.WithdrawalsProcessor;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.math.BigInteger;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
abstract class AbstractBlockCreatorTest {
  @Mock private WithdrawalsProcessor withdrawalsProcessor;

  @Test
  void withProcessorAndEmptyWithdrawals_NoWithdrawalsAreProcessed() {
    final AbstractBlockCreator blockCreator = createBlockCreatorWithWithdrawalsProcessor();
    blockCreator.createBlock(
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1L, false);
    verify(withdrawalsProcessor, never()).processWithdrawals(any(), any());
  }

  @Test
  void withNoProcessorAndEmptyWithdrawals_NoWithdrawalsAreNotProcessed() {
    final AbstractBlockCreator blockCreator = createBlockCreatorWithoutWithdrawalsProcessor();
    blockCreator.createBlock(
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1L, false);
    verify(withdrawalsProcessor, never()).processWithdrawals(any(), any());
  }

  @Test
  void withProcessorAndWithdrawals_WithdrawalsAreProcessed() {
    final AbstractBlockCreator blockCreator = createBlockCreatorWithWithdrawalsProcessor();
    final List<Withdrawal> withdrawals =
        List.of(new Withdrawal(UInt64.ONE, UInt64.ONE, Address.fromHexString("0x1"), GWei.ONE));
    blockCreator.createBlock(
        Optional.empty(), Optional.empty(), Optional.of(withdrawals), Optional.empty(), 1L, false);
    verify(withdrawalsProcessor).processWithdrawals(eq(withdrawals), any());
  }

  @Test
  void withNoProcessorAndWithdrawals_WithdrawalsAreNotProcessed() {
    final AbstractBlockCreator blockCreator = createBlockCreatorWithoutWithdrawalsProcessor();
    final List<Withdrawal> withdrawals =
        List.of(new Withdrawal(UInt64.ONE, UInt64.ONE, Address.fromHexString("0x1"), GWei.ONE));
    blockCreator.createBlock(
        Optional.empty(), Optional.empty(), Optional.of(withdrawals), Optional.empty(), 1L, false);
    verify(withdrawalsProcessor, never()).processWithdrawals(any(), any());
  }

  private AbstractBlockCreator createBlockCreatorWithWithdrawalsProcessor() {
    final ProtocolSpecAdapters protocolSpecAdapters =
        ProtocolSpecAdapters.create(
            0, specBuilder -> specBuilder.withdrawalsProcessor(withdrawalsProcessor));
    return createBlockCreator(protocolSpecAdapters);
  }

  private AbstractBlockCreator createBlockCreatorWithoutWithdrawalsProcessor() {
    return createBlockCreator(new ProtocolSpecAdapters(Map.of()));
  }

  private AbstractBlockCreator createBlockCreator(final ProtocolSpecAdapters protocolSpecAdapters) {
    final GenesisConfigOptions genesisConfigOptions = GenesisConfigFile.DEFAULT.getConfigOptions();
    final ExecutionContextTestFixture executionContextTestFixture =
        ExecutionContextTestFixture.builder()
            .protocolSchedule(
                new ProtocolScheduleBuilder(
                        genesisConfigOptions,
                        BigInteger.valueOf(42),
                        protocolSpecAdapters,
                        PrivacyParameters.DEFAULT,
                        false,
                        genesisConfigOptions.isQuorum(),
                        EvmConfiguration.DEFAULT)
                    .createProtocolSchedule())
            .build();

    final MutableBlockchain blockchain = executionContextTestFixture.getBlockchain();
    final AbstractPendingTransactionsSorter sorter =
        new GasPricePendingTransactionsSorter(
            ImmutableTransactionPoolConfiguration.builder().txPoolMaxSize(100).build(),
            Clock.systemUTC(),
            new NoOpMetricsSystem(),
            blockchain::getChainHeadHeader);

    return new TestBlockCreator(
        Address.ZERO,
        __ -> Address.ZERO,
        () -> Optional.of(30_000_000L),
        __ -> Bytes.fromHexString("deadbeef"),
        sorter,
        executionContextTestFixture.getProtocolContext(),
        executionContextTestFixture.getProtocolSchedule(),
        Wei.of(1L),
        0d,
        blockchain.getChainHeadHeader());
  }

  static class TestBlockCreator extends AbstractBlockCreator {

    protected TestBlockCreator(
        final Address coinbase,
        final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
        final Supplier<Optional<Long>> targetGasLimitSupplier,
        final ExtraDataCalculator extraDataCalculator,
        final AbstractPendingTransactionsSorter pendingTransactions,
        final ProtocolContext protocolContext,
        final ProtocolSchedule protocolSchedule,
        final Wei minTransactionGasPrice,
        final Double minBlockOccupancyRatio,
        final BlockHeader parentHeader) {
      super(
          coinbase,
          miningBeneficiaryCalculator,
          targetGasLimitSupplier,
          extraDataCalculator,
          pendingTransactions,
          protocolContext,
          protocolSchedule,
          minTransactionGasPrice,
          minBlockOccupancyRatio,
          parentHeader);
    }

    @Override
    protected BlockHeader createFinalBlockHeader(final SealableBlockHeader sealableBlockHeader) {
      return BlockHeaderBuilder.create()
          .difficulty(Difficulty.ZERO)
          .populateFrom(sealableBlockHeader)
          .mixHash(Hash.EMPTY)
          .nonce(0L)
          .blockHeaderFunctions(blockHeaderFunctions)
          .buildBlockHeader();
    }
  }
}
