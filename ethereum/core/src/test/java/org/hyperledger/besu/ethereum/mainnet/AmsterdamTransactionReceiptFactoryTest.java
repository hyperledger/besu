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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Log;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MainnetProtocolSpecs.AmsterdamTransactionReceiptFactory}.
 *
 * <p>EIP-7778 introduces a new gasSpent field in receipts that tracks the post-refund gas (what
 * users actually pay), while cumulativeGasUsed now represents pre-refund gas (for block
 * accounting).
 */
public class AmsterdamTransactionReceiptFactoryTest {

  @Test
  public void createsReceiptWithGasSpent() {
    // Setup
    final long preRefundGasUsed = 70_000L; // cumulativeGasUsed for block accounting
    final long postRefundGasSpent = 60_000L; // what user actually pays (after refunds)

    final TransactionProcessingResult result = mock(TransactionProcessingResult.class);
    when(result.isSuccessful()).thenReturn(true);
    when(result.getGasSpent()).thenReturn(postRefundGasSpent);
    when(result.getLogs()).thenReturn(List.of());
    when(result.getRevertReason()).thenReturn(Optional.empty());

    final MainnetProtocolSpecs.AmsterdamTransactionReceiptFactory factory =
        new MainnetProtocolSpecs.AmsterdamTransactionReceiptFactory(false);

    // Execute
    final TransactionReceipt receipt =
        factory.create(TransactionType.EIP1559, result, preRefundGasUsed);

    // Verify
    assertThat(receipt.getCumulativeGasUsed()).isEqualTo(preRefundGasUsed);
    assertThat(receipt.getGasSpent()).isPresent();
    assertThat(receipt.getGasSpent().get()).isEqualTo(postRefundGasSpent);
    assertThat(receipt.getStatus()).isEqualTo(1);
  }

  @Test
  public void gasSpentLessThanCumulativeGasWhenRefundsApply() {
    // When SSTORE refunds apply, gasSpent should be less than cumulativeGasUsed
    final long preRefundGasUsed = 80_000L;
    final long refundAmount = 15_000L;
    final long postRefundGasSpent = preRefundGasUsed - refundAmount;

    final TransactionProcessingResult result = mock(TransactionProcessingResult.class);
    when(result.isSuccessful()).thenReturn(true);
    when(result.getGasSpent()).thenReturn(postRefundGasSpent);
    when(result.getLogs()).thenReturn(List.of());
    when(result.getRevertReason()).thenReturn(Optional.empty());

    final MainnetProtocolSpecs.AmsterdamTransactionReceiptFactory factory =
        new MainnetProtocolSpecs.AmsterdamTransactionReceiptFactory(false);

    final TransactionReceipt receipt =
        factory.create(TransactionType.EIP1559, result, preRefundGasUsed);

    // gasSpent should be less than cumulativeGasUsed due to refunds
    assertThat(receipt.getGasSpent().get()).isLessThan(receipt.getCumulativeGasUsed());
    assertThat(receipt.getCumulativeGasUsed() - receipt.getGasSpent().get())
        .isEqualTo(refundAmount);
  }

  @Test
  public void gasSpentEqualsCumulativeGasWhenNoRefunds() {
    // Without refunds, gasSpent equals cumulativeGasUsed
    final long gasUsed = 50_000L;

    final TransactionProcessingResult result = mock(TransactionProcessingResult.class);
    when(result.isSuccessful()).thenReturn(true);
    when(result.getGasSpent()).thenReturn(gasUsed);
    when(result.getLogs()).thenReturn(List.of());
    when(result.getRevertReason()).thenReturn(Optional.empty());

    final MainnetProtocolSpecs.AmsterdamTransactionReceiptFactory factory =
        new MainnetProtocolSpecs.AmsterdamTransactionReceiptFactory(false);

    final TransactionReceipt receipt = factory.create(TransactionType.FRONTIER, result, gasUsed);

    assertThat(receipt.getGasSpent().get()).isEqualTo(receipt.getCumulativeGasUsed());
  }

  @Test
  public void createsFailedReceiptWithGasSpent() {
    final long preRefundGasUsed = 100_000L;
    final long postRefundGasSpent = 100_000L; // No refunds for failed transactions

    final TransactionProcessingResult result = mock(TransactionProcessingResult.class);
    when(result.isSuccessful()).thenReturn(false);
    when(result.getGasSpent()).thenReturn(postRefundGasSpent);
    when(result.getLogs()).thenReturn(List.of());
    when(result.getRevertReason()).thenReturn(Optional.empty());

    final MainnetProtocolSpecs.AmsterdamTransactionReceiptFactory factory =
        new MainnetProtocolSpecs.AmsterdamTransactionReceiptFactory(false);

    final TransactionReceipt receipt =
        factory.create(TransactionType.EIP1559, result, preRefundGasUsed);

    assertThat(receipt.getStatus()).isEqualTo(0);
    assertThat(receipt.getGasSpent()).isPresent();
    assertThat(receipt.getGasSpent().get()).isEqualTo(postRefundGasSpent);
  }

  @Test
  public void includesRevertReasonWhenEnabled() {
    final Bytes revertReason = Bytes.fromHexString("0x08c379a0");

    final TransactionProcessingResult result = mock(TransactionProcessingResult.class);
    when(result.isSuccessful()).thenReturn(false);
    when(result.getGasSpent()).thenReturn(50_000L);
    when(result.getLogs()).thenReturn(List.of());
    when(result.getRevertReason()).thenReturn(Optional.of(revertReason));

    final MainnetProtocolSpecs.AmsterdamTransactionReceiptFactory factory =
        new MainnetProtocolSpecs.AmsterdamTransactionReceiptFactory(true);

    final TransactionReceipt receipt = factory.create(TransactionType.EIP1559, result, 50_000L);

    assertThat(receipt.getRevertReason()).isPresent();
    assertThat(receipt.getRevertReason().get()).isEqualTo(revertReason);
  }

  @Test
  public void excludesRevertReasonWhenDisabled() {
    final Bytes revertReason = Bytes.fromHexString("0x08c379a0");

    final TransactionProcessingResult result = mock(TransactionProcessingResult.class);
    when(result.isSuccessful()).thenReturn(false);
    when(result.getGasSpent()).thenReturn(50_000L);
    when(result.getLogs()).thenReturn(List.of());
    when(result.getRevertReason()).thenReturn(Optional.of(revertReason));

    final MainnetProtocolSpecs.AmsterdamTransactionReceiptFactory factory =
        new MainnetProtocolSpecs.AmsterdamTransactionReceiptFactory(false);

    final TransactionReceipt receipt = factory.create(TransactionType.EIP1559, result, 50_000L);

    assertThat(receipt.getRevertReason()).isEmpty();
  }

  @Test
  public void includesLogsInReceipt() {
    // Create a real Log instead of mocking to avoid bloom filter issues
    final Log log = new Log(Address.ZERO, Bytes.EMPTY, List.of());
    final List<Log> logs = List.of(log);

    final TransactionProcessingResult result = mock(TransactionProcessingResult.class);
    when(result.isSuccessful()).thenReturn(true);
    when(result.getGasSpent()).thenReturn(21_000L);
    when(result.getLogs()).thenReturn(logs);
    when(result.getRevertReason()).thenReturn(Optional.empty());

    final MainnetProtocolSpecs.AmsterdamTransactionReceiptFactory factory =
        new MainnetProtocolSpecs.AmsterdamTransactionReceiptFactory(false);

    final TransactionReceipt receipt = factory.create(TransactionType.EIP1559, result, 21_000L);

    assertThat(receipt.getLogsList()).hasSize(1);
  }
}
