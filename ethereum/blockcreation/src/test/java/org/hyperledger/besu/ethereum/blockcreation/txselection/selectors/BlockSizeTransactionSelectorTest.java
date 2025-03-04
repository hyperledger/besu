/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.blockcreation.txselection.selectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MAX_SCORE;
import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.BLOCK_FULL;
import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.BLOCK_OCCUPANCY_ABOVE_THRESHOLD;
import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.SELECTED;
import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.TX_TOO_LARGE_FOR_REMAINING_GAS;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.blockcreation.txselection.BlockSelectionContext;
import org.hyperledger.besu.ethereum.blockcreation.txselection.TransactionEvaluationContext;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.SelectorsStateManager;

import java.util.Optional;
import java.util.stream.IntStream;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockSizeTransactionSelectorTest {
  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  private static final KeyPair KEYS = SIGNATURE_ALGORITHM.get().generateKeyPair();
  private static final long TRANSFER_GAS_LIMIT = 21_000L;
  private static final long BLOCK_GAS_LIMIT = 1_000_000L;

  @Mock(answer = RETURNS_DEEP_STUBS)
  BlockSelectionContext blockSelectionContext;

  SelectorsStateManager selectorsStateManager;
  BlockSizeTransactionSelector selector;
  MiningConfiguration miningConfiguration;

  @BeforeEach
  void setup() {
    miningConfiguration = MiningConfiguration.newDefault();
    when(blockSelectionContext.pendingBlockHeader().getGasLimit()).thenReturn(BLOCK_GAS_LIMIT);
    when(blockSelectionContext.miningConfiguration()).thenReturn(miningConfiguration);

    selectorsStateManager = new SelectorsStateManager();
    selector = new BlockSizeTransactionSelector(blockSelectionContext, selectorsStateManager);
  }

  @Test
  void singleTransactionBelowBlockGasLimitIsSelected() {
    final var tx = createPendingTransaction(TRANSFER_GAS_LIMIT);

    final var txEvaluationContext =
        new TransactionEvaluationContext(
            blockSelectionContext.pendingBlockHeader(), tx, null, null, null);
    selectorsStateManager.blockSelectionStarted();
    evaluateAndAssertSelected(txEvaluationContext, remainingGas(0));

    assertThat(selector.getWorkingState()).isEqualTo(TRANSFER_GAS_LIMIT);
  }

  @Test
  void singleTransactionAboveBlockGasLimitIsNotSelected() {
    final var tx = createPendingTransaction(BLOCK_GAS_LIMIT + 1);

    final var txEvaluationContext =
        new TransactionEvaluationContext(
            blockSelectionContext.pendingBlockHeader(), tx, null, null, null);
    selectorsStateManager.blockSelectionStarted();
    evaluateAndAssertNotSelected(txEvaluationContext, TX_TOO_LARGE_FOR_REMAINING_GAS);

    assertThat(selector.getWorkingState()).isEqualTo(0);
  }

  @Test
  void correctlyCumulatesOnlyTheEffectiveGasUsedAfterProcessing() {
    final var tx = createPendingTransaction(TRANSFER_GAS_LIMIT * 2);
    final long remainingGas = 100;

    final var txEvaluationContext =
        new TransactionEvaluationContext(
            blockSelectionContext.pendingBlockHeader(), tx, null, null, null);
    selectorsStateManager.blockSelectionStarted();
    evaluateAndAssertSelected(txEvaluationContext, remainingGas(remainingGas));

    assertThat(selector.getWorkingState()).isEqualTo(TRANSFER_GAS_LIMIT * 2 - remainingGas);
  }

  @Test
  void moreTransactionsBelowBlockGasLimitAreSelected() {
    selectorsStateManager.blockSelectionStarted();

    final int txCount = 10;

    IntStream.range(0, txCount)
        .forEach(
            unused -> {
              final var tx = createPendingTransaction(TRANSFER_GAS_LIMIT);

              final var txEvaluationContext =
                  new TransactionEvaluationContext(
                      blockSelectionContext.pendingBlockHeader(), tx, null, null, null);
              evaluateAndAssertSelected(txEvaluationContext, remainingGas(0));
            });

    assertThat(selector.getWorkingState()).isEqualTo(TRANSFER_GAS_LIMIT * txCount);
  }

  @Test
  void moreTransactionsThanBlockCanFitOnlySomeAreSelected() {
    selectorsStateManager.blockSelectionStarted();

    final int txCount = 10;

    IntStream.range(0, txCount)
        .forEach(
            unused -> {
              final var tx = createPendingTransaction(TRANSFER_GAS_LIMIT);

              final var txEvaluationContext =
                  new TransactionEvaluationContext(
                      blockSelectionContext.pendingBlockHeader(), tx, null, null, null);
              evaluateAndAssertSelected(txEvaluationContext, remainingGas(0));
            });

    assertThat(selector.getWorkingState()).isEqualTo(TRANSFER_GAS_LIMIT * txCount);

    // last tx is too big for the remaining gas
    final long tooBigGasLimit = BLOCK_GAS_LIMIT - (TRANSFER_GAS_LIMIT * txCount) + 1;

    final var bigTxEvaluationContext =
        new TransactionEvaluationContext(
            blockSelectionContext.pendingBlockHeader(),
            createPendingTransaction(tooBigGasLimit),
            null,
            null,
            null);
    evaluateAndAssertNotSelected(bigTxEvaluationContext, TX_TOO_LARGE_FOR_REMAINING_GAS);

    assertThat(selector.getWorkingState()).isEqualTo(TRANSFER_GAS_LIMIT * txCount);
  }

  @Test
  void identifyWhenBlockOccupancyIsAboveThreshold() {
    selectorsStateManager.blockSelectionStarted();

    // create 2 txs with a gas limit just above the min block occupancy ratio
    // so the first is accepted while the second not
    final long justAboveOccupancyRatioGasLimit =
        (long) (BLOCK_GAS_LIMIT * miningConfiguration.getMinBlockOccupancyRatio()) + 100;
    final var tx1 = createPendingTransaction(justAboveOccupancyRatioGasLimit);

    final var txEvaluationContext1 =
        new TransactionEvaluationContext(
            blockSelectionContext.pendingBlockHeader(), tx1, null, null, null);
    evaluateAndAssertSelected(txEvaluationContext1, remainingGas(0));

    assertThat(selector.getWorkingState()).isEqualTo(justAboveOccupancyRatioGasLimit);

    final var tx2 = createPendingTransaction(justAboveOccupancyRatioGasLimit);

    final var txEvaluationContext2 =
        new TransactionEvaluationContext(
            blockSelectionContext.pendingBlockHeader(), tx2, null, null, null);
    evaluateAndAssertNotSelected(txEvaluationContext2, BLOCK_OCCUPANCY_ABOVE_THRESHOLD);

    assertThat(selector.getWorkingState()).isEqualTo(justAboveOccupancyRatioGasLimit);
  }

  @Test
  void identifyWhenBlockIsFull() {
    when(blockSelectionContext.gasCalculator().getMinimumTransactionCost())
        .thenReturn(TRANSFER_GAS_LIMIT);

    selectorsStateManager.blockSelectionStarted();

    // allow to completely fill the block
    miningConfiguration.setMinBlockOccupancyRatio(1.0);

    // create 2 txs, where the first fill the block leaving less gas than the min required by a
    // transfer
    final long fillBlockGasLimit = BLOCK_GAS_LIMIT - TRANSFER_GAS_LIMIT + 1;
    final var tx1 = createPendingTransaction(fillBlockGasLimit);

    final var txEvaluationContext1 =
        new TransactionEvaluationContext(
            blockSelectionContext.pendingBlockHeader(), tx1, null, null, null);
    evaluateAndAssertSelected(txEvaluationContext1, remainingGas(0));

    assertThat(selector.getWorkingState()).isEqualTo(fillBlockGasLimit);

    final var tx2 = createPendingTransaction(TRANSFER_GAS_LIMIT);

    final var txEvaluationContext2 =
        new TransactionEvaluationContext(
            blockSelectionContext.pendingBlockHeader(), tx2, null, null, null);
    evaluateAndAssertNotSelected(txEvaluationContext2, BLOCK_FULL);

    assertThat(selector.getWorkingState()).isEqualTo(fillBlockGasLimit);
  }

  private void evaluateAndAssertSelected(
      final TransactionEvaluationContext txEvaluationContext,
      final TransactionProcessingResult transactionProcessingResult) {
    assertThat(selector.evaluateTransactionPreProcessing(txEvaluationContext)).isEqualTo(SELECTED);
    assertThat(
            selector.evaluateTransactionPostProcessing(
                txEvaluationContext, transactionProcessingResult))
        .isEqualTo(SELECTED);
  }

  private void evaluateAndAssertNotSelected(
      final TransactionEvaluationContext txEvaluationContext,
      final TransactionSelectionResult preProcessedResult) {
    assertThat(selector.evaluateTransactionPreProcessing(txEvaluationContext))
        .isEqualTo(preProcessedResult);
  }

  private PendingTransaction createPendingTransaction(final long gasLimit) {
    return PendingTransaction.newPendingTransaction(
        createTransaction(TransactionType.EIP1559, gasLimit), false, false, MAX_SCORE);
  }

  private Transaction createTransaction(final TransactionType type, final long gasLimit) {

    var tx =
        new TransactionTestFixture()
            .to(Optional.of(Address.fromHexString("0x634316eA0EE79c701c6F67C53A4C54cBAfd2316d")))
            .nonce(0)
            .gasLimit(gasLimit)
            .type(type)
            .maxFeePerGas(Optional.of(Wei.of(1000)))
            .maxPriorityFeePerGas(Optional.of(Wei.of(100)));

    return tx.createTransaction(KEYS);
  }

  private TransactionProcessingResult remainingGas(final long remainingGas) {
    final var txProcessingResult = mock(TransactionProcessingResult.class);
    when(txProcessingResult.getGasRemaining()).thenReturn(remainingGas);
    return txProcessingResult;
  }
}
