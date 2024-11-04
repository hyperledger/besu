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
package org.hyperledger.besu.ethereum.eth.transactions.layered;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.hyperledger.besu.ethereum.eth.transactions.layered.LayeredRemovalReason.PoolRemovalReason.INVALIDATED;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.transactions.BlobCache;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
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
import org.hyperledger.besu.testutil.DeterministicEthScheduler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import com.google.common.base.Splitter;
import kotlin.ranges.LongRange;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplayTest {
  private static final Logger LOG = LoggerFactory.getLogger(ReplayTest.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final TransactionPoolMetrics txPoolMetrics = new TransactionPoolMetrics(metricsSystem);
  protected final EthScheduler ethScheduler = new DeterministicEthScheduler();

  private final Address senderToLog =
      Address.fromHexString("0xf7445f4b8a07921bf882175470dc8f7221c53996");

  private BlockHeader currBlockHeader;

  /**
   * Ignored by default since this is useful to debug issues having a dump of txs that could be
   * quite big and so could take many minutes to execute. To generate the input file for the test
   * enable the LOG_FOR_REPLAY logger by adding these parts to the log4j2 configuration: in the
   * Appenders section add
   *
   * <pre>{@code
   * <RollingFile name="txCSV" fileName="/data/besu/tx.csv" filePattern="/data/besu/tx-%d{MM-dd-yyyy}-%i.csv.gz">
   *    <PatternLayout>
   *        <Pattern>%m%n</Pattern>
   *    </PatternLayout>
   *    <Policies>
   *        <OnStartupTriggeringPolicy />
   *    </Policies>
   * </RollingFile>
   * }</pre>
   *
   * in the Loggers section add
   *
   * <pre>{@code
   * <Logger name="LOG_FOR_REPLAY" level="TRACE" additivity="false">
   *    <AppenderRef ref="txCSV" />
   * </Logger>
   * }</pre>
   *
   * restart and let it run until you need it, then copy the CSV in the test resource folder.
   *
   * @throws IOException when fails to read the resource
   */
  @Test
  @Disabled("Provide a replay file to run the test on demand")
  public void replay() throws IOException {
    SignatureAlgorithmFactory.setDefaultInstance();
    try (BufferedReader br =
        new BufferedReader(
            new InputStreamReader(
                new GZIPInputStream(getClass().getResourceAsStream("/tx.csv.gz")),
                StandardCharsets.UTF_8))) {
      currBlockHeader = mockBlockHeader(br.readLine());
      final BaseFeeMarket baseFeeMarket = FeeMarket.london(0L);

      final TransactionPoolConfiguration poolConfig =
          ImmutableTransactionPoolConfiguration.builder()
              .prioritySenders(readPrioritySenders(br.readLine()))
              .maxPrioritizedTransactionsByType(readMaxPrioritizedByType(br.readLine()))
              .build();

      final AbstractPrioritizedTransactions prioritizedTransactions =
          createLayers(poolConfig, txPoolMetrics, baseFeeMarket);
      final LayeredPendingTransactions pendingTransactions =
          new LayeredPendingTransactions(poolConfig, prioritizedTransactions, ethScheduler);
      br.lines()
          .forEach(
              line -> {
                try {
                  final String[] commaSplit = line.split(",");
                  final String type = commaSplit[0];
                  switch (type) {
                    case "T":
                      System.out.println(
                          "T:"
                              + commaSplit[1]
                              + " @ "
                              + Instant.ofEpochMilli(Long.parseLong(commaSplit[2])));
                      processTransaction(commaSplit, pendingTransactions, prioritizedTransactions);
                      break;
                    case "B":
                      System.out.println("B:" + commaSplit[1]);
                      processBlock(commaSplit, prioritizedTransactions, baseFeeMarket);
                      break;
                    case "S":
                      // ToDo: commented since not always working, needs fix
                      // System.out.println("S");
                      // assertStats(line, pendingTransactions);
                      break;
                    case "D":
                      System.out.println("D:" + commaSplit[1]);
                      processInvalid(commaSplit, prioritizedTransactions);
                      break;
                    default:
                      throw new IllegalArgumentException("Unexpected first field value " + type);
                  }
                } catch (Throwable throwable) {
                  fail(line, throwable);
                }
              });
    }
  }

  private Map<TransactionType, Integer> readMaxPrioritizedByType(final String line) {
    return Arrays.stream(line.split(","))
        .map(e -> e.split("="))
        .collect(
            Collectors.toMap(
                a -> TransactionType.valueOf(a[0]),
                a -> Integer.parseInt(a[1]),
                (a, b) -> a,
                () -> new EnumMap<>(TransactionType.class)));
  }

  private List<Address> readPrioritySenders(final String line) {
    return Arrays.stream(line.split(",")).map(Address::fromHexString).toList();
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
    final BiFunction<PendingTransaction, PendingTransaction, Boolean> txReplacementTester =
        (tx1, tx2) -> transactionReplacementTester(poolConfig, tx1, tx2);
    final SparseTransactions sparseTransactions =
        new SparseTransactions(
            poolConfig,
            ethScheduler,
            evictCollector,
            txPoolMetrics,
            txReplacementTester,
            new BlobCache());

    final ReadyTransactions readyTransactions =
        new ReadyTransactions(
            poolConfig,
            ethScheduler,
            sparseTransactions,
            txPoolMetrics,
            txReplacementTester,
            new BlobCache());
    return new BaseFeePrioritizedTransactions(
        poolConfig,
        () -> currBlockHeader,
        ethScheduler,
        readyTransactions,
        txPoolMetrics,
        txReplacementTester,
        baseFeeMarket,
        new BlobCache(),
        MiningConfiguration.newDefault());
  }

  // ToDo: commented since not always working, needs fix
  //  private void assertStats(
  //      final String line, final LayeredPendingTransactions pendingTransactions) {
  //    final String statsString = line.substring(2);
  //    assertThat(pendingTransactions.logStats()).as(line).endsWith(statsString);
  //  }

  private void processBlock(
      final String[] commaSplit,
      final AbstractPrioritizedTransactions prioritizedTransactions,
      final FeeMarket feeMarket) {
    final Bytes bytes = Bytes.fromHexString(commaSplit[commaSplit.length - 1]);
    final RLPInput rlpInput = new BytesValueRLPInput(bytes, false);
    final BlockHeader blockHeader =
        BlockHeader.readFrom(rlpInput, new MainnetBlockHeaderFunctions());

    final Map<Address, Long> maxNonceBySender = new HashMap<>();
    int i = 3;
    if (!commaSplit[i].equals("")) {
      while (!commaSplit[i].equals("R")) {
        final Address sender = Address.fromHexString(commaSplit[i]);
        final long nonce = Long.parseLong(commaSplit[i + 1]);
        maxNonceBySender.put(sender, nonce);
        i += 2;
      }
    } else {
      ++i;
    }

    ++i;
    final Map<Address, LongRange> nonceRangeBySender = new HashMap<>();
    if (!commaSplit[i].equals("")) {
      for (; i < commaSplit.length - 1; i += 3) {
        final Address sender = Address.fromHexString(commaSplit[i]);
        final long start = Long.parseLong(commaSplit[i + 1]);
        final long end = Long.parseLong(commaSplit[i + 2]);
        nonceRangeBySender.put(sender, new LongRange(start, end));
      }
    }

    if (maxNonceBySender.containsKey(senderToLog) || nonceRangeBySender.containsKey(senderToLog)) {
      LOG.warn(
          "B {} M {} R {} Before {}",
          blockHeader.getNumber(),
          maxNonceBySender.get(senderToLog),
          nonceRangeBySender.get(senderToLog),
          prioritizedTransactions.logSender(senderToLog));
    }
    prioritizedTransactions.blockAdded(feeMarket, blockHeader, maxNonceBySender);
    if (maxNonceBySender.containsKey(senderToLog) || nonceRangeBySender.containsKey(senderToLog)) {
      LOG.warn("After {}", prioritizedTransactions.logSender(senderToLog));
    }
  }

  private void processTransaction(
      final String[] commaSplit,
      final LayeredPendingTransactions pendingTransactions,
      final AbstractPrioritizedTransactions prioritizedTransactions) {
    final Bytes rlp = Bytes.fromHexString(commaSplit[commaSplit.length - 1]);
    final Transaction tx = Transaction.readFrom(rlp);
    final Account mockAccount = mock(Account.class);
    final long nonce = Long.parseLong(commaSplit[4]);
    when(mockAccount.getNonce()).thenReturn(nonce);
    if (tx.getSender().equals(senderToLog)) {
      LOG.warn(
          "N {} T {}, Before {}",
          nonce,
          tx.getNonce(),
          prioritizedTransactions.logSender(senderToLog));
    }
    assertThat(
            pendingTransactions.addTransaction(
                PendingTransaction.newPendingTransaction(tx, false, false),
                Optional.of(mockAccount)))
        .isNotEqualTo(TransactionAddedResult.INTERNAL_ERROR);
    if (tx.getSender().equals(senderToLog)) {
      LOG.warn("After {}", prioritizedTransactions.logSender(senderToLog));
    }
  }

  private void processInvalid(
      final String[] commaSplit, final AbstractPrioritizedTransactions prioritizedTransactions) {
    final Bytes rlp = Bytes.fromHexString(commaSplit[commaSplit.length - 1]);
    final Transaction tx = Transaction.readFrom(rlp);
    if (tx.getSender().equals(senderToLog)) {
      LOG.warn("D {}, Before {}", tx.getNonce(), prioritizedTransactions.logSender(senderToLog));
    }
    prioritizedTransactions.remove(new PendingTransaction.Remote(tx), INVALIDATED);
    if (tx.getSender().equals(senderToLog)) {
      LOG.warn("After {}", prioritizedTransactions.logSender(senderToLog));
    }
  }

  private boolean transactionReplacementTester(
      final TransactionPoolConfiguration poolConfig,
      final PendingTransaction pt1,
      final PendingTransaction pt2) {
    final TransactionPoolReplacementHandler transactionReplacementHandler =
        new TransactionPoolReplacementHandler(
            poolConfig.getPriceBump(), poolConfig.getBlobPriceBump());
    return transactionReplacementHandler.shouldReplace(pt1, pt2, currBlockHeader);
  }
}
