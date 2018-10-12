package tech.pegasys.pantheon.ethereum.jsonrpc.internal.results;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Log;
import tech.pegasys.pantheon.ethereum.core.LogTopic;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
  "address",
  "topics",
  "data",
  "blockNumber",
  "transactionHash",
  "transactionIndex",
  "blockHash",
  "logIndex",
  "removed"
})
public class TransactionReceiptLogResult {

  private final String address;
  private final List<String> topics;
  private final String data;
  private final String blockNumber;
  private final String transactionHash;
  private final String transactionIndex;
  private final String blockHash;
  private final String logIndex;
  private final boolean removed;

  public TransactionReceiptLogResult(
      final Log log,
      final long blockNumber,
      final Hash transactionHash,
      final Hash blockHash,
      final int transactionIndex,
      final int logIndex) {
    this.address = log.getLogger().toString();
    this.topics = new ArrayList<>(log.getTopics().size());

    for (final LogTopic topic : log.getTopics()) {
      topics.add(topic.toString());
    }

    this.data = log.getData().toString();
    this.blockNumber = Quantity.create(blockNumber);
    this.transactionHash = transactionHash.toString();
    this.transactionIndex = Quantity.create(transactionIndex);
    this.blockHash = blockHash.toString();
    this.logIndex = Quantity.create(logIndex);

    // TODO: Handle chain reorgs, i.e. return `true` if log is removed
    this.removed = false;
  }

  @JsonGetter(value = "address")
  public String getAddress() {
    return address;
  }

  @JsonGetter(value = "topics")
  public List<String> getTopics() {
    return topics;
  }

  @JsonGetter(value = "data")
  public String getData() {
    return data;
  }

  @JsonGetter(value = "blockNumber")
  public String getBlockNumber() {
    return blockNumber;
  }

  @JsonGetter(value = "transactionHash")
  public String getTransactionHash() {
    return transactionHash;
  }

  @JsonGetter(value = "transactionIndex")
  public String getTransactionIndex() {
    return transactionIndex;
  }

  @JsonGetter(value = "blockHash")
  public String getBlockHash() {
    return blockHash;
  }

  @JsonGetter(value = "logIndex")
  public String getLogIndex() {
    return logIndex;
  }

  @JsonGetter(value = "removed")
  public boolean isRemoved() {
    return removed;
  }
}
