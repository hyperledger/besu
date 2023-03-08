package org.hyperledger.besu.ethereum.eth.transactions.layered;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionAddedListener;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionDroppedListener;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Predicate;

public interface TransactionsLayer {

  String name();

  void reset();

  Optional<Transaction> getByHash(Hash transactionHash);

  boolean contains(Transaction transaction);

  Set<PendingTransaction> getAll();

  TransactionAddedResult add(PendingTransaction pendingTransaction, int gap);

  // ToDo: find a more efficient way to handle remove and gaps
  void remove(PendingTransaction pendingTransaction);

  void blockAdded(
      FeeMarket feeMarket,
      BlockHeader blockHeader,
      final Map<Address, Long> maxConfirmedNonceBySender);

  List<Transaction> getAllLocal();

  int count();

  OptionalLong getNextNonceFor(Address sender);

  PendingTransaction promote(Predicate<PendingTransaction> promotionFilter);

  long subscribeToAdded(PendingTransactionAddedListener listener);

  void unsubscribeFromAdded(long id);

  long subscribeToDropped(PendingTransactionDroppedListener listener);

  void unsubscribeFromDropped(long id);

  PendingTransaction promote(Address sender, long nonce);

  void notifyAdded(PendingTransaction pendingTransaction);

  long getUsedSpace();
}
