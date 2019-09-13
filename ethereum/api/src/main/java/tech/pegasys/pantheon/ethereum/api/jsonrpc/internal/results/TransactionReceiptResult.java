/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.results;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.queries.TransactionReceiptWithMetadata;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Log;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
  "blockHash",
  "blockNumber",
  "contractAddress",
  "cumulativeGasUsed",
  "from",
  "gasUsed",
  "logs",
  "logsBloom",
  "root",
  "status",
  "to",
  "transactionHash",
  "transactionIndex",
  "revertReason"
})
public abstract class TransactionReceiptResult {

  private final String blockHash;
  private final String blockNumber;
  private final String contractAddress;
  private final String cumulativeGasUsed;
  private final String from;
  private final String gasUsed;
  private final List<TransactionReceiptLogResult> logs;
  private final String logsBloom;
  private final String to;
  private final String transactionHash;
  private final String transactionIndex;
  private final String revertReason;

  protected final TransactionReceipt receipt;

  public TransactionReceiptResult(final TransactionReceiptWithMetadata receiptWithMetadata) {

    receipt = receiptWithMetadata.getReceipt();

    this.blockHash = receiptWithMetadata.getBlockHash().toString();
    this.blockNumber = Quantity.create(receiptWithMetadata.getBlockNumber());
    this.contractAddress =
        receiptWithMetadata.getTransaction().contractAddress().map(Address::toString).orElse(null);
    this.cumulativeGasUsed = Quantity.create(receipt.getCumulativeGasUsed());
    this.from = receiptWithMetadata.getTransaction().getSender().toString();
    this.gasUsed = Quantity.create(receiptWithMetadata.getGasUsed());
    this.logs =
        logReceipts(
            receipt.getLogs(),
            receiptWithMetadata.getBlockNumber(),
            receiptWithMetadata.getTransaction().hash(),
            receiptWithMetadata.getBlockHash(),
            receiptWithMetadata.getTransactionIndex());
    this.logsBloom = receipt.getBloomFilter().toString();
    this.to = receiptWithMetadata.getTransaction().getTo().map(BytesValue::toString).orElse(null);
    this.transactionHash = receiptWithMetadata.getTransaction().hash().toString();
    this.transactionIndex = Quantity.create(receiptWithMetadata.getTransactionIndex());
    this.revertReason = receipt.getRevertReason().map(BytesValue::toString).orElse(null);
  }

  @JsonGetter(value = "blockHash")
  public String getBlockHash() {
    return blockHash;
  }

  @JsonGetter(value = "blockNumber")
  public String getBlockNumber() {
    return blockNumber;
  }

  @JsonGetter(value = "contractAddress")
  public String getContractAddress() {
    return contractAddress;
  }

  @JsonGetter(value = "cumulativeGasUsed")
  public String getCumulativeGasUsed() {
    return cumulativeGasUsed;
  }

  @JsonGetter(value = "from")
  public String getFrom() {
    return from;
  }

  @JsonGetter(value = "gasUsed")
  public String getGasUsed() {
    return gasUsed;
  }

  @JsonGetter(value = "logs")
  public List<TransactionReceiptLogResult> getLogs() {
    return logs;
  }

  @JsonGetter(value = "logsBloom")
  public String getLogsBloom() {
    return logsBloom;
  }

  @JsonGetter(value = "to")
  public String getTo() {
    return to;
  }

  @JsonGetter(value = "transactionHash")
  public String getTransactionHash() {
    return transactionHash;
  }

  @JsonGetter(value = "transactionIndex")
  public String getTransactionIndex() {
    return transactionIndex;
  }

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
      final int transactionIndex) {
    final List<TransactionReceiptLogResult> logResults = new ArrayList<>(logs.size());

    for (int i = 0; i < logs.size(); i++) {
      final Log log = logs.get(i);
      logResults.add(
          new TransactionReceiptLogResult(
              log, blockNumber, transactionHash, blockHash, transactionIndex, i));
    }

    return logResults;
  }
}
