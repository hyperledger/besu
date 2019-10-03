/*
 *
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Comparator;
import java.util.List;

import com.google.common.base.MoreObjects;

public class LogWithMetadata extends Log implements Comparable<LogWithMetadata> {

  private final int logIndex;
  private final long blockNumber;
  private final Hash blockHash;
  private final Hash transactionHash;
  private final int transactionIndex;
  private final Address address;
  private final BytesValue data;
  private final List<LogTopic> topics;
  private final boolean removed;

  public LogWithMetadata(
      final int logIndex,
      final long blockNumber,
      final Hash blockHash,
      final Hash transactionHash,
      final int transactionIndex,
      final Address address,
      final BytesValue data,
      final List<LogTopic> topics,
      final boolean removed) {
    super(address, data, topics);
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

  @Override
  public Address getLogger() {
    return address;
  }

  @Override
  public BytesValue getData() {
    return data;
  }

  @Override
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

  @Override
  public int compareTo(final LogWithMetadata other) {
    // here chronology is block chronology, not real time chronology.
    final var chronologicalOrder =
        Comparator.comparingLong(LogWithMetadata::getBlockNumber)
            .thenComparingInt(LogWithMetadata::getTransactionIndex)
            .thenComparingInt(LogWithMetadata::getLogIndex);

    final int removedCompare = Boolean.compare(this.removed, other.isRemoved());
    if (removedCompare != 0) { // sort removed logs (true) before added logs (false)
      return -removedCompare;
    } else if (removed) { // if we're sorting removed logs, reverse chronological
      return chronologicalOrder.reversed().compare(this, other);
    } else { // if we're sorting added logs, chronological
      return chronologicalOrder.compare(this, other);
    }
  }
}
