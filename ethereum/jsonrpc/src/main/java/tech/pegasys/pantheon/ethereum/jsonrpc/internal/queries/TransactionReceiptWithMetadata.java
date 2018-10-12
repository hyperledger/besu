package tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;

public class TransactionReceiptWithMetadata {
  private final TransactionReceipt receipt;
  private final Hash transactionHash;
  private final int transactionIndex;
  private final long gasUsed;
  private final long blockNumber;
  private final Hash blockHash;
  private final Transaction transaction;

  private TransactionReceiptWithMetadata(
      final TransactionReceipt receipt,
      final Transaction transaction,
      final Hash transactionHash,
      final int transactionIndex,
      final long gasUsed,
      final Hash blockHash,
      final long blockNumber) {
    this.receipt = receipt;
    this.transactionHash = transactionHash;
    this.transactionIndex = transactionIndex;
    this.gasUsed = gasUsed;
    this.blockHash = blockHash;
    this.blockNumber = blockNumber;
    this.transaction = transaction;
  }

  public static TransactionReceiptWithMetadata create(
      final TransactionReceipt receipt,
      final Transaction transaction,
      final Hash transactionHash,
      final int transactionIndex,
      final long gasUsed,
      final Hash blockHash,
      final long blockNumber) {
    return new TransactionReceiptWithMetadata(
        receipt, transaction, transactionHash, transactionIndex, gasUsed, blockHash, blockNumber);
  }

  public TransactionReceipt getReceipt() {
    return receipt;
  }

  public Hash getTransactionHash() {
    return transactionHash;
  }

  public Transaction getTransaction() {
    return transaction;
  }

  public int getTransactionIndex() {
    return transactionIndex;
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  public long getBlockNumber() {
    return blockNumber;
  }

  // The gas used for this particular transaction (as opposed to cumulativeGas which is included in
  // the receipt itself)
  public long getGasUsed() {
    return gasUsed;
  }
}
