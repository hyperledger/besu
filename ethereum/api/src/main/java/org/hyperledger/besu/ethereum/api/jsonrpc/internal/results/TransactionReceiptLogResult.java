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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** The type Transaction receipt log result. */
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

  /**
   * Instantiates a new Transaction receipt log result.
   *
   * @param log the log
   * @param blockNumber the block number
   * @param transactionHash the transaction hash
   * @param blockHash the block hash
   * @param transactionIndex the transaction index
   * @param logIndex the log index
   */
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

  /**
   * Gets address.
   *
   * @return the address
   */
  @JsonGetter(value = "address")
  public String getAddress() {
    return address;
  }

  /**
   * Gets topics.
   *
   * @return the topics
   */
  @JsonGetter(value = "topics")
  public List<String> getTopics() {
    return topics;
  }

  /**
   * Gets data.
   *
   * @return the data
   */
  @JsonGetter(value = "data")
  public String getData() {
    return data;
  }

  /**
   * Gets block number.
   *
   * @return the block number
   */
  @JsonGetter(value = "blockNumber")
  public String getBlockNumber() {
    return blockNumber;
  }

  /**
   * Gets transaction hash.
   *
   * @return the transaction hash
   */
  @JsonGetter(value = "transactionHash")
  public String getTransactionHash() {
    return transactionHash;
  }

  /**
   * Gets transaction index.
   *
   * @return the transaction index
   */
  @JsonGetter(value = "transactionIndex")
  public String getTransactionIndex() {
    return transactionIndex;
  }

  /**
   * Gets block hash.
   *
   * @return the block hash
   */
  @JsonGetter(value = "blockHash")
  public String getBlockHash() {
    return blockHash;
  }

  /**
   * Gets log index.
   *
   * @return the log index
   */
  @JsonGetter(value = "logIndex")
  public String getLogIndex() {
    return logIndex;
  }

  /**
   * Is removed boolean.
   *
   * @return the boolean
   */
  @JsonGetter(value = "removed")
  public boolean isRemoved() {
    return removed;
  }
}
