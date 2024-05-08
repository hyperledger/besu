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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionReceiptLogResult;
import org.hyperledger.besu.ethereum.privacy.PrivacyGroupUtil;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.tuweni.bytes.Bytes;

/** The type Private transaction receipt result. */
@JsonPropertyOrder({
  "contractAddress",
  "from",
  "to",
  "output",
  "commitmentHash",
  "transactionHash",
  "privateFrom",
  "privateFor",
  "privacyGroupId",
  "status",
  "revertReason",
  "logs",
  "logsBloom",
  "blockHash",
  "blockNumber",
  "transactionIndex"
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrivateTransactionReceiptResult {

  private final String blockHash;
  private final String blockNumber;
  private final String contractAddress;
  private final String from;
  private final List<TransactionReceiptLogResult> logs;
  private final String to;
  private final String transactionIndex;
  private final String revertReason;
  private final String output;
  private final String commitmentHash;
  private final String status;
  private final String privateFrom;
  private final List<String> privateFor;
  private final String privacyGroupId;
  private final String logsBloom;

  /**
   * Instantiates a new Private transaction receipt result.
   *
   * @param contractAddress the contract address
   * @param from the from
   * @param to the to
   * @param logs the logs
   * @param output the output
   * @param blockHash the block hash
   * @param blockNumber the block number
   * @param txIndex the tx index
   * @param commitmentHash the commitment hash
   * @param privateFrom the private from
   * @param privateFor the private for
   * @param privacyGroupId the privacy group id
   * @param revertReason the revert reason
   * @param status the status
   */
  public PrivateTransactionReceiptResult(
      final String contractAddress,
      final String from,
      final String to,
      final List<Log> logs,
      final Bytes output,
      final Hash blockHash,
      final long blockNumber,
      final int txIndex,
      final Hash commitmentHash,
      final Bytes privateFrom,
      final List<Bytes> privateFor,
      final Bytes privacyGroupId,
      final Bytes revertReason,
      final String status) {
    this.contractAddress = contractAddress;
    this.from = from;
    this.to = to;
    this.output = output.toString();
    this.commitmentHash = commitmentHash.toString();
    this.privateFrom = privateFrom != null ? privateFrom.toBase64String() : null;
    this.privateFor =
        privateFor != null
            ? privateFor.stream().map(Bytes::toBase64String).collect(Collectors.toList())
            : null;
    this.privacyGroupId =
        privacyGroupId != null
            ? privacyGroupId.toBase64String()
            : PrivacyGroupUtil.calculateEeaPrivacyGroupId(privateFrom, privateFor).toBase64String();
    this.revertReason = revertReason != null ? revertReason.toString() : null;
    this.status = status;
    this.logs = logReceipts(logs, blockNumber, commitmentHash, blockHash, txIndex);
    this.logsBloom = LogsBloomFilter.builder().insertLogs(logs).build().toHexString();
    this.blockHash = blockHash.toHexString();
    this.blockNumber = Quantity.create(blockNumber);
    this.transactionIndex = Quantity.create(txIndex);
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
   * Gets from.
   *
   * @return the from
   */
  @JsonGetter(value = "from")
  public String getFrom() {
    return from;
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
   * Gets output.
   *
   * @return the output
   */
  @JsonGetter(value = "output")
  public String getOutput() {
    return output;
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
   * Gets commitment hash.
   *
   * @return the commitment hash
   */
  @JsonGetter("commitmentHash")
  public String getCommitmentHash() {
    return commitmentHash;
  }

  /**
   * Gets private from.
   *
   * @return the private from
   */
  @JsonGetter("privateFrom")
  public String getPrivateFrom() {
    return privateFrom;
  }

  /**
   * Gets private for.
   *
   * @return the private for
   */
  @JsonGetter("privateFor")
  public List<String> getPrivateFor() {
    return privateFor;
  }

  /**
   * Gets privacy group id.
   *
   * @return the privacy group id
   */
  @JsonGetter("privacyGroupId")
  public String getPrivacyGroupId() {
    return privacyGroupId;
  }

  /**
   * Gets revert reason.
   *
   * @return the revert reason
   */
  @JsonGetter("revertReason")
  public String getRevertReason() {
    return revertReason;
  }

  /**
   * Gets status.
   *
   * @return the status
   */
  @JsonGetter("status")
  public String getStatus() {
    return status;
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
   * Gets block number.
   *
   * @return the block number
   */
  @JsonGetter(value = "blockNumber")
  public String getBlockNumber() {
    return blockNumber;
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
