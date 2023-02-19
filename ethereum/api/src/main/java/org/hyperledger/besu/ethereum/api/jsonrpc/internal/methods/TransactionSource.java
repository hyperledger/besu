package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TransactionSource implements Iterator<Transaction> {

  private final List<Transaction> transactions;
  private final AtomicInteger currentIndex = new AtomicInteger(0);

  public TransactionSource(final Block block) {
    this.transactions = block.getBody().getTransactions();
  }

  @Override
  public boolean hasNext() {
    return currentIndex.get() < (transactions.size() - 1);
  }

  @Override
  public Transaction next() {
    return transactions.get(currentIndex.getAndIncrement());
  }
}
