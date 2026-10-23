/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.plugins.graphql.internal.query;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.LogTopic;
import org.hyperledger.besu.plugin.data.LogWithMetadata;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;

/**
 * A log of a transaction, with its block-relative index, built from the transaction's receipt.
 *
 * @param logIndex the index of the log in its block
 * @param blockNumber the number of the block
 * @param blockHash the hash of the block
 * @param transactionHash the hash of the transaction
 * @param transactionIndex the index of the transaction in its block
 * @param logger the address of the contract that emitted the log
 * @param data the data of the log
 * @param topics the topics of the log
 */
public record TransactionLog(
    int logIndex,
    long blockNumber,
    Hash blockHash,
    Hash transactionHash,
    int transactionIndex,
    Address logger,
    Bytes data,
    List<LogTopic> topics)
    implements LogWithMetadata {

  @Override
  public Address getLogger() {
    return logger;
  }

  @Override
  public List<LogTopic> getTopics() {
    return topics;
  }

  @Override
  public Bytes getData() {
    return data;
  }

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

  /**
   * Always false: GraphQL does not expose whether a log belongs to a block that left the canonical
   * chain.
   *
   * @return false
   */
  @Override
  public boolean isRemoved() {
    return false;
  }
}
