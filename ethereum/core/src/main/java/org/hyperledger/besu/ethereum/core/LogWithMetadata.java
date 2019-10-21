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

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.MoreObjects;

public class LogWithMetadata extends Log {

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

  public static List<LogWithMetadata> generate(
      final Block block, final List<TransactionReceipt> receipts, final boolean removed) {
    final List<LogWithMetadata> logsWithMetadata = new ArrayList<>();
    for (int txi = 0; txi < receipts.size(); ++txi) {
      final TransactionReceipt currentReceipt = receipts.get(txi);
      for (int li = 0; li < currentReceipt.getLogs().size(); ++li) {
        final Log currentLog = currentReceipt.getLogs().get(li);
        logsWithMetadata.add(
            new LogWithMetadata(
                li,
                block.getHeader().getNumber(),
                block.getHash(),
                block.getBody().getTransactions().get(txi).getHash(),
                txi,
                currentLog.getLogger(),
                currentLog.getData(),
                currentLog.getTopics(),
                removed));
      }
    }
    return logsWithMetadata;
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
}
