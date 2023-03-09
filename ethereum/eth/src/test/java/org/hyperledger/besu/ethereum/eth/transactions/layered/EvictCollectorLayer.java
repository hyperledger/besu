package org.hyperledger.besu.ethereum.eth.transactions.layered;

import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;

import java.util.ArrayList;
import java.util.List;

public class EvictCollectorLayer extends EndLayer {
  final List<PendingTransaction> evictedTxs = new ArrayList<>();

  public EvictCollectorLayer(final TransactionPoolMetrics metrics) {
    super(metrics);
  }

  @Override
  public TransactionAddedResult add(final PendingTransaction pendingTransaction, final int gap) {
    final var res = super.add(pendingTransaction, gap);
    evictedTxs.add(pendingTransaction);
    return res;
  }

  public List<PendingTransaction> getEvictedTrancations() {
    return evictedTxs;
  }
}
