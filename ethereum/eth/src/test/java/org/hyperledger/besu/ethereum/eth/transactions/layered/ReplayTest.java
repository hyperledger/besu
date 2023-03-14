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
package org.hyperledger.besu.ethereum.eth.transactions.layered;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolReplacementHandler;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.metrics.StubMetricsSystem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.Splitter;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class ReplayTest {
  private final TransactionPoolConfiguration poolConfig =
      ImmutableTransactionPoolConfiguration.builder().build();

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final TransactionPoolMetrics txPoolMetrics = new TransactionPoolMetrics(metricsSystem);

  private BlockHeader currBlockHeader;

  @Test
  public void replay() throws IOException {
    try (BufferedReader br =
        new BufferedReader(
            new InputStreamReader(
                getClass().getResourceAsStream("/txpool/tx.csv"), StandardCharsets.UTF_8))) {
      currBlockHeader = mockBlockHeader(br.readLine());
      final BaseFeeMarket baseFeeMarket = FeeMarket.london(0L);

      final AbstractPrioritizedTransactions prioritizedTransactions =
          createLayers(poolConfig, txPoolMetrics, baseFeeMarket);
      final LayeredPendingTransactions pendingTransactions =
          new LayeredPendingTransactions(poolConfig, prioritizedTransactions, txPoolMetrics);
      br.lines()
          .forEach(
              line -> {
                try {
                  final String[] commaSplit = line.split(",");
                  final String type = commaSplit[0];
                  switch (type) {
                    case "T":
                      System.out.println("T:" + commaSplit[1]);
                      processTransaction(commaSplit, pendingTransactions);
                      break;
                    case "B":
                      System.out.println("B:" + commaSplit[1]);
                      processBlock(commaSplit, prioritizedTransactions, baseFeeMarket);
                      break;
                    case "S":
                      System.out.println("S");
                      assertStats(line, pendingTransactions);
                      break;
                    default:
                      throw new IllegalArgumentException("Unexpected first field value " + type);
                  }
                } catch (Throwable throwable) {
                  fail(line);
                  throw throwable;
                }
              });
    }
  }

  private BlockHeader mockBlockHeader(final String line) {
    final List<String> commaSplit = Splitter.on(',').splitToList(line);
    final long number = Long.parseLong(commaSplit.get(0));
    final Wei initBaseFee = Wei.of(new BigInteger(commaSplit.get(1)));
    final long gasUsed = Long.parseLong(commaSplit.get(2));
    final long gasLimit = Long.parseLong(commaSplit.get(3));

    final BlockHeader mockHeader = mock(BlockHeader.class);
    when(mockHeader.getNumber()).thenReturn(number);
    when(mockHeader.getBaseFee()).thenReturn(Optional.of(initBaseFee));
    when(mockHeader.getGasUsed()).thenReturn(gasUsed);
    when(mockHeader.getGasLimit()).thenReturn(gasLimit);

    return mockHeader;
  }

  private BaseFeePrioritizedTransactions createLayers(
      final TransactionPoolConfiguration poolConfig,
      final TransactionPoolMetrics txPoolMetrics,
      final BaseFeeMarket baseFeeMarket) {
    final EvictCollectorLayer evictCollector = new EvictCollectorLayer(txPoolMetrics);
    final SparseTransactions sparseTransactions =
        new SparseTransactions(
            poolConfig, evictCollector, txPoolMetrics, this::transactionReplacementTester);

    final ReadyTransactions readyTransactions =
        new ReadyTransactions(
            poolConfig, sparseTransactions, txPoolMetrics, this::transactionReplacementTester);

    return new BaseFeePrioritizedTransactions(
        poolConfig,
        () -> currBlockHeader,
        readyTransactions,
        txPoolMetrics,
        this::transactionReplacementTester,
        baseFeeMarket);
  }

  private void assertStats(
      final String line, final LayeredPendingTransactions pendingTransactions) {
    final String statsString = line.substring(2);
    assertThat(pendingTransactions.logStats()).as(line).endsWith(statsString);
  }

  private void processBlock(
      final String[] commaSplit,
      final AbstractPrioritizedTransactions prioritizedTransactions,
      final FeeMarket feeMarket) {
    final Bytes bytes = Bytes.fromHexString(commaSplit[commaSplit.length - 1]);
    final RLPInput rlpInput = new BytesValueRLPInput(bytes, false);
    final BlockHeader blockHeader =
        BlockHeader.readFrom(rlpInput, new MainnetBlockHeaderFunctions());

    final Map<Address, Long> maxNonceBySender = new HashMap<>();
    for (int i = 3; i < commaSplit.length - 1; i += 2) {
      final Address sender = Address.fromHexString(commaSplit[i]);
      final long nonce = Long.parseLong(commaSplit[i + 1]);
      maxNonceBySender.put(sender, nonce);
    }

    prioritizedTransactions.blockAdded(feeMarket, blockHeader, maxNonceBySender);
  }

  private void processTransaction(
      final String[] commaSplit, final LayeredPendingTransactions pendingTransactions) {
    final Bytes rlp = Bytes.fromHexString(commaSplit[commaSplit.length - 1]);
    final Transaction tx = Transaction.readFrom(rlp);
    final Account mockAccount = mock(Account.class);
    when(mockAccount.getNonce()).thenReturn(Long.parseLong(commaSplit[4]));
    pendingTransactions.addRemoteTransaction(tx, Optional.of(mockAccount));
  }

  private boolean transactionReplacementTester(
      final PendingTransaction pt1, final PendingTransaction pt2) {
    return transactionReplacementTester(poolConfig, pt1, pt2);
  }

  private boolean transactionReplacementTester(
      final TransactionPoolConfiguration poolConfig,
      final PendingTransaction pt1,
      final PendingTransaction pt2) {
    final TransactionPoolReplacementHandler transactionReplacementHandler =
        new TransactionPoolReplacementHandler(poolConfig.getPriceBump());
    return transactionReplacementHandler.shouldReplace(pt1, pt2, currBlockHeader);
  }
}
