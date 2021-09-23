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
package org.hyperledger.besu.ethereum.api.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.BaseFeePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;

import java.util.Arrays;
import java.util.Collection;
import java.util.OptionalLong;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LatestNonceProviderTest {

  private final Address senderAdress = Address.fromHexString("1");

  private final BlockchainQueries blockchainQueries = mock(BlockchainQueries.class);
  private LatestNonceProvider nonceProvider;

  @Parameterized.Parameter public AbstractPendingTransactionsSorter pendingTransactions;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {mock(GasPricePendingTransactionsSorter.class)},
          {mock(BaseFeePendingTransactionsSorter.class)}
        });
  }

  @Before
  public void setUp() {
    nonceProvider = new LatestNonceProvider(blockchainQueries, pendingTransactions);
  }

  @Test
  public void nextNonceUsesTxPool() {
    final long highestNonceInPendingTransactions = 123;
    when(pendingTransactions.getNextNonceForSender(senderAdress))
        .thenReturn(OptionalLong.of(highestNonceInPendingTransactions));
    assertThat(nonceProvider.getNonce(senderAdress)).isEqualTo(highestNonceInPendingTransactions);
  }

  @Test
  public void nextNonceIsTakenFromBlockchainIfNoPendingTransactionResponse() {
    final long headBlockNumber = 8;
    final long nonceInBLockchain = 56;
    when(pendingTransactions.getNextNonceForSender(senderAdress)).thenReturn(OptionalLong.empty());
    when(blockchainQueries.headBlockNumber()).thenReturn(headBlockNumber);
    when(blockchainQueries.getTransactionCount(senderAdress, headBlockNumber))
        .thenReturn(nonceInBLockchain);
    assertThat(nonceProvider.getNonce(senderAdress)).isEqualTo(nonceInBLockchain);
  }
}
