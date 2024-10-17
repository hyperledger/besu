/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.mainnet;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.mainnet.blockhash.FrontierBlockHashProcessor;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestBlockchain;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestWorldState;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.units.bigints.UInt64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
abstract class AbstractBlockProcessorTest {

  @Mock private MainnetTransactionProcessor transactionProcessor;
  @Mock private AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory;
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private ProtocolSpec protocolSpec;
  @Mock private WithdrawalsProcessor withdrawalsProcessor;

  final Blockchain blockchain = new ReferenceTestBlockchain();
  final MutableWorldState worldState = ReferenceTestWorldState.create(emptyMap());
  final BlockHeader emptyBlockHeader =
      new BlockHeaderTestFixture()
          .transactionsRoot(Hash.EMPTY_LIST_HASH)
          .ommersHash(Hash.EMPTY_LIST_HASH)
          .buildHeader();
  private TestBlockProcessor blockProcessor;

  @BeforeEach
  void baseSetup() {
    lenient().when(protocolSchedule.getByBlockHeader(any())).thenReturn(protocolSpec);
    lenient()
        .when(protocolSpec.getBlockHashProcessor())
        .thenReturn(new FrontierBlockHashProcessor());
    blockProcessor =
        new TestBlockProcessor(
            transactionProcessor,
            transactionReceiptFactory,
            Wei.ZERO,
            BlockHeader::getCoinbase,
            true,
            protocolSchedule);
  }

  @Test
  void withProcessorAndEmptyWithdrawals_WithdrawalsAreNotProcessed() {
    when(protocolSpec.getWithdrawalsProcessor()).thenReturn(Optional.empty());
    blockProcessor.processBlock(
        blockchain, worldState, emptyBlockHeader, emptyList(), emptyList(), Optional.empty(), null);
    verify(withdrawalsProcessor, never()).processWithdrawals(any(), any());
  }

  @Test
  void withNoProcessorAndEmptyWithdrawals_WithdrawalsAreNotProcessed() {
    when(protocolSpec.getWithdrawalsProcessor()).thenReturn(Optional.empty());
    blockProcessor.processBlock(
        blockchain, worldState, emptyBlockHeader, emptyList(), emptyList(), Optional.empty(), null);
    verify(withdrawalsProcessor, never()).processWithdrawals(any(), any());
  }

  @Test
  void withProcessorAndWithdrawals_WithdrawalsAreProcessed() {
    when(protocolSpec.getWithdrawalsProcessor()).thenReturn(Optional.of(withdrawalsProcessor));
    final List<Withdrawal> withdrawals =
        List.of(new Withdrawal(UInt64.ONE, UInt64.ONE, Address.fromHexString("0x1"), GWei.ONE));
    blockProcessor.processBlock(
        blockchain,
        worldState,
        emptyBlockHeader,
        emptyList(),
        emptyList(),
        Optional.of(withdrawals),
        null);
    verify(withdrawalsProcessor).processWithdrawals(eq(withdrawals), any());
  }

  @Test
  void withNoProcessorAndWithdrawals_WithdrawalsAreNotProcessed() {
    when(protocolSpec.getWithdrawalsProcessor()).thenReturn(Optional.empty());

    final List<Withdrawal> withdrawals =
        List.of(new Withdrawal(UInt64.ONE, UInt64.ONE, Address.fromHexString("0x1"), GWei.ONE));
    blockProcessor.processBlock(
        blockchain,
        worldState,
        emptyBlockHeader,
        emptyList(),
        emptyList(),
        Optional.of(withdrawals),
        null);
    verify(withdrawalsProcessor, never()).processWithdrawals(any(), any());
  }

  private static class TestBlockProcessor extends AbstractBlockProcessor {

    protected TestBlockProcessor(
        final MainnetTransactionProcessor transactionProcessor,
        final TransactionReceiptFactory transactionReceiptFactory,
        final Wei blockReward,
        final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
        final boolean skipZeroBlockRewards,
        final ProtocolSchedule protocolSchedule) {
      super(
          transactionProcessor,
          transactionReceiptFactory,
          blockReward,
          miningBeneficiaryCalculator,
          skipZeroBlockRewards,
          protocolSchedule);
    }

    @Override
    boolean rewardCoinbase(
        final MutableWorldState worldState,
        final BlockHeader header,
        final List<BlockHeader> ommers,
        final boolean skipZeroBlockRewards) {
      return false;
    }
  }
}
