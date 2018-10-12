package net.consensys.pantheon.ethereum.jsonrpc.internal.results;

import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.Log;
import net.consensys.pantheon.ethereum.core.TransactionReceipt;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.TransactionReceiptWithMetadata;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
  "blockHash",
  "blockNumber",
  "contractAddress",
  "cumulativeGasUsed",
  "from",
  "gasUsed",
  "logs",
  "logsBloom",
  "root",
  "status",
  "to",
  "transactionHash",
  "transactionIndex"
})
public abstract class TransactionReceiptResult {

  private final String blockHash;
  private final String blockNumber;
  private final String contractAddress;
  private final String cumulativeGasUsed;
  private final String from;
  private final String gasUsed;
  private final List<TransactionReceiptLogResult> logs;
  private final String logsBloom;
  private final String to;
  private final String transactionHash;
  private final String transactionIndex;

  protected final TransactionReceipt receipt;

  public TransactionReceiptResult(final TransactionReceiptWithMetadata receiptWithMetadata) {

    receipt = receiptWithMetadata.getReceipt();

    this.blockHash = receiptWithMetadata.getBlockHash().toString();
    this.blockNumber = Quantity.create(receiptWithMetadata.getBlockNumber());
    this.contractAddress =
        receiptWithMetadata.getTransaction().contractAddress().map(Address::toString).orElse(null);
    this.cumulativeGasUsed = Quantity.create(receipt.getCumulativeGasUsed());
    this.from = receiptWithMetadata.getTransaction().getSender().toString();
    this.gasUsed = Quantity.create(receiptWithMetadata.getGasUsed());
    this.logs =
        logReceipts(
            receipt.getLogs(),
            receiptWithMetadata.getBlockNumber(),
            receiptWithMetadata.getTransaction().hash(),
            receiptWithMetadata.getBlockHash(),
            receiptWithMetadata.getTransactionIndex());
    this.logsBloom = receipt.getBloomFilter().toString();
    this.to = receiptWithMetadata.getTransaction().getTo().map(BytesValue::toString).orElse(null);
    this.transactionHash = receiptWithMetadata.getTransaction().hash().toString();
    this.transactionIndex = Quantity.create(receiptWithMetadata.getTransactionIndex());
  }

  @JsonGetter(value = "blockHash")
  public String getBlockHash() {
    return blockHash;
  }

  @JsonGetter(value = "blockNumber")
  public String getBlockNumber() {
    return blockNumber;
  }

  @JsonGetter(value = "contractAddress")
  public String getContractAddress() {
    return contractAddress;
  }

  @JsonGetter(value = "cumulativeGasUsed")
  public String getCumulativeGasUsed() {
    return cumulativeGasUsed;
  }

  @JsonGetter(value = "from")
  public String getFrom() {
    return from;
  }

  @JsonGetter(value = "gasUsed")
  public String getGasUsed() {
    return gasUsed;
  }

  @JsonGetter(value = "logs")
  public List<TransactionReceiptLogResult> getLogs() {
    return logs;
  }

  @JsonGetter(value = "logsBloom")
  public String getLogsBloom() {
    return logsBloom;
  }

  @JsonGetter(value = "to")
  public String getTo() {
    return to;
  }

  @JsonGetter(value = "transactionHash")
  public String getTransactionHash() {
    return transactionHash;
  }

  @JsonGetter(value = "transactionIndex")
  public String getTransactionIndex() {
    return transactionIndex;
  }

  private List<TransactionReceiptLogResult> logReceipts(
      final List<Log> logs,
      final long blockNumber,
      final Hash transactionHash,
      final Hash blockHash,
      final int transactionIndex) {
    final List<TransactionReceiptLogResult> logResults = new ArrayList<>(logs.size());

    for (int i = 0; i < logs.size(); i++) {
      final Log log = logs.get(i);
      logResults.add(
          new TransactionReceiptLogResult(
              log, blockNumber, transactionHash, blockHash, transactionIndex, i));
    }

    return logResults;
  }
}
