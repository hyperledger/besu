package org.hyperledger.besu.ethereum.eth.transactions.layered;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Predicate;

public class EndLayer extends BaseTransactionsLayer {

  private final TransactionPoolMetrics metrics;

  public EndLayer(final TransactionPoolMetrics metrics) {
    this.metrics = metrics;
  }

  @Override
  public String name() {
    return "end";
  }

  @Override
  public void reset() {}

  @Override
  public Optional<Transaction> getByHash(final Hash transactionHash) {
    return Optional.empty();
  }

  @Override
  public boolean contains(final Transaction transaction) {
    return false;
  }

  @Override
  public Set<PendingTransaction> getAll() {
    return Set.of();
  }

  @Override
  public TransactionAddedResult add(final PendingTransaction pendingTransaction, final int gap) {
    notifyTransactionDropped(pendingTransaction);
    metrics.incrementRemoved(pendingTransaction.isReceivedFromLocalSource(), "dropped", name());
    return TransactionAddedResult.DROPPED;
  }

  @Override
  public void remove(final PendingTransaction pendingTransaction) {}

  @Override
  public void blockAdded(
      final FeeMarket feeMarket,
      final BlockHeader blockHeader,
      final Map<Address, Long> maxConfirmedNonceBySender) {}

  @Override
  public List<Transaction> getAllLocal() {
    return List.of();
  }

  @Override
  public int count() {
    return 0;
  }

  @Override
  public OptionalLong getNextNonceFor(final Address sender) {
    return OptionalLong.empty();
  }

  @Override
  public PendingTransaction promote(final Predicate<PendingTransaction> promotionFilter) {
    return null;
  }

  @Override
  public PendingTransaction promote(final Address sender, final long nonce) {
    return null;
  }
}
