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

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "number",
  "hash",
  "mixHash",
  "parentHash",
  "nonce",
  "sha3Uncles",
  "logsBloom",
  "transactionsRoot",
  "stateRoot",
  "receiptsRoot",
  "miner",
  "difficulty",
  "totalDifficulty",
  "extraData",
  "baseFeePerGas",
  "size",
  "gasLimit",
  "gasUsed",
  "timestamp",
  "uncles",
  "transactions"
})
public class BlockResult implements JsonRpcResult {

  private final String number;
  protected final String hash;
  private final String mixHash;
  private final String parentHash;
  private final String nonce;
  private final String sha3Uncles;
  private final String logsBloom;
  private final String transactionsRoot;
  private final String stateRoot;
  private final String receiptsRoot;
  private final String miner;
  private final String difficulty;
  private final String totalDifficulty;
  private final String extraData;
  private final String baseFeePerGas;
  private final String size;
  private final String gasLimit;
  private final String gasUsed;
  private final String timestamp;
  protected final List<TransactionResult> transactions;
  private final List<JsonNode> ommers;
  private final String coinbase;

  public <T extends TransactionResult> BlockResult(
      final BlockHeader header,
      final List<TransactionResult> transactions,
      final List<JsonNode> ommers,
      final Difficulty totalDifficulty,
      final int size) {
    this(header, transactions, ommers, totalDifficulty, size, false);
  }

  public <T extends TransactionResult> BlockResult(
      final BlockHeader header,
      final List<TransactionResult> transactions,
      final List<JsonNode> ommers,
      final Difficulty totalDifficulty,
      final int size,
      final boolean includeCoinbase) {
    this.number = Quantity.create(header.getNumber());
    this.hash = header.getHash().toString();
    this.mixHash = header.getMixHash().toString();
    this.parentHash = header.getParentHash().toString();
    this.nonce = Quantity.longToPaddedHex(header.getNonce(), 8);
    this.sha3Uncles = header.getOmmersHash().toString();
    this.logsBloom = header.getLogsBloom().toString();
    this.transactionsRoot = header.getTransactionsRoot().toString();
    this.stateRoot = header.getStateRoot().toString();
    this.receiptsRoot = header.getReceiptsRoot().toString();
    this.miner = header.getCoinbase().toString();
    this.difficulty = Quantity.create(header.getDifficulty());
    this.totalDifficulty = Quantity.create(totalDifficulty);
    this.extraData = header.getExtraData().toString();
    this.baseFeePerGas = header.getBaseFee().map(Quantity::create).orElse(null);
    this.size = Quantity.create(size);
    this.gasLimit = Quantity.create(header.getGasLimit());
    this.gasUsed = Quantity.create(header.getGasUsed());
    this.timestamp = Quantity.create(header.getTimestamp());
    this.ommers = ommers;
    this.transactions = transactions;
    this.coinbase = includeCoinbase ? header.getCoinbase().toString() : null;
  }

  @JsonGetter(value = "number")
  public String getNumber() {
    return number;
  }

  @JsonGetter(value = "hash")
  public String getHash() {
    return hash;
  }

  @JsonGetter(value = "mixHash")
  public String getMixHash() {
    return mixHash;
  }

  @JsonGetter(value = "parentHash")
  public String getParentHash() {
    return parentHash;
  }

  @JsonGetter(value = "nonce")
  public String getNonce() {
    return nonce;
  }

  @JsonGetter(value = "sha3Uncles")
  public String getSha3Uncles() {
    return sha3Uncles;
  }

  @JsonGetter(value = "logsBloom")
  public String getLogsBloom() {
    return logsBloom;
  }

  @JsonGetter(value = "transactionsRoot")
  public String getTransactionsRoot() {
    return transactionsRoot;
  }

  @JsonGetter(value = "stateRoot")
  public String getStateRoot() {
    return stateRoot;
  }

  @JsonGetter(value = "receiptsRoot")
  public String getReceiptsRoot() {
    return receiptsRoot;
  }

  @JsonGetter(value = "miner")
  public String getMiner() {
    return miner;
  }

  @JsonGetter(value = "difficulty")
  public String getDifficulty() {
    return difficulty;
  }

  @JsonGetter(value = "totalDifficulty")
  public String getTotalDifficulty() {
    return totalDifficulty;
  }

  @JsonGetter(value = "extraData")
  public String getExtraData() {
    return extraData;
  }

  @JsonGetter(value = "baseFeePerGas")
  public String getBaseFeePerGas() {
    return baseFeePerGas;
  }

  @JsonGetter(value = "size")
  public String getSize() {
    return size;
  }

  @JsonGetter(value = "gasLimit")
  public String getGasLimit() {
    return gasLimit;
  }

  @JsonGetter(value = "gasUsed")
  public String getGasUsed() {
    return gasUsed;
  }

  @JsonGetter(value = "timestamp")
  public String getTimestamp() {
    return timestamp;
  }

  @JsonGetter(value = "uncles")
  public List<JsonNode> getOmmers() {
    return ommers;
  }

  @JsonGetter(value = "transactions")
  public List<TransactionResult> getTransactions() {
    return transactions;
  }

  @JsonGetter(value = "author")
  @JsonInclude(Include.NON_NULL)
  public String getCoinbase() {
    return coinbase;
  }
}
