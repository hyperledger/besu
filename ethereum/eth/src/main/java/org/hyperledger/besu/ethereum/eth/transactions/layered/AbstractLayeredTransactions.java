package org.hyperledger.besu.ethereum.eth.transactions.layered;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;

public abstract class AbstractLayeredTransactions {
    protected final Map<Hash, PendingTransaction> pendingTransactions = new HashMap<>();
    protected final Map<Address, NavigableMap<Long, PendingTransaction>> readyBySender =
            new HashMap<>();
    protected long spaceUsed = 0;

    synchronized void reset() {
        pendingTransactions.clear();
        readyBySender.clear();
        spaceUsed = 0;
    }

    public Optional<Transaction> getTransactionByHash(final Hash transactionHash) {
        return Optional.ofNullable(pendingTransactions.get(transactionHash))
                .map(PendingTransaction::getTransaction);
    }

    public boolean containsTransaction(final Transaction transaction) {
      return pendingTransactions.containsKey(transaction.getHash());
    }

    /*
        @Override
        public Optional<Transaction> getTransactionByHash(final Hash transactionHash) {
          return Optional.ofNullable(pendingTransactions.get(transactionHash))
              .map(PendingTransaction::getTransaction);
        }
      */
    public synchronized Set<PendingTransaction> getPendingTransactions() {
      return new HashSet<>(pendingTransactions.values());
    }
}
