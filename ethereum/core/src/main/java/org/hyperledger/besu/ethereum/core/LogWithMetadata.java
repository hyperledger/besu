/*
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
 */
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionReceipt;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;

public class LogWithMetadata extends Log
    implements org.hyperledger.besu.plugin.data.LogWithMetadata {

  private final int logIndex;
  private final long blockNumber;
  private final Hash blockHash;
  private final Hash transactionHash;
  private final int transactionIndex;
  private final boolean removed;

  public LogWithMetadata(
      final int logIndex,
      final long blockNumber,
      final Hash blockHash,
      final Hash transactionHash,
      final int transactionIndex,
      final Address address,
      final Bytes data,
      final List<LogTopic> topics,
      final boolean removed) {
    super(address, data, topics);
    this.logIndex = logIndex;
    this.blockNumber = blockNumber;
    this.blockHash = blockHash;
    this.transactionHash = transactionHash;
    this.transactionIndex = transactionIndex;
    this.removed = removed;
  }

  public static List<LogWithMetadata> generate(
      final int logIndexOffset,
      final TransactionReceipt receipt,
      final long number,
      final Hash blockHash,
      final Hash transactionHash,
      final int transactionIndex,
      final boolean removed) {
    return generate(
        logIndexOffset,
        receipt.getLogsList(),
        number,
        blockHash,
        transactionHash,
        transactionIndex,
        removed);
  }

  public static List<LogWithMetadata> generate(
      final Block block, final List<TransactionReceipt> receipts, final boolean removed) {
    final List<LogWithMetadata> logsWithMetadata = new ArrayList<>();
    int logIndexOffset = 0;
    for (int txi = 0; txi < receipts.size(); ++txi) {
      final List<LogWithMetadata> logs =
          generate(
              logIndexOffset,
              receipts.get(txi),
              block.getHeader().getNumber(),
              block.getHash(),
              block.getBody().getTransactions().get(txi).getHash(),
              txi,
              removed);
      logIndexOffset += logs.size();
      logsWithMetadata.addAll(logs);
    }
    return logsWithMetadata;
  }

  public static List<LogWithMetadata> generate(
      final int logIndexOffset,
      final PrivateTransactionReceipt receipt,
      final long number,
      final Hash blockHash,
      final Hash transactionHash,
      final int transactionIndex,
      final boolean removed) {
    return generate(
        logIndexOffset,
        receipt.getLogs(),
        number,
        blockHash,
        transactionHash,
        transactionIndex,
        removed);
  }

  private static List<LogWithMetadata> generate(
      final int logIndexOffset,
      final List<Log> receiptLogs,
      final long number,
      final Hash blockHash,
      final Hash transactionHash,
      final int transactionIndex,
      final boolean removed) {

    final List<LogWithMetadata> logs = new ArrayList<>();
    for (int logIndex = 0; logIndex < receiptLogs.size(); ++logIndex) {
      logs.add(
          new LogWithMetadata(
              logIndexOffset + logIndex,
              number,
              blockHash,
              transactionHash,
              transactionIndex,
              receiptLogs.get(logIndex).getLogger(),
              receiptLogs.get(logIndex).getData(),
              receiptLogs.get(logIndex).getTopics(),
              removed));
    }

    return logs;
  }

  // The index of this log within the entire ordered list of logs associated with the block this log
  // belongs to.
  @Override
  public int getLogIndex() {
    return logIndex;
  }

  @Override
  public long getBlockNumber() {
    return blockNumber;
  }

  @Override
  public Hash getBlockHash() {
    return blockHash;
  }

  @Override
  public Hash getTransactionHash() {
    return transactionHash;
  }

  @Override
  public int getTransactionIndex() {
    return transactionIndex;
  }

  @Override
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
        .add("address", getLogger())
        .add("data", getData())
        .add("topics", getTopics())
        .add("removed", removed)
        .toString();
  }

  public static LogWithMetadata fromPlugin(
      final org.hyperledger.besu.plugin.data.LogWithMetadata pluginObject) {
    return new LogWithMetadata(
        pluginObject.getLogIndex(),
        pluginObject.getBlockNumber(),
        pluginObject.getBlockHash(),
        pluginObject.getTransactionHash(),
        pluginObject.getTransactionIndex(),
        pluginObject.getLogger(),
        pluginObject.getData(),
        pluginObject.getTopics().stream().map(LogTopic::create).collect(Collectors.toList()),
        pluginObject.isRemoved());
  }
}
