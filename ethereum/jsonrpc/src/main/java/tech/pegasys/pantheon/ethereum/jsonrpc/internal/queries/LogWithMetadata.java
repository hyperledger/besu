package tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.LogTopic;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.List;

import com.google.common.base.MoreObjects;

public class LogWithMetadata {

  private final int logIndex;
  private final long blockNumber;
  private final Hash blockHash;
  private final Hash transactionHash;
  private final int transactionIndex;
  private final Address address;
  private final BytesValue data;
  private final List<LogTopic> topics;
  private final boolean removed;

  private LogWithMetadata(
      final int logIndex,
      final long blockNumber,
      final Hash blockHash,
      final Hash transactionHash,
      final int transactionIndex,
      final Address address,
      final BytesValue data,
      final List<LogTopic> topics,
      final boolean removed) {
    this.logIndex = logIndex;
    this.blockNumber = blockNumber;
    this.blockHash = blockHash;
    this.transactionHash = transactionHash;
    this.transactionIndex = transactionIndex;
    this.address = address;
    this.data = data;
    this.topics = topics;
    this.removed = removed;
  }

  public static LogWithMetadata create(
      final int logIndex,
      final long blockNumber,
      final Hash blockHash,
      final Hash transactionHash,
      final int transactionIndex,
      final Address address,
      final BytesValue data,
      final List<LogTopic> topics,
      final boolean removed) {
    return new LogWithMetadata(
        logIndex,
        blockNumber,
        blockHash,
        transactionHash,
        transactionIndex,
        address,
        data,
        topics,
        removed);
  }

  // The index of this log within the entire ordered list of logs associated with the block this log
  // belongs to.
  public int getLogIndex() {
    return logIndex;
  }

  public long getBlockNumber() {
    return blockNumber;
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  public Hash getTransactionHash() {
    return transactionHash;
  }

  public int getTransactionIndex() {
    return transactionIndex;
  }

  public Address getAddress() {
    return address;
  }

  public BytesValue getData() {
    return data;
  }

  public List<LogTopic> getTopics() {
    return topics;
  }

  public boolean isRemoved() {
    return removed;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("logIndex", logIndex)
        .add("blockNumber", blockNumber)
        .add("blockHash", blockHash)
        .add("transactionHash", transactionHash)
        .add("transactionIndex", transactionIndex)
        .add("address", address)
        .add("data", data)
        .add("topics", topics)
        .add("removed", removed)
        .toString();
  }
}
