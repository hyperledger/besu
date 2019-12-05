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

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionReceiptLogResult;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Log;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrivateTransactionReceiptResult {

  private final String contractAddress;
  private final String from;
  private final String to;
  private final String output;
  private final String commitmentHash;
  private final String transactionHash;
  private final String privateFrom;
  private final List<String> privateFor;
  private final String privacyGroupId;
  private final String revertReason;
  private final String status;
  private final List<TransactionReceiptLogResult> logs;

  public PrivateTransactionReceiptResult(
      final String contractAddress,
      final String from,
      final String to,
      final List<Log> logs,
      final BytesValue output,
      final Hash blockHash,
      final long blockNumber,
      final int txIndex,
      final Hash commitmentHash,
      final Hash transactionHash,
      final BytesValue privateFrom,
      final List<BytesValue> privateFor,
      final BytesValue privacyGroupId,
      final BytesValue revertReason,
      final String status) {
    this.contractAddress = contractAddress;
    this.from = from;
    this.to = to;
    this.output = output.toString();
    this.commitmentHash = commitmentHash.toString();
    this.transactionHash = transactionHash.toString();
    this.privateFrom = privateFrom != null ? BytesValues.asBase64String(privateFrom) : null;
    this.privateFor =
        privateFor != null
            ? privateFor.stream().map(BytesValues::asBase64String).collect(Collectors.toList())
            : null;
    this.privacyGroupId =
        privacyGroupId != null ? BytesValues.asBase64String(privacyGroupId) : null;
    this.revertReason = revertReason != null ? revertReason.toString() : null;
    this.status = status;
    this.logs = logReceipts(logs, blockNumber, commitmentHash, blockHash, txIndex);
  }

  @JsonGetter(value = "contractAddress")
  public String getContractAddress() {
    return contractAddress;
  }

  @JsonGetter(value = "from")
  public String getFrom() {
    return from;
  }

  @JsonGetter(value = "to")
  public String getTo() {
    return to;
  }

  @JsonGetter(value = "output")
  public String getOutput() {
    return output;
  }

  @JsonGetter(value = "logs")
  public List<TransactionReceiptLogResult> getLogs() {
    return logs;
  }

  @JsonGetter("commitmentHash")
  public String getCommitmentHash() {
    return commitmentHash;
  }

  @JsonGetter("transactionHash")
  public String getTransactionHash() {
    return transactionHash;
  }

  @JsonGetter("privateFrom")
  public String getPrivateFrom() {
    return privateFrom;
  }

  @JsonGetter("privateFor")
  public List<String> getPrivateFor() {
    return privateFor;
  }

  @JsonGetter("privacyGroupId")
  public String getPrivacyGroupId() {
    return privacyGroupId;
  }

  @JsonGetter("revertReason")
  public String getRevertReason() {
    return revertReason;
  }

  @JsonGetter("status")
  public String getStatus() {
    return status;
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
