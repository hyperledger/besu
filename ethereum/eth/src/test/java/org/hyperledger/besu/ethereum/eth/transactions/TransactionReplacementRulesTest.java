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
package org.hyperledger.besu.ethereum.eth.transactions;

import static java.util.Arrays.asList;
import static java.util.Optional.empty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.util.number.Percentage;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class TransactionReplacementRulesTest {

  public static Collection<Object[]> data() {
    return asList(
        new Object[][] {
          // TransactionReplacementByGasPriceRule
          //   basefee absent
          {frontierTx(5L), frontierTx(6L), empty(), 0, true},
          {frontierTx(5L), frontierTx(5L), empty(), 0, false},
          {frontierTx(5L), frontierTx(4L), empty(), 0, false},
          {frontierTx(100L), frontierTx(105L), empty(), 10, false},
          {frontierTx(100L), frontierTx(110L), empty(), 10, false},
          {frontierTx(100L), frontierTx(111L), empty(), 10, true},
          //   basefee present
          {frontierTx(5L), frontierTx(6L), Optional.of(Wei.of(3L)), 0, true},
          {frontierTx(5L), frontierTx(5L), Optional.of(Wei.of(3L)), 0, false},
          {frontierTx(5L), frontierTx(4L), Optional.of(Wei.of(3L)), 0, false},
          {frontierTx(100L), frontierTx(105L), Optional.of(Wei.of(3L)), 10, false},
          {frontierTx(100L), frontierTx(110L), Optional.of(Wei.of(3L)), 10, false},
          {frontierTx(100L), frontierTx(111L), Optional.of(Wei.of(3L)), 10, true},
          // TransactionReplacementByFeeMarketRule
          //  eip1559 replacing frontier
          {frontierTx(5L), eip1559Tx(3L, 6L), Optional.of(Wei.of(1L)), 0, false},
          {frontierTx(5L), eip1559Tx(3L, 5L), Optional.of(Wei.of(3L)), 0, false},
          {frontierTx(5L), eip1559Tx(3L, 6L), Optional.of(Wei.of(3L)), 0, true},
          //  frontier replacing 1559
          {eip1559Tx(3L, 8L), frontierTx(7L), Optional.of(Wei.of(4L)), 0, false},
          {eip1559Tx(3L, 8L), frontierTx(8L), Optional.of(Wei.of(4L)), 0, true},
          //  eip1559 replacing eip1559
          {eip1559Tx(3L, 6L), eip1559Tx(3L, 6L), Optional.of(Wei.of(3L)), 0, false},
          {eip1559Tx(3L, 6L), eip1559Tx(3L, 7L), Optional.of(Wei.of(3L)), 0, false},
          {eip1559Tx(3L, 6L), eip1559Tx(3L, 7L), Optional.of(Wei.of(4L)), 0, true},
          {eip1559Tx(10L, 200L), eip1559Tx(10L, 200L), Optional.of(Wei.of(90L)), 10, false},
          {eip1559Tx(10L, 200L), eip1559Tx(15L, 200L), Optional.of(Wei.of(90L)), 10, false},
          {eip1559Tx(10L, 200L), eip1559Tx(21L, 200L), Optional.of(Wei.of(90L)), 10, true},
          //  pathological, priority fee > max fee
          {eip1559Tx(8L, 6L), eip1559Tx(3L, 7L), Optional.of(Wei.of(3L)), 0, false},
          {eip1559Tx(8L, 6L), eip1559Tx(3L, 7L), Optional.of(Wei.of(4L)), 0, true},
          //  pathological, eip1559 without basefee
          {eip1559Tx(8L, 6L), eip1559Tx(3L, 7L), Optional.empty(), 0, false},
          {eip1559Tx(8L, 6L), eip1559Tx(3L, 7L), Optional.empty(), 0, false},
        });
  }

  @ParameterizedTest
  @MethodSource("data")
  public void shouldReplace(
      final PendingTransaction oldTx,
      final PendingTransaction newTx,
      final Optional<Wei> baseFee,
      final int priceBump,
      final boolean expected) {
    BlockHeader mockHeader = mock(BlockHeader.class);
    when(mockHeader.getBaseFee()).thenReturn(baseFee);

    assertThat(
            new TransactionPoolReplacementHandler(Percentage.fromInt(priceBump))
                .shouldReplace(oldTx, newTx, mockHeader))
        .isEqualTo(expected);
  }

  private static PendingTransaction frontierTx(final long price) {
    final PendingTransaction pendingTransaction = mock(PendingTransaction.class);
    final Transaction transaction =
        Transaction.builder()
            .chainId(BigInteger.ZERO)
            .type(TransactionType.FRONTIER)
            .gasPrice(Wei.of(price))
            .build();
    when(pendingTransaction.getTransaction()).thenReturn(transaction);
    when(pendingTransaction.getGasPrice()).thenReturn(Wei.of(price));
    return pendingTransaction;
  }

  private static PendingTransaction eip1559Tx(
      final long maxPriorityFeePerGas, final long maxFeePerGas) {
    final PendingTransaction pendingTransaction = mock(PendingTransaction.class);
    final Transaction transaction =
        Transaction.builder()
            .chainId(BigInteger.ZERO)
            .type(TransactionType.EIP1559)
            .maxPriorityFeePerGas(Wei.of(maxPriorityFeePerGas))
            .maxFeePerGas(Wei.of(maxFeePerGas))
            .build();
    when(pendingTransaction.getTransaction()).thenReturn(transaction);
    return pendingTransaction;
  }
}
