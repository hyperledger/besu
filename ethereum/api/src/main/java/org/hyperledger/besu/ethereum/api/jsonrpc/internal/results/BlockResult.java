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

import static java.util.stream.Collectors.toList;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.WithdrawalParameter;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Withdrawal;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.tuweni.bytes.Bytes32;

/** The type Block result. */
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
  "transactions",
  "withdrawalsRoot",
  "withdrawals"
})
public class BlockResult implements JsonRpcResult {

  private final String number;

  /** The Hash. */
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

  /** The Transactions. */
  protected final List<TransactionResult> transactions;

  private final List<JsonNode> ommers;
  private final String coinbase;
  private final String withdrawalsRoot;
  private final List<WithdrawalParameter> withdrawals;

  private final String blobGasUsed;
  private final String excessBlobGas;
  private final String parentBeaconBlockRoot;

  /**
   * Instantiates a new Block result.
   *
   * @param header the header
   * @param transactions the transactions
   * @param ommers the ommers
   * @param totalDifficulty the total difficulty
   * @param size the size
   */
  public BlockResult(
      final BlockHeader header,
      final List<TransactionResult> transactions,
      final List<JsonNode> ommers,
      final Difficulty totalDifficulty,
      final int size) {
    this(header, transactions, ommers, totalDifficulty, size, false, Optional.empty());
  }

  /**
   * Instantiates a new Block result.
   *
   * @param header the header
   * @param transactions the transactions
   * @param ommers the ommers
   * @param totalDifficulty the total difficulty
   * @param size the size
   * @param includeCoinbase the include coinbase
   * @param withdrawals the withdrawals
   */
  public BlockResult(
      final BlockHeader header,
      final List<TransactionResult> transactions,
      final List<JsonNode> ommers,
      final Difficulty totalDifficulty,
      final int size,
      final boolean includeCoinbase,
      final Optional<List<Withdrawal>> withdrawals) {
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
    this.withdrawalsRoot = header.getWithdrawalsRoot().map(Hash::toString).orElse(null);
    this.withdrawals =
        withdrawals
            .map(w -> w.stream().map(WithdrawalParameter::fromWithdrawal).collect(toList()))
            .orElse(null);

    this.blobGasUsed = header.getBlobGasUsed().map(Quantity::create).orElse(null);
    this.excessBlobGas = header.getExcessBlobGas().map(Quantity::create).orElse(null);
    this.parentBeaconBlockRoot =
        header.getParentBeaconBlockRoot().map(Bytes32::toHexString).orElse(null);
  }

  /**
   * Gets number.
   *
   * @return the number
   */
  @JsonGetter(value = "number")
  public String getNumber() {
    return number;
  }

  /**
   * Gets hash.
   *
   * @return the hash
   */
  @JsonGetter(value = "hash")
  public String getHash() {
    return hash;
  }

  /**
   * Gets mix hash.
   *
   * @return the mix hash
   */
  @JsonGetter(value = "mixHash")
  public String getMixHash() {
    return mixHash;
  }

  /**
   * Gets parent hash.
   *
   * @return the parent hash
   */
  @JsonGetter(value = "parentHash")
  public String getParentHash() {
    return parentHash;
  }

  /**
   * Gets nonce.
   *
   * @return the nonce
   */
  @JsonGetter(value = "nonce")
  public String getNonce() {
    return nonce;
  }

  /**
   * Gets sha 3 uncles.
   *
   * @return the sha 3 uncles
   */
  @JsonGetter(value = "sha3Uncles")
  public String getSha3Uncles() {
    return sha3Uncles;
  }

  /**
   * Gets logs bloom.
   *
   * @return the logs bloom
   */
  @JsonGetter(value = "logsBloom")
  public String getLogsBloom() {
    return logsBloom;
  }

  /**
   * Gets transactions root.
   *
   * @return the transactions root
   */
  @JsonGetter(value = "transactionsRoot")
  public String getTransactionsRoot() {
    return transactionsRoot;
  }

  /**
   * Gets state root.
   *
   * @return the state root
   */
  @JsonGetter(value = "stateRoot")
  public String getStateRoot() {
    return stateRoot;
  }

  /**
   * Gets receipts root.
   *
   * @return the receipts root
   */
  @JsonGetter(value = "receiptsRoot")
  public String getReceiptsRoot() {
    return receiptsRoot;
  }

  /**
   * Gets miner.
   *
   * @return the miner
   */
  @JsonGetter(value = "miner")
  public String getMiner() {
    return miner;
  }

  /**
   * Gets difficulty.
   *
   * @return the difficulty
   */
  @JsonGetter(value = "difficulty")
  public String getDifficulty() {
    return difficulty;
  }

  /**
   * Gets total difficulty.
   *
   * @return the total difficulty
   */
  @JsonGetter(value = "totalDifficulty")
  public String getTotalDifficulty() {
    return totalDifficulty;
  }

  /**
   * Gets extra data.
   *
   * @return the extra data
   */
  @JsonGetter(value = "extraData")
  public String getExtraData() {
    return extraData;
  }

  /**
   * Gets base fee per gas.
   *
   * @return the base fee per gas
   */
  @JsonGetter(value = "baseFeePerGas")
  public String getBaseFeePerGas() {
    return baseFeePerGas;
  }

  /**
   * Gets size.
   *
   * @return the size
   */
  @JsonGetter(value = "size")
  public String getSize() {
    return size;
  }

  /**
   * Gets gas limit.
   *
   * @return the gas limit
   */
  @JsonGetter(value = "gasLimit")
  public String getGasLimit() {
    return gasLimit;
  }

  /**
   * Gets gas used.
   *
   * @return the gas used
   */
  @JsonGetter(value = "gasUsed")
  public String getGasUsed() {
    return gasUsed;
  }

  /**
   * Gets timestamp.
   *
   * @return the timestamp
   */
  @JsonGetter(value = "timestamp")
  public String getTimestamp() {
    return timestamp;
  }

  /**
   * Gets ommers.
   *
   * @return the ommers
   */
  @JsonGetter(value = "uncles")
  public List<JsonNode> getOmmers() {
    return ommers;
  }

  /**
   * Gets transactions.
   *
   * @return the transactions
   */
  @JsonGetter(value = "transactions")
  public List<TransactionResult> getTransactions() {
    return transactions;
  }

  /**
   * Gets coinbase.
   *
   * @return the coinbase
   */
  @JsonGetter(value = "author")
  @JsonInclude(Include.NON_NULL)
  public String getCoinbase() {
    return coinbase;
  }

  /**
   * Gets withdrawals root.
   *
   * @return the withdrawals root
   */
  @JsonGetter(value = "withdrawalsRoot")
  public String getWithdrawalsRoot() {
    return withdrawalsRoot;
  }

  /**
   * Gets withdrawals.
   *
   * @return the withdrawals
   */
  @JsonGetter(value = "withdrawals")
  public List<WithdrawalParameter> getWithdrawals() {
    return withdrawals;
  }

  /**
   * Gets blob gas used.
   *
   * @return the blob gas used
   */
  @JsonGetter(value = "blobGasUsed")
  public String getBlobGasUsed() {
    return blobGasUsed;
  }

  /**
   * Gets excess blob gas.
   *
   * @return the excess blob gas
   */
  @JsonGetter(value = "excessBlobGas")
  public String getExcessBlobGas() {
    return excessBlobGas;
  }

  /**
   * Gets parent beacon block root.
   *
   * @return the parent beacon block root
   */
  @JsonGetter(value = "parentBeaconBlockRoot")
  public String getParentBeaconBlockRoot() {
    return parentBeaconBlockRoot;
  }
}
