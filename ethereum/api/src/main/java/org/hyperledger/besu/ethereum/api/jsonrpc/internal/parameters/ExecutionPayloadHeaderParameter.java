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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.LogsBloomFilter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes32;

/**
 * parentHash: DATA, 32 Bytes coinbase: DATA, 20 Bytes stateRoot: DATA, 32 Bytes receiptRoot: DATA,
 * 32 Bytes logsBloom: DATA, 256 Bytes random: DATA, 32 Bytes blockNumber: QUANTITY gasLimit:
 * QUANTITY gasUsed: QUANTITY timestamp: QUANTITY baseFeePerGas: QUANTITY blockHash: DATA, 32 Bytes
 * transactions: Array of TypedTransaction
 */
public class ExecutionPayloadHeaderParameter {
  private final Hash blockHash;
  private final Hash parentHash;
  private final Address coinbase;
  private final Hash stateRoot;
  private final long number;
  private final Bytes32 random;
  private final long baseFeePerGas;
  private final long gasLimit;
  private final long gasUsed;
  private final long timestamp;
  private final Hash receiptsRoot;
  private final LogsBloomFilter logsBloom;
  private final String transactionsRoot;

  @JsonCreator
  public ExecutionPayloadHeaderParameter(
      @JsonProperty("blockHash") final Hash blockHash,
      @JsonProperty("parentHash") final Hash parentHash,
      @JsonProperty("miner") final Address coinbase,
      @JsonProperty("stateRoot") final Hash stateRoot,
      @JsonProperty("number") final UnsignedLongParameter number,
      @JsonProperty("baseFeePerGas") final long baseFeePerGas,
      @JsonProperty("gasLimit") final UnsignedLongParameter gasLimit,
      @JsonProperty("gasUsed") final UnsignedLongParameter gasUsed,
      @JsonProperty("timestamp") final UnsignedLongParameter timestamp,
      @JsonProperty("receiptsRoot") final Hash receiptsRoot,
      @JsonProperty("logsBloom") final LogsBloomFilter logsBloom,
      @JsonProperty("random") final String random,
      @JsonProperty("transactionsRoot") final String transactionsRoot) {
    this.blockHash = blockHash;
    this.parentHash = parentHash;
    this.coinbase = coinbase;
    this.stateRoot = stateRoot;
    this.number = number.getValue();
    this.baseFeePerGas = baseFeePerGas;
    this.gasLimit = gasLimit.getValue();
    this.gasUsed = gasUsed.getValue();
    this.timestamp = timestamp.getValue();
    this.receiptsRoot = receiptsRoot;
    this.logsBloom = logsBloom;
    this.random = Bytes32.fromHexString(random);
    this.transactionsRoot = transactionsRoot;
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  public Hash getParentHash() {
    return parentHash;
  }

  public Address getCoinbase() {
    return coinbase;
  }

  public Hash getStateRoot() {
    return stateRoot;
  }

  public long getNumber() {
    return number;
  }

  public long getBaseFeePerGas() {
    return baseFeePerGas;
  }

  public long getGasLimit() {
    return gasLimit;
  }

  public long getGasUsed() {
    return gasUsed;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public Hash getReceiptsRoot() {
    return receiptsRoot;
  }

  public LogsBloomFilter getLogsBloom() {
    return logsBloom;
  }

  public Bytes32 getRandom() {
    return random;
  }

  /**
   * get the SSZ hashed transactions root.
   *
   * @return String
   */
  public String getTransactionsRoot() {
    return transactionsRoot;
  }
}
