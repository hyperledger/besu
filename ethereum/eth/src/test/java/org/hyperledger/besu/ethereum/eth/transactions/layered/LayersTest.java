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
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolReplacementHandler;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.metrics.StubMetricsSystem;

import java.util.ArrayList;
import java.util.Arrays;
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
          .pendingTransactionsMaxCapacityBytes(createEIP1559Transaction(0).getSize() * 3)
          .build();

  protected final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  final TransactionPoolMetrics txPoolMetrics = new TransactionPoolMetrics(metricsSystem);

  final BiFunction<PendingTransaction, PendingTransaction, Boolean> transactionReplacementTester =
      (pt1, pt2) -> transactionReplacementTester(poolConfig, pt1, pt2);
  final EndLayer endLayer = new EndLayer(txPoolMetrics);
  final SparseTransactions sparseTransactions =
      new SparseTransactions(poolConfig, endLayer, txPoolMetrics, transactionReplacementTester);

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

    scenario.execute(prioritizedTransactions);

    assertThat(prioritizedTransactions.stream())
        .containsExactlyElementsOf(scenario.expectedPrioritized);

    assertThat(readyTransactions.stream()).containsExactlyElementsOf(scenario.expectedReady);

    assertThat(sparseTransactions.stream()).containsExactlyElementsOf(scenario.expectedSparse);
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

  static Stream<Arguments> providerAddTransactions() {
    return Stream.of(
        Arguments.of(
            new Scenario("add first").addForSender(S1, 0).expectedPrioritizedForSender(S1, 0)),
        Arguments.of(
            new Scenario("fill prioritized")
                .addForSender(S1, 0, 1, 2)
                .expectedPrioritizedForSender(S1, 0, 1, 2)),
        Arguments.of(
            new Scenario("reverse fill prioritized")
                .addForSender(S1, 2, 1, 0)
                .expectedPrioritizedForSender(S1, 0, 1, 2)));
    /*
       Arguments.of(
           "fill prioritized",
           0,
           new int[] {1, 2, 3},
           new int[] {1, 2, 3},
           new int[0],
           new int[0]),
       Arguments.of(
           "overflow to ready",
           0,
           new int[] {1, 2, 3, 4},
           new int[] {1, 2, 3},
           new int[] {4},
           new int[0]),
       Arguments.of(
           "overflow to sparse",
           0,
           new int[] {1, 2, 3, 4, 5, 6, 7},
           new int[] {1, 2, 3},
           new int[] {4, 5, 6},
           new int[] {7}),
       Arguments.of(
           "nonce gap to sparse",
           0,
           new int[] {1, 2, 3, 6},
           new int[] {1, 2, 3},
           new int[0],
           new int[] {6}),
       Arguments.of(
           "overflow to ready and nonce gap to sparse",
           0,
           new int[] {1, 2, 3, 4, 6},
           new int[] {1, 2, 3},
           new int[] {4},
           new int[] {6}),
       Arguments.of("first is sparse", 1, new int[] {1}, new int[0], new int[0], new int[] {1}));

    */
  }

  static class Scenario extends BaseTransactionPoolTest {
    final String description;
    final List<Consumer<TransactionsLayer>> actions = new ArrayList<>();
    final List<PendingTransaction> expectedPrioritized = new ArrayList<>();
    final List<PendingTransaction> expectedReady = new ArrayList<>();
    final List<PendingTransaction> expectedSparse = new ArrayList<>();

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

    Scenario setGap(final Sender sender, final int gap) {
      nonceBySender.put(sender, gap);
      return this;
    }

    void execute(final TransactionsLayer layer) {
      actions.forEach(action -> action.accept(layer));
    }

    private PendingTransaction getOrCreate(final Sender sender, final long nonce) {
      return txsBySender.get(sender).computeIfAbsent(nonce, this::createEIP1559PendingTransactions);
    }

    private PendingTransaction get(final Sender sender, final long nonce) {
      return txsBySender.get(sender).get(nonce);
    }

    private PendingTransaction createEIP1559PendingTransactions(final long nonce) {
      return createRemotePendingTransaction(createEIP1559Transaction(nonce));
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

    public Scenario expectedPrioritizedForSenders(final Object... args) {
      return expectedForSenders(expectedPrioritized, args);
    }

    public Scenario expectedReadyForSenders(final Object... args) {
      return expectedForSenders(expectedReady, args);
    }

    public Scenario expectedSparseForSenders(final Object... args) {
      return expectedForSenders(expectedSparse, args);
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
}
