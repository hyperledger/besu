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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.api.query.TransactionReceiptWithMetadata;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.evm.log.Log;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.tuweni.bytes.Bytes;

/** The type Transaction receipt result. */
@JsonPropertyOrder({
  "blockHash",
  "blockNumber",
  "contractAddress",
  "cumulativeGasUsed",
  "from",
  "gasUsed",
  "effectiveGasPrice",
  "logs",
  "logsBloom",
  "root",
  "status",
  "to",
  "transactionHash",
  "transactionIndex",
  "revertReason",
  "type",
  "blobGasUsed",
  "blobGasPrice"
})
public abstract class TransactionReceiptResult {

  private final String blockHash;
  private final String blockNumber;
  private final String contractAddress;
  private final String cumulativeGasUsed;
  private final String from;
  private final String gasUsed;
  private final String effectiveGasPrice;
  private final List<TransactionReceiptLogResult> logs;
  private final String logsBloom;
  private final String to;
  private final String transactionHash;
  private final String transactionIndex;
  private final String revertReason;

  /** The Receipt. */
  protected final TransactionReceipt receipt;

  /** The Type. */
  protected final String type;

  private final String blobGasUsed;
  private final String blobGasPrice;

  /**
   * Instantiates a new Transaction receipt result.
   *
   * @param receiptWithMetadata the receipt with metadata
   */
  protected TransactionReceiptResult(final TransactionReceiptWithMetadata receiptWithMetadata) {
    final Transaction txn = receiptWithMetadata.getTransaction();
    this.receipt = receiptWithMetadata.getReceipt();
    this.blockHash = receiptWithMetadata.getBlockHash().toString();
    this.blockNumber = Quantity.create(receiptWithMetadata.getBlockNumber());
    this.contractAddress = txn.contractAddress().map(Address::toString).orElse(null);
    this.cumulativeGasUsed = Quantity.create(receipt.getCumulativeGasUsed());
    this.from = txn.getSender().toString();
    this.gasUsed = Quantity.create(receiptWithMetadata.getGasUsed());
    this.blobGasUsed = receiptWithMetadata.getBlobGasUsed().map(Quantity::create).orElse(null);
    this.blobGasPrice = receiptWithMetadata.getBlobGasPrice().map(Quantity::create).orElse(null);
    this.effectiveGasPrice =
        Quantity.create(txn.getEffectiveGasPrice(receiptWithMetadata.getBaseFee()));

    this.logs =
        logReceipts(
            receipt.getLogsList(),
            receiptWithMetadata.getBlockNumber(),
            txn.getHash(),
            receiptWithMetadata.getBlockHash(),
            receiptWithMetadata.getTransactionIndex(),
            receiptWithMetadata.getLogIndexOffset());
    this.logsBloom = receipt.getBloomFilter().toString();
    this.to = txn.getTo().map(Bytes::toHexString).orElse(null);
    this.transactionHash = txn.getHash().toString();
    this.transactionIndex = Quantity.create(receiptWithMetadata.getTransactionIndex());
    this.revertReason = receipt.getRevertReason().map(Bytes::toString).orElse(null);
    this.type =
        txn.getType().equals(TransactionType.FRONTIER)
            ? Quantity.create(0)
            : Quantity.create(txn.getType().getSerializedType());
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
   * Gets block number.
   *
   * @return the block number
   */
  @JsonGetter(value = "blockNumber")
  public String getBlockNumber() {
    return blockNumber;
  }

  /**
   * Gets contract address.
   *
   * @return the contract address
   */
  @JsonGetter(value = "contractAddress")
  public String getContractAddress() {
    return contractAddress;
  }

  /**
   * Gets cumulative gas used.
   *
   * @return the cumulative gas used
   */
  @JsonGetter(value = "cumulativeGasUsed")
  public String getCumulativeGasUsed() {
    return cumulativeGasUsed;
  }

  /**
   * Gets from.
   *
   * @return the from
   */
  @JsonGetter(value = "from")
  public String getFrom() {
    return from;
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
   * Gets blob gas used.
   *
   * @return the blob gas used
   */
  @JsonGetter(value = "blobGasUsed")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String getBlobGasUsed() {
    return blobGasUsed;
  }

  /**
   * Gets blob gas price.
   *
   * @return the blob gas price
   */
  @JsonGetter(value = "blobGasPrice")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String getBlobGasPrice() {
    return blobGasPrice;
  }

  /**
   * Gets effective gas price.
   *
   * @return the effective gas price
   */
  @JsonGetter(value = "effectiveGasPrice")
  public String getEffectiveGasPrice() {
    return effectiveGasPrice;
  }

  /**
   * Gets logs.
   *
   * @return the logs
   */
  @JsonGetter(value = "logs")
  public List<TransactionReceiptLogResult> getLogs() {
    return logs;
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
   * Gets to.
   *
   * @return the to
   */
  @JsonGetter(value = "to")
  public String getTo() {
    return to;
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
   * Gets type.
   *
   * @return the type
   */
  @JsonGetter(value = "type")
  public String getType() {
    return type;
  }

  /**
   * Gets revert reason.
   *
   * @return the revert reason
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonGetter(value = "revertReason")
  public String getRevertReason() {
    return revertReason;
  }

  private List<TransactionReceiptLogResult> logReceipts(
      final List<Log> logs,
      final long blockNumber,
      final Hash transactionHash,
      final Hash blockHash,
      final int transactionIndex,
      final int logIndexOffset) {
    final List<TransactionReceiptLogResult> logResults = new ArrayList<>(logs.size());

    for (int i = 0; i < logs.size(); i++) {
      final Log log = logs.get(i);
      logResults.add(
          new TransactionReceiptLogResult(
              log, blockNumber, transactionHash, blockHash, transactionIndex, i + logIndexOffset));
    }

    return logResults;
  }
}
