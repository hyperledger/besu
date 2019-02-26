/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.privacy;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Log;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.TransactionReceiptLogResult;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
  "contractAddress",
  "from",
  "to",
})
public class PrivateTransactionReceiptResult {

  private final String contractAddress;
  private final String from;
  private final String to;

  public PrivateTransactionReceiptResult(
      final String contractAddress, final String from, final String to) {

    this.contractAddress = contractAddress;
    this.from = from;
    // TODO: handle logs as in TransactionReceiptResult
    this.to = to;
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
