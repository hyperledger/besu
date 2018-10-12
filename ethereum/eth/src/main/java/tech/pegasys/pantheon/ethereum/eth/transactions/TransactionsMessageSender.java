package tech.pegasys.pantheon.ethereum.eth.transactions;

import static java.util.stream.Collectors.toSet;

import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.messages.TransactionsMessage;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection.PeerNotConnected;

import java.util.Set;

class TransactionsMessageSender {

  private static final int MAX_BATCH_SIZE = 10;
  private final PeerTransactionTracker transactionTracker;

  public TransactionsMessageSender(final PeerTransactionTracker transactionTracker) {
    this.transactionTracker = transactionTracker;
  }

  public void sendTransactionsToPeers() {
    transactionTracker.getEthPeersWithUnsentTransactions().forEach(this::sendTransactionsToPeer);
  }

  private void sendTransactionsToPeer(final EthPeer peer) {
    final Set<Transaction> allTxToSend = transactionTracker.claimTransactionsToSendToPeer(peer);
    while (!allTxToSend.isEmpty()) {
      final Set<Transaction> subsetToSend =
          allTxToSend.stream().limit(MAX_BATCH_SIZE).collect(toSet());
      allTxToSend.removeAll(subsetToSend);
      try {
        peer.send(TransactionsMessage.create(subsetToSend));
      } catch (final PeerNotConnected e) {
        return;
      }
    }
  }
}
