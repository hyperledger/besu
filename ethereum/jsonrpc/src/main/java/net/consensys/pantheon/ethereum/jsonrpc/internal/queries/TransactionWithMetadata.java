package net.consensys.pantheon.ethereum.jsonrpc.internal.queries;

import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.Transaction;

public class TransactionWithMetadata {

  private final Transaction transaction;
  private final long blockNumber;
  private final Hash blockHash;
  private final int transactionIndex;

  public TransactionWithMetadata(
      final Transaction transaction,
      final long blockNumber,
      final Hash blockHash,
      final int transactionIndex) {
    this.transaction = transaction;
    this.blockNumber = blockNumber;
    this.blockHash = blockHash;
    this.transactionIndex = transactionIndex;
  }

  public Transaction getTransaction() {
    return transaction;
  }

  public long getBlockNumber() {
    return blockNumber;
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  public int getTransactionIndex() {
    return transactionIndex;
  }
}
