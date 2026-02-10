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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BlockGasAccountingStrategy}.
 *
 * <p>EIP-7778 changes how gas is accounted for at the block level:
 *
 * <ul>
 *   <li>Pre-EIP-7778 (FRONTIER): Block gas = gasLimit - gasRemaining (post-refund)
 *   <li>EIP-7778 (Amsterdam+): Block gas = estimateGasUsedByTransaction (pre-refund)
 * </ul>
 */
public class BlockGasAccountingStrategyTest {

  private static final long GAS_LIMIT = 100_000L;
  private static final long GAS_REMAINING = 30_000L;
  // Pre-refund gas used: gasLimit - gasRemaining = 70,000
  private static final long PRE_REFUND_GAS = GAS_LIMIT - GAS_REMAINING;

  @Test
  public void frontierStrategy_usesPostRefundGas() {
    // Setup: Transaction with gas limit 100k, 30k remaining after execution
    final Transaction tx = mock(Transaction.class);
    when(tx.getGasLimit()).thenReturn(GAS_LIMIT);

    final TransactionProcessingResult result = mock(TransactionProcessingResult.class);
    when(result.getGasRemaining()).thenReturn(GAS_REMAINING);
    when(result.getEstimateGasUsedByTransaction()).thenReturn(PRE_REFUND_GAS);

    // Frontier strategy: gasLimit - gasRemaining = 100,000 - 30,000 = 70,000
    final long blockGas = BlockGasAccountingStrategy.FRONTIER.calculateBlockGas(tx, result);

    assertThat(blockGas).isEqualTo(PRE_REFUND_GAS);
  }

  @Test
  public void eip7778Strategy_usesPreRefundGas() {
    // Setup: Transaction with gas limit 100k, processed with pre-refund gas of 70k
    final Transaction tx = mock(Transaction.class);
    when(tx.getGasLimit()).thenReturn(GAS_LIMIT);

    final TransactionProcessingResult result = mock(TransactionProcessingResult.class);
    when(result.getGasRemaining()).thenReturn(GAS_REMAINING);
    when(result.getEstimateGasUsedByTransaction()).thenReturn(PRE_REFUND_GAS);

    // EIP-7778 strategy: uses estimateGasUsedByTransaction directly = 70,000
    final long blockGas = BlockGasAccountingStrategy.EIP7778.calculateBlockGas(tx, result);

    assertThat(blockGas).isEqualTo(PRE_REFUND_GAS);
  }

  @Test
  public void strategiesDifferWhenRefundsApply() {
    // Setup: Simulate a transaction with SSTORE refunds
    // - Gas limit: 100,000
    // - Gas remaining after refund applied: 40,000 (post-refund remaining)
    // - Actual execution used 70,000 gas before refunds
    // - Refund of 10,000 was applied
    final long gasRemainingAfterRefund = 40_000L;
    final long preRefundGasUsed = 70_000L;

    final Transaction tx = mock(Transaction.class);
    when(tx.getGasLimit()).thenReturn(GAS_LIMIT);

    final TransactionProcessingResult result = mock(TransactionProcessingResult.class);
    when(result.getGasRemaining()).thenReturn(gasRemainingAfterRefund);
    when(result.getEstimateGasUsedByTransaction()).thenReturn(preRefundGasUsed);

    // Frontier: 100,000 - 40,000 = 60,000 (benefits from refund)
    final long frontierGas = BlockGasAccountingStrategy.FRONTIER.calculateBlockGas(tx, result);
    // EIP-7778: 70,000 (no refund benefit for block accounting)
    final long eip7778Gas = BlockGasAccountingStrategy.EIP7778.calculateBlockGas(tx, result);

    assertThat(frontierGas).isEqualTo(60_000L);
    assertThat(eip7778Gas).isEqualTo(70_000L);
    // EIP-7778 accounts for more gas, preventing block gas limit circumvention
    assertThat(eip7778Gas).isGreaterThan(frontierGas);
  }

  @Test
  public void strategiesEqualWhenNoRefunds() {
    // When there are no refunds, both strategies should produce the same result
    final long gasUsed = 50_000L;
    final long gasRemaining = GAS_LIMIT - gasUsed;

    final Transaction tx = mock(Transaction.class);
    when(tx.getGasLimit()).thenReturn(GAS_LIMIT);

    final TransactionProcessingResult result = mock(TransactionProcessingResult.class);
    when(result.getGasRemaining()).thenReturn(gasRemaining);
    when(result.getEstimateGasUsedByTransaction()).thenReturn(gasUsed);

    final long frontierGas = BlockGasAccountingStrategy.FRONTIER.calculateBlockGas(tx, result);
    final long eip7778Gas = BlockGasAccountingStrategy.EIP7778.calculateBlockGas(tx, result);

    assertThat(frontierGas).isEqualTo(gasUsed);
    assertThat(eip7778Gas).isEqualTo(gasUsed);
  }
}
