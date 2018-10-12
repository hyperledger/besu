package tech.pegasys.pantheon.ethereum.jsonrpc.internal.results;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.TransactionReceiptWithMetadata;

import com.fasterxml.jackson.annotation.JsonGetter;

public class TransactionReceiptRootResult extends TransactionReceiptResult {

  private final String root;

  public TransactionReceiptRootResult(final TransactionReceiptWithMetadata receiptWithMetadata) {
    super(receiptWithMetadata);
    root = receipt.getStateRoot().toString();
  }

  @JsonGetter(value = "root")
  public String getRoot() {
    return root;
  }
}
