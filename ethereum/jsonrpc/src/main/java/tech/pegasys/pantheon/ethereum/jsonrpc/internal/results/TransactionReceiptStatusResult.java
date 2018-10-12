package tech.pegasys.pantheon.ethereum.jsonrpc.internal.results;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.TransactionReceiptWithMetadata;

import com.fasterxml.jackson.annotation.JsonGetter;

public class TransactionReceiptStatusResult extends TransactionReceiptResult {

  private final String status;

  public TransactionReceiptStatusResult(final TransactionReceiptWithMetadata receiptWithMetadata) {
    super(receiptWithMetadata);
    status = Quantity.create(receipt.getStatus());
  }

  @JsonGetter(value = "status")
  public String getStatus() {
    return status;
  }
}
