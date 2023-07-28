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
package org.hyperledger.besu.ethereum.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TransactionSimulatorResultTest {

  private TransactionSimulatorResult transactionSimulatorResult;

  @Mock private Transaction transaction;
  @Mock private TransactionProcessingResult result;

  @BeforeEach
  public void before() {
    this.transactionSimulatorResult = new TransactionSimulatorResult(transaction, result);
  }

  @Test
  public void shouldDelegateToTransactionProcessorResultWhenOutputIsCalled() {
    transactionSimulatorResult.getOutput();

    verify(result).getOutput();
  }

  @Test
  public void shouldDelegateToTransactionProcessorResultWhenIsSuccessfulIsCalled() {
    transactionSimulatorResult.isSuccessful();

    verify(result).isSuccessful();
  }

  @Test
  public void shouldUseTransactionProcessorResultAndTransactionToCalculateGasEstimate() {
    transactionSimulatorResult.getGasEstimate();

    verify(transaction).getGasLimit();
    verify(result).getGasRemaining();
  }

  @Test
  public void shouldCalculateCorrectGasEstimateWhenConsumedAllGas() {
    when(transaction.getGasLimit()).thenReturn(5L);
    when(result.getGasRemaining()).thenReturn(0L);

    assertThat(transactionSimulatorResult.getGasEstimate()).isEqualTo(5L);
  }

  @Test
  public void shouldCalculateCorrectGasEstimateWhenGasWasInsufficient() {
    when(transaction.getGasLimit()).thenReturn(1L);
    when(result.getGasRemaining()).thenReturn(-5L);

    assertThat(transactionSimulatorResult.getGasEstimate()).isEqualTo(6L);
  }

  @Test
  public void shouldCalculateCorrectGasEstimateWhenGasLimitWasSufficient() {
    when(transaction.getGasLimit()).thenReturn(10L);
    when(result.getGasRemaining()).thenReturn(3L);

    assertThat(transactionSimulatorResult.getGasEstimate()).isEqualTo(7L);
  }
}
