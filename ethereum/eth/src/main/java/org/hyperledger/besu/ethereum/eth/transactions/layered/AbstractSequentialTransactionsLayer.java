package org.hyperledger.besu.ethereum.eth.transactions.layered;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;

import java.util.NavigableMap;
import java.util.OptionalLong;
import java.util.function.BiFunction;

public abstract class AbstractSequentialTransactionsLayer extends AbstractTransactionsLayer {
  //  private static final Logger LOG =
  //      LoggerFactory.getLogger(AbstractSequentialTransactionsLayer.class);

  public AbstractSequentialTransactionsLayer(
      final TransactionPoolConfiguration poolConfig,
      final TransactionsLayer nextLayer,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester,
      final TransactionPoolMetrics metrics) {
    super(poolConfig, nextLayer, transactionReplacementTester, metrics);
  }

  @Override
  protected boolean gapsAllowed() {
    return false;
  }

  @Override
  protected void internalConfirmed(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final Address sender,
      final long maxConfirmedNonce) {
    // no -op
  }

  @Override
  protected void internalEvict(
      final NavigableMap<Long, PendingTransaction> senderTxs, final PendingTransaction evictedTx) {
    processRemove(senderTxs, evictedTx.getTransaction());
  }

  @Override
  public OptionalLong getNextNonceFor(final Address sender) {
    final OptionalLong nextLayerRes = nextLayer.getNextNonceFor(sender);
    if (nextLayerRes.isEmpty()) {
      final var senderTxs = readyBySender.get(sender);
      if (senderTxs != null) {
        return OptionalLong.of(senderTxs.lastKey() + 1);
      }
    }
    return nextLayerRes;
  }
}
