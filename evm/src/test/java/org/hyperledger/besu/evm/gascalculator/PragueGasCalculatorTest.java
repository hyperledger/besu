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
package org.hyperledger.besu.evm.gascalculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
class PragueGasCalculatorTest {

  private static final long TARGET_BLOB_GAS_PER_BLOCK_PRAGUE = 0xC0000;
  private final PragueGasCalculator pragueGasCalculator = new PragueGasCalculator();
  @Mock private Transaction transaction;
  @Mock private MessageFrame messageFrame;

  @Test
  void testPrecompileSize() {
    PragueGasCalculator subject = new PragueGasCalculator();
    assertThat(subject.isPrecompile(Address.precompiled(0x14))).isFalse();
    assertThat(subject.isPrecompile(Address.BLS12_MAP_FP2_TO_G2)).isTrue();
  }

  @ParameterizedTest(
      name = "{index} - parent gas {0}, used gas {1}, blob target {2} new excess {3}")
  @MethodSource("blobGasses")
  public void shouldCalculateExcessBlobGasCorrectly(
      final long parentExcess, final long used, final long expected) {
    final long usedBlobGas = pragueGasCalculator.blobGasCost(used);
    assertThat(pragueGasCalculator.computeExcessBlobGas(parentExcess, usedBlobGas))
        .isEqualTo(expected);
  }

  Iterable<Arguments> blobGasses() {
    long sixBlobTargetGas = TARGET_BLOB_GAS_PER_BLOCK_PRAGUE;
    long newTargetCount = 6;

    return List.of(
        // New target count
        Arguments.of(0L, 0L, 0L),
        Arguments.of(sixBlobTargetGas, 0L, 0L),
        Arguments.of(newTargetCount, 0L, 0L),
        Arguments.of(0L, newTargetCount, 0L),
        Arguments.of(1L, newTargetCount, 1L),
        Arguments.of(
            pragueGasCalculator.blobGasCost(newTargetCount),
            1L,
            pragueGasCalculator.getBlobGasPerBlob()),
        Arguments.of(sixBlobTargetGas, newTargetCount, sixBlobTargetGas));
  }

  @Test
  void shouldCalculateRefundWithCodeDelegationAndNoSelfDestructs() {
    // Arrange
    when(messageFrame.getSelfDestructs()).thenReturn(Collections.emptySet());
    when(messageFrame.getGasRefund()).thenReturn(1000L);
    when(messageFrame.getRemainingGas()).thenReturn(5000L);
    when(transaction.getGasLimit()).thenReturn(100000L);
    when(transaction.getPayload()).thenReturn(Bytes.EMPTY);

    // Act
    long refund = pragueGasCalculator.calculateGasRefund(transaction, messageFrame, 500L);

    // Assert
    // execution refund = 1000 + 0 (self destructs) + 500 (code delegation) = 1500
    AssertionsForClassTypes.assertThat(refund)
        .isEqualTo(6500L); // 5000 (remaining) + min(1500 (execution refund), 19000 (max allowance))
  }

  @Test
  void shouldCalculateRefundWithMultipleSelfDestructs() {
    // Arrange
    Set<Address> selfDestructs = new HashSet<>();
    selfDestructs.add(Address.wrap(Bytes.random(20)));
    selfDestructs.add(Address.wrap(Bytes.random(20)));

    when(messageFrame.getSelfDestructs()).thenReturn(selfDestructs);
    when(messageFrame.getGasRefund()).thenReturn(1000L);
    when(messageFrame.getRemainingGas()).thenReturn(5000L);
    when(transaction.getGasLimit()).thenReturn(100000L);
    when(transaction.getPayload()).thenReturn(Bytes.EMPTY);

    // Act
    long refund = pragueGasCalculator.calculateGasRefund(transaction, messageFrame, 1000L);

    // Assert
    // execution refund = 1000 + 0 (self destructs EIP-3529) + 1000 (code delegation) = 2000
    AssertionsForClassTypes.assertThat(refund)
        .isEqualTo(7000L); // 5000 (remaining) + min(2000 (execution refund), 1900 (max allowance))
  }

  @Test
  void shouldRespectMaxRefundAllowance() {
    // Arrange
    when(messageFrame.getSelfDestructs()).thenReturn(Collections.emptySet());
    when(messageFrame.getGasRefund()).thenReturn(100000L);
    when(messageFrame.getRemainingGas()).thenReturn(20000L);
    when(transaction.getGasLimit()).thenReturn(100000L);
    when(transaction.getPayload()).thenReturn(Bytes.EMPTY);

    // Act
    long refund = pragueGasCalculator.calculateGasRefund(transaction, messageFrame, 1000L);

    // Assert
    // execution refund = 100000 + 1000 (code delegation) = 101000
    AssertionsForClassTypes.assertThat(refund)
        .isEqualTo(
            36000L); // 20000 (remaining) + min(101000 (execution refund), 16000 (max allowance))
  }

  @Test
  void shouldHandleZeroValuesCorrectly() {
    // Arrange
    when(messageFrame.getSelfDestructs()).thenReturn(Collections.emptySet());
    when(messageFrame.getGasRefund()).thenReturn(0L);
    when(messageFrame.getRemainingGas()).thenReturn(0L);
    when(transaction.getGasLimit()).thenReturn(100000L);
    when(transaction.getPayload()).thenReturn(Bytes.EMPTY);

    // Act
    long refund =
        pragueGasCalculator.calculateGasRefund(
            transaction,
            messageFrame,
            0L); // 0 (remaining) + min(0 (execution refund), 20000 (max allowance))

    // Assert
    AssertionsForClassTypes.assertThat(refund).isEqualTo(0L);
  }

  @Test
  void shouldRespectTransactionFloorCost() {
    // Arrange
    when(messageFrame.getSelfDestructs()).thenReturn(Collections.emptySet());
    when(messageFrame.getGasRefund()).thenReturn(100000L);
    when(messageFrame.getRemainingGas()).thenReturn(90000L);
    when(transaction.getGasLimit()).thenReturn(100000L);
    when(transaction.getPayload()).thenReturn(Bytes.EMPTY);

    // Act
    long refund = pragueGasCalculator.calculateGasRefund(transaction, messageFrame, 1000L);

    // Assert
    // refund allowance = 16000
    // execution gas used = 100000 (gas limit) - 20000 (remaining gas) - 16000 (refund allowance) =
    // 64000
    // floor cost = 21000 (base cost) + 0 (tokensInCallData * floor cost per token) = 21000
    AssertionsForClassTypes.assertThat(refund)
        .isEqualTo(
            79000L); // 100000 (gas limit) - max(8000 (execution gas used), 21000 (floor cost))
  }

  @Test
  void transactionFloorCostShouldBeAtLeastTransactionBaseCost() {
    // floor cost = 21000 (base cost) + 0
    AssertionsForClassTypes.assertThat(pragueGasCalculator.transactionFloorCost(Bytes.EMPTY))
        .isEqualTo(21000);
    // floor cost = 21000 (base cost) + 256 (tokensInCallData) * 10 (cost per token)
    AssertionsForClassTypes.assertThat(
            pragueGasCalculator.transactionFloorCost(Bytes.repeat((byte) 0x0, 256)))
        .isEqualTo(23560L);
    // floor cost = 21000 (base cost) + 256 * 4 (tokensInCallData) * 10 (cost per token)
    AssertionsForClassTypes.assertThat(
            pragueGasCalculator.transactionFloorCost(Bytes.repeat((byte) 0x1, 256)))
        .isEqualTo(31240L);
    // floor cost = 21000 (base cost) + 5 + (6 * 4) (tokensInCallData) * 10 (cost per token)
    AssertionsForClassTypes.assertThat(
            pragueGasCalculator.transactionFloorCost(
                Bytes.fromHexString("0x0001000100010001000101")))
        .isEqualTo(21290L);
  }
}
