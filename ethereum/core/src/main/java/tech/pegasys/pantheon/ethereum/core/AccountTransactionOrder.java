package net.consensys.pantheon.ethereum.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

public class AccountTransactionOrder {

  private static final Comparator<Transaction> SORT_BY_NONCE =
      Comparator.comparing(Transaction::getNonce);
  private final SortedSet<Transaction> transactionsForSender = new TreeSet<>(SORT_BY_NONCE);
  private final SortedSet<Transaction> deferredTransactions = new TreeSet<>(SORT_BY_NONCE);

  public AccountTransactionOrder(final Stream<Transaction> senderTransactions) {
    senderTransactions.forEach(this.transactionsForSender::add);
  }

  /**
   * Determine the transactions from this sender that are able to be processed given that <code>
   * nextTransactionInPriorityOrder</code> has been reached in the normal priority order.
   *
   * <p>Transactions may be deferred from their place in normal priority order if the sender has
   * other transactions in the pool with lower nonces. Deferred transactions are delayed until the
   * transactions preceding them are reached.
   *
   * @param nextTransactionInPriorityOrder the next transaction to be processed in normal priority
   *     order. Must be from the sender this instance is ordering.
   * @return the transactions from this sender that are now due to be processed, in order.
   */
  public Iterable<Transaction> transactionsToProcess(
      final Transaction nextTransactionInPriorityOrder) {
    deferredTransactions.add(nextTransactionInPriorityOrder);
    final List<Transaction> transactionsToApply = new ArrayList<>();
    while (!deferredTransactions.isEmpty()
        && !transactionsForSender.isEmpty()
        && deferredTransactions.first().equals(transactionsForSender.first())) {
      final Transaction transaction = deferredTransactions.first();
      transactionsToApply.add(transaction);
      deferredTransactions.remove(transaction);
      transactionsForSender.remove(transaction);
    }
    return transactionsToApply;
  }
}
