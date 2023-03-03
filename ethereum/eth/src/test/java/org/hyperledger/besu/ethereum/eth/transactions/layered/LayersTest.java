package org.hyperledger.besu.ethereum.eth.transactions.layered;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.eth.transactions.layered.LayersTest.Sender.S1;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolReplacementHandler;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.metrics.StubMetricsSystem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class LayersTest extends BaseTransactionPoolTest {
  protected static final int MAX_PRIO_TRANSACTIONS = 3;
  protected static final int MAX_FUTURE_FOR_SENDER = 9;

  final TransactionPoolConfiguration poolConfig =
      ImmutableTransactionPoolConfiguration.builder()
          .maxPrioritizedTransactions(MAX_PRIO_TRANSACTIONS)
          .maxFutureBySender(MAX_FUTURE_FOR_SENDER)
          .pendingTransactionsMaxCapacityBytes(createEIP1559Transaction(0, KEYS1).getSize() * 3)
          .build();

  protected final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  final TransactionPoolMetrics txPoolMetrics = new TransactionPoolMetrics(metricsSystem);

  final BiFunction<PendingTransaction, PendingTransaction, Boolean> transactionReplacementTester =
      (pt1, pt2) -> transactionReplacementTester(poolConfig, pt1, pt2);
  final EvictCollector evictCollector = new EvictCollector(txPoolMetrics);
  final SparseTransactions sparseTransactions =
      new SparseTransactions(
          poolConfig, evictCollector, txPoolMetrics, transactionReplacementTester);

  final ReadyTransactions readyTransactions =
      new ReadyTransactions(
          poolConfig, sparseTransactions, txPoolMetrics, transactionReplacementTester);

  final BaseFeePrioritizedTransactions prioritizedTransactions =
      new BaseFeePrioritizedTransactions(
          poolConfig,
          LayersTest::mockBlockHeader,
          readyTransactions,
          txPoolMetrics,
          transactionReplacementTester,
          FeeMarket.london(0L));

  @AfterEach
  void reset() {
    prioritizedTransactions.reset();
  }

  @ParameterizedTest
  @MethodSource("providerAddTransactions")
  void addTransactions(final Scenario scenario) {
    assertScenario(scenario);
  }

  @ParameterizedTest
  @MethodSource("providerRemoveTransactions")
  void removeTransactions(final Scenario scenario) {
    assertScenario(scenario);
  }

  @ParameterizedTest
  @MethodSource("providerInterleavedAddRemoveTransactions")
  void interleavedAddRemoveTransactions(final Scenario scenario) {
    assertScenario(scenario);
  }

  @ParameterizedTest
  @MethodSource("providerRemoveConfirmedTransactions")
  void removeConfirmedTransactions(final Scenario scenario) {
    assertScenario(scenario);
  }

  private void assertScenario(final Scenario scenario) {
    scenario.execute(prioritizedTransactions);

    assertThat(prioritizedTransactions.stream())
        .containsExactlyElementsOf(scenario.expectedPrioritized);

    assertThat(readyTransactions.stream()).containsExactlyElementsOf(scenario.expectedReady);

    // sparse txs are returned from the most recent to the oldest, so reverse it to make writing
    // scenarios easier
    Collections.reverse(scenario.expectedSparse);
    assertThat(sparseTransactions.stream()).containsExactlyElementsOf(scenario.expectedSparse);

    assertThat(evictCollector.evictedTxs)
        .containsExactlyInAnyOrderElementsOf(scenario.expectedDropped);
  }

  static Stream<Arguments> providerAddTransactions() {
    return Stream.of(
        Arguments.of(
            new Scenario("add first").addForSender(S1, 0).expectedPrioritizedForSender(S1, 0)),
        Arguments.of(
            new Scenario("add first sparse").addForSender(S1, 1).expectedSparseForSender(S1, 1)),
        Arguments.of(
            new Scenario("fill prioritized")
                .addForSender(S1, 0, 1, 2)
                .expectedPrioritizedForSender(S1, 0, 1, 2)),
        Arguments.of(
            new Scenario("fill prioritized reverse")
                .addForSender(S1, 2, 1, 0)
                .expectedPrioritizedForSender(S1, 0, 1, 2)),
        Arguments.of(
            new Scenario("fill prioritized mixed order 1")
                .addForSender(S1, 2, 0, 1)
                .expectedPrioritizedForSender(S1, 0, 1, 2)),
        Arguments.of(
            new Scenario("fill prioritized mixed order 2")
                .addForSender(S1, 0, 2, 1)
                .expectedPrioritizedForSender(S1, 0, 1, 2)),
        Arguments.of(
            new Scenario("overflow to ready")
                .addForSender(S1, 0, 1, 2, 3)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3)),
        Arguments.of(
            new Scenario("overflow to ready reverse")
                .addForSender(S1, 3, 2, 1, 0)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3)),
        Arguments.of(
            new Scenario("overflow to ready mixed order 1")
                .addForSender(S1, 3, 0, 2, 1)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3)),
        Arguments.of(
            new Scenario("overflow to ready mixed order 2")
                .addForSender(S1, 0, 3, 1, 2)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3)),
        Arguments.of(
            new Scenario("overflow to sparse")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6)),
        Arguments.of(
            new Scenario("overflow to sparse reverse")
                .addForSender(S1, 6, 5, 4, 3, 2, 1, 0)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                // 4,5,6 are evicted since max capacity of sparse layer is 3 txs
                .expectedReadyForSender(S1, 3)
                .expectedDroppedForSender(S1, 4, 5, 6)),
        Arguments.of(
            new Scenario("overflow to sparse mixed order 1")
                .addForSender(S1, 6, 0, 4, 1, 3, 2, 5)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6)),
        Arguments.of(
            new Scenario("overflow to sparse mixed order 2")
                .addForSender(S1, 0, 4, 6, 1, 5, 2, 3)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6)),
        Arguments.of(
            new Scenario("overflow to sparse mixed order 3")
                .addForSender(S1, 0, 1, 2, 3, 5, 6, 4)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6)),
        Arguments.of(
            new Scenario("nonce gap to sparse 1")
                .addForSender(S1, 0, 2)
                .expectedPrioritizedForSender(S1, 0)
                .expectedSparseForSender(S1, 2)),
        Arguments.of(
            new Scenario("nonce gap to sparse 2")
                .addForSender(S1, 0, 1, 2, 3, 5)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3)
                .expectedSparseForSender(S1, 5)),
        Arguments.of(
            new Scenario("fill sparse 1")
                .addForSender(S1, 2, 3, 5)
                .expectedSparseForSender(S1, 2, 3, 5)),
        Arguments.of(
            new Scenario("fill sparse 2")
                .addForSender(S1, 5, 3, 2)
                .expectedSparseForSender(S1, 5, 3, 2)),
        Arguments.of(
            new Scenario("overflow sparse 1")
                .addForSender(S1, 1, 2, 3, 4)
                .expectedSparseForSender(S1, 1, 2, 3)
                .expectedDroppedForSender(S1, 4)),
        Arguments.of(
            new Scenario("overflow sparse 2")
                .addForSender(S1, 4, 2, 3, 1)
                .expectedSparseForSender(S1, 2, 3, 1)
                .expectedDroppedForSender(S1, 4)),
        Arguments.of(
            new Scenario("overflow sparse 3")
                .addForSender(S1, 0, 4, 2, 3, 5)
                .expectedPrioritizedForSender(S1, 0)
                .expectedSparseForSender(S1, 4, 2, 3)
                .expectedDroppedForSender(S1, 5)));
  }

  static Stream<Arguments> providerRemoveTransactions() {
    return Stream.of(
        Arguments.of(new Scenario("remove not existing").removeForSender(S1, 0)),
        Arguments.of(new Scenario("add/remove first").addForSender(S1, 0).removeForSender(S1, 0)),
        Arguments.of(
            new Scenario("add/remove first sparse").addForSender(S1, 1).removeForSender(S1, 1)),
        Arguments.of(
            new Scenario("fill/remove prioritized 1")
                .addForSender(S1, 0, 1, 2)
                .removeForSender(S1, 0, 1, 2)),
        Arguments.of(
            new Scenario("fill/remove prioritized 2")
                .addForSender(S1, 0, 1, 2)
                .removeForSender(S1, 2, 1, 0)),
        Arguments.of(
            new Scenario("fill/remove prioritized reverse 1")
                .addForSender(S1, 2, 1, 0)
                .removeForSender(S1, 0, 1, 2)),
        Arguments.of(
            new Scenario("fill/remove prioritized reverse 2")
                .addForSender(S1, 2, 1, 0)
                .removeForSender(S1, 2, 1, 0)),
        Arguments.of(
            new Scenario("fill/remove first prioritized")
                .addForSender(S1, 0, 1, 2)
                .removeForSender(S1, 0)
                .expectedSparseForSender(S1, 1, 2)),
        Arguments.of(
            new Scenario("fill/remove last prioritized")
                .addForSender(S1, 0, 1, 2)
                .removeForSender(S1, 2)
                .expectedPrioritizedForSender(S1, 0, 1)),
        Arguments.of(
            new Scenario("fill/remove middle prioritized")
                .addForSender(S1, 0, 1, 2)
                .removeForSender(S1, 1)
                .expectedPrioritizedForSender(S1, 0)
                .expectedSparseForSender(S1, 2)),
        Arguments.of(
            new Scenario("overflow to ready then remove 1")
                .addForSender(S1, 0, 1, 2, 3)
                .removeForSender(S1, 2)
                .expectedPrioritizedForSender(S1, 0, 1)
                .expectedSparseForSender(S1, 3)),
        Arguments.of(
            new Scenario("overflow to ready then remove 2")
                .addForSender(S1, 0, 1, 2, 3, 4)
                .removeForSender(S1, 4)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3)),
        Arguments.of(
            new Scenario("overflow to ready then remove 3")
                .addForSender(S1, 0, 1, 2, 3, 4, 5)
                .removeForSender(S1, 4)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3)
                .expectedSparseForSender(S1, 5)),
        Arguments.of(
            new Scenario("overflow to ready then remove 4")
                .addForSender(S1, 0, 1, 2, 3, 4, 5)
                .removeForSender(S1, 3)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedSparseForSender(S1, 4, 5)),
        Arguments.of(
            new Scenario("overflow to ready then remove 5")
                .addForSender(S1, 0, 1, 2, 3, 4, 5)
                .removeForSender(S1, 0)
                .expectedSparseForSender(S1, 1, 2, 3)
                .expectedDroppedForSender(S1, 4, 5)),
        Arguments.of(
            new Scenario("overflow to sparse then remove 1")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6)
                .removeForSender(S1, 6)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)),
        Arguments.of(
            new Scenario("overflow to sparse then remove 2")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6)
                .removeForSender(S1, 5)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4)
                .expectedSparseForSender(S1, 6)),
        Arguments.of(
            new Scenario("overflow to sparse then remove 3")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6)
                .removeForSender(S1, 2)
                .expectedPrioritizedForSender(S1, 0, 1)
                .expectedSparseForSender(S1, 3, 4, 5)
                .expectedDroppedForSender(S1, 6)),
        Arguments.of(
            new Scenario("overflow to sparse then remove 4")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6, 7, 8)
                .removeForSender(S1, 7)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6, 8)),
        Arguments.of(
            new Scenario("overflow to sparse then remove 5")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6, 7, 8)
                .removeForSender(S1, 6)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 7, 8)),
        Arguments.of(
            new Scenario("overflow to sparse then remove 6")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6, 7, 8)
                .removeForSender(S1, 6)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 7, 8)));
  }

  static Stream<Arguments> providerInterleavedAddRemoveTransactions() {
    return Stream.of(
        Arguments.of(
            new Scenario("interleaved add/remove 1")
                .addForSender(S1, 0)
                .removeForSender(S1, 0)
                .addForSender(S1, 0)
                .expectedPrioritizedForSender(S1, 0)),
        Arguments.of(
            new Scenario("interleaved add/remove 2")
                .addForSender(S1, 0)
                .removeForSender(S1, 0)
                .addForSender(S1, 1)
                .expectedSparseForSender(S1, 1)),
        Arguments.of(
            new Scenario("interleaved add/remove 3")
                .addForSender(S1, 0)
                .removeForSender(S1, 0)
                .addForSender(S1, 1, 0)
                .expectedPrioritizedForSender(S1, 0, 1)));
  }

  static Stream<Arguments> providerRemoveConfirmedTransactions() {
    return Stream.of(
        Arguments.of(new Scenario("confirmed not exist").confirmedForSenders(S1, 0)),
        Arguments.of(
            new Scenario("confirmed below existing lower nonce")
                .addForSender(S1, 1)
                .confirmedForSenders(S1, 0)
                .expectedPrioritizedForSender(S1, 1)),
        Arguments.of(
            new Scenario("confirmed only one existing")
                .addForSender(S1, 0)
                .confirmedForSenders(S1, 0)),
        Arguments.of(
            new Scenario("confirmed above existing")
                .addForSender(S1, 0)
                .confirmedForSenders(S1, 1)),
        Arguments.of(
            new Scenario("confirmed some existing 1")
                .addForSender(S1, 0, 1)
                .confirmedForSenders(S1, 0)
                .expectedPrioritizedForSender(S1, 1)),
        Arguments.of(
            new Scenario("confirmed some existing 2")
                .addForSender(S1, 0, 1, 2)
                .confirmedForSenders(S1, 1)
                .expectedPrioritizedForSender(S1, 2)),
        Arguments.of(
            new Scenario("overflow to ready and confirmed some existing 1")
                .addForSender(S1, 0, 1, 2, 3)
                .confirmedForSenders(S1, 3)),
        Arguments.of(
            new Scenario("overflow to ready and confirmed some existing 2")
                .addForSender(S1, 0, 1, 2, 3)
                .confirmedForSenders(S1, 0)
                .expectedPrioritizedForSender(S1, 1, 2, 3)),
        Arguments.of(
            new Scenario("overflow to ready and confirmed some existing 3")
                .addForSender(S1, 0, 1, 2, 3, 4, 5)
                .confirmedForSenders(S1, 1)
                .expectedPrioritizedForSender(S1, 2, 3, 4)
                .expectedReadyForSender(S1, 5)),
        Arguments.of(
            new Scenario("overflow to ready and confirmed all existing")
                .addForSender(S1, 0, 1, 2, 3, 4, 5)
                .confirmedForSenders(S1, 5)),
        Arguments.of(
            new Scenario("overflow to ready and confirmed above highest nonce")
                .addForSender(S1, 0, 1, 2, 3, 4, 5)
                .confirmedForSenders(S1, 6)),
        Arguments.of(
            new Scenario("overflow to sparse and confirmed some existing 1")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6)
                .confirmedForSenders(S1, 0)
                .expectedPrioritizedForSender(S1, 1, 2, 3)
                .expectedReadyForSender(S1, 4, 5, 6)),
        Arguments.of(
            new Scenario("overflow to sparse and confirmed some existing 2")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6)
                .confirmedForSenders(S1, 2)
                .expectedPrioritizedForSender(S1, 3, 4, 5)
                .expectedReadyForSender(S1, 6)),
        Arguments.of(
            new Scenario("overflow to sparse and confirmed some existing 3")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6)
                .confirmedForSenders(S1, 3)
                .expectedPrioritizedForSender(S1, 4, 5, 6)),
        Arguments.of(
            new Scenario("overflow to sparse and confirmed some existing 4")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6, 7, 8)
                .confirmedForSenders(S1, 0)
                .expectedPrioritizedForSender(S1, 1, 2, 3)
                .expectedReadyForSender(S1, 4, 5, 6)
                .expectedSparseForSender(S1, 7, 8)),
        Arguments.of(
            new Scenario("overflow to sparse and confirmed some existing with gap")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 7, 8)
                .confirmedForSenders(S1, 0)
                .expectedPrioritizedForSender(S1, 1, 2, 3)
                .expectedReadyForSender(S1, 4, 5)
                .expectedSparseForSender(S1, 7, 8)),
        Arguments.of(
            new Scenario("overflow to sparse and confirmed all w/o gap")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6, 7, 8)
                .confirmedForSenders(S1, 8)));
  }

  private static BlockHeader mockBlockHeader() {
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBaseFee()).thenReturn(Optional.of(Wei.ONE));
    return blockHeader;
  }

  private static boolean transactionReplacementTester(
      final TransactionPoolConfiguration poolConfig,
      final PendingTransaction pt1,
      final PendingTransaction pt2) {
    final TransactionPoolReplacementHandler transactionReplacementHandler =
        new TransactionPoolReplacementHandler(poolConfig.getPriceBump());
    return transactionReplacementHandler.shouldReplace(pt1, pt2, mockBlockHeader());
  }

  static class Scenario extends BaseTransactionPoolTest {
    final String description;
    final List<Consumer<TransactionsLayer>> actions = new ArrayList<>();
    final List<PendingTransaction> expectedPrioritized = new ArrayList<>();
    final List<PendingTransaction> expectedReady = new ArrayList<>();
    final List<PendingTransaction> expectedSparse = new ArrayList<>();
    final List<PendingTransaction> expectedDropped = new ArrayList<>();

    final EnumMap<Sender, Integer> nonceBySender = new EnumMap<>(Sender.class);

    {
      Arrays.stream(Sender.values()).forEach(e -> nonceBySender.put(e, 0));
    }

    final EnumMap<Sender, Map<Long, PendingTransaction>> txsBySender = new EnumMap<>(Sender.class);

    {
      Arrays.stream(Sender.values()).forEach(e -> txsBySender.put(e, new HashMap<>()));
    }

    Scenario(final String description) {
      this.description = description;
    }

    Scenario addForSender(final Sender sender, final long... nonce) {
      Arrays.stream(nonce)
          .forEach(
              n -> {
                final var pendingTx = getOrCreate(sender, n);
                actions.add(layer -> layer.add(pendingTx, (int) (n - nonceBySender.get(sender))));
              });
      return this;
    }

    Scenario addForSenders(final Object... args) {
      for (int i = 0; i < args.length; i = i + 2) {
        final Sender sender = (Sender) args[i];
        final long nonce = (int) args[i + 1];
        addForSender(sender, nonce);
      }
      return this;
    }

    public Scenario confirmedForSenders(final Object... args) {
      final Map<Address, Long> maxConfirmedNonceBySender = new HashMap<>();
      for (int i = 0; i < args.length; i = i + 2) {
        final Sender sender = (Sender) args[i];
        final long nonce = (int) args[i + 1];
        maxConfirmedNonceBySender.put(sender.address, nonce);
      }
      actions.add(
          layer ->
              layer.blockAdded(FeeMarket.london(0L), mockBlockHeader(), maxConfirmedNonceBySender));
      return this;
    }

    Scenario setGap(final Sender sender, final int gap) {
      nonceBySender.put(sender, gap);
      return this;
    }

    void execute(final TransactionsLayer layer) {
      actions.forEach(action -> action.accept(layer));
    }

    private PendingTransaction getOrCreate(final Sender sender, final long nonce) {
      return txsBySender
          .get(sender)
          .computeIfAbsent(nonce, n -> createEIP1559PendingTransactions(n, sender.key));
    }

    private PendingTransaction get(final Sender sender, final long nonce) {
      return txsBySender.get(sender).get(nonce);
    }

    private PendingTransaction createEIP1559PendingTransactions(
        final long nonce, final KeyPair keys) {
      return createRemotePendingTransaction(createEIP1559Transaction(nonce, keys));
    }

    public Scenario expectedPrioritizedForSender(final Sender sender, final long... nonce) {
      return expectedForSender(expectedPrioritized, sender, nonce);
    }

    public Scenario expectedReadyForSender(final Sender sender, final long... nonce) {
      return expectedForSender(expectedReady, sender, nonce);
    }

    public Scenario expectedSparseForSender(final Sender sender, final long... nonce) {
      return expectedForSender(expectedSparse, sender, nonce);
    }

    public Scenario expectedDroppedForSender(final Sender sender, final long... nonce) {
      return expectedForSender(expectedDropped, sender, nonce);
    }

    public Scenario expectedPrioritizedForSenders(final Object... args) {
      return expectedForSenders(expectedPrioritized, args);
    }

    public Scenario expectedReadyForSenders(final Object... args) {
      return expectedForSenders(expectedReady, args);
    }

    public Scenario expectedSparseForSenders(final Object... args) {
      return expectedForSenders(expectedSparse, args);
    }

    public Scenario expectedDroppedForSenders(final Object... args) {
      return expectedForSenders(expectedDropped, args);
    }

    private Scenario expectedForSenders(
        final List<PendingTransaction> expected, final Object... args) {
      for (int i = 0; i < args.length; i = i + 2) {
        final Sender sender = (Sender) args[i];
        final long nonce = (int) args[i + 1];
        expected.add(get(sender, nonce));
      }
      return this;
    }

    private Scenario expectedForSender(
        final List<PendingTransaction> expected, final Sender sender, final long... nonce) {
      Arrays.stream(nonce)
          .forEach(
              n -> {
                expected.add(get(sender, n));
              });
      return this;
    }

    @Override
    public String toString() {
      return description;
    }

    public Scenario removeForSender(final Sender sender, final long... nonce) {
      Arrays.stream(nonce)
          .forEach(
              n -> {
                final var pendingTx = getOrCreate(sender, n);
                actions.add(layer -> layer.remove(pendingTx));
              });
      return this;
    }
  }

  enum Sender {
    S1,
    S2,
    S3;

    final KeyPair key;
    final Address address;

    Sender() {
      key = SIGNATURE_ALGORITHM.get().generateKeyPair();
      address = Util.publicKeyToAddress(key.getPublicKey());
    }
  }

  static class EvictCollector extends EndLayer {
    final List<PendingTransaction> evictedTxs = new ArrayList<>();

    public EvictCollector(final TransactionPoolMetrics metrics) {
      super(metrics);
    }

    @Override
    public TransactionAddedResult add(final PendingTransaction pendingTransaction, final int gap) {
      final var res = super.add(pendingTransaction, gap);
      evictedTxs.add(pendingTransaction);
      return res;
    }
  }
}
