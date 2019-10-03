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

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
  "contractAddress",
  "from",
  "to",
  "output",
  "publicHash",
  "privateHash",
  "privateFrom",
  "privateFor",
  "privacyGroupId",
  "logs",
})
public class PrivateTransactionReceiptResult {

  private final String contractAddress;
  private final String from;
  private final String to;
  private final String output;
  private final Hash publicHash;
  private final Hash privateHash;
  private final BytesValue privateFrom;
  private final List<BytesValue> privateFor;
  private final BytesValue privacyGroupId;
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
      final Hash publicHash,
      final Hash privateHash,
      final BytesValue privateFrom,
      final List<BytesValue> privateFor,
      final BytesValue privacyGroupId) {
    this.contractAddress = contractAddress;
    this.from = from;
    this.to = to;
    this.output = output.toString();
    this.publicHash = publicHash;
    this.privateHash = privateHash;
    this.privateFrom = privateFrom;
    this.privateFor = privateFor;
    this.privacyGroupId = privacyGroupId;
    this.logs = logReceipts(logs, blockNumber, publicHash, blockHash, txIndex);
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

  @JsonGetter("publicHash")
  public Hash getPublicHash() {
    return publicHash;
  }

  @JsonGetter("privateHash")
  public Hash getPrivateHash() {
    return privateHash;
  }

  @JsonGetter("privateFrom")
  public BytesValue getPrivateFrom() {
    return privateFrom;
  }

  @JsonGetter("privateFor")
  public List<BytesValue> getPrivateFor() {
    return privateFor;
  }

  @JsonGetter("privacyGroupId")
  public BytesValue getPrivacyGroupId() {
    return privacyGroupId;
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
