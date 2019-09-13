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

import tech.pegasys.pantheon.ethereum.api.TransactionWithMetadata;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
  "blockHash",
  "blockNumber",
  "from",
  "gas",
  "gasPrice",
  "hash",
  "input",
  "nonce",
  "to",
  "transactionIndex",
  "value",
  "v",
  "r",
  "s"
})
public class TransactionCompleteResult implements TransactionResult {

  private final String blockHash;
  private final String blockNumber;
  private final String from;
  private final String gas;
  private final String gasPrice;
  private final String hash;
  private final String input;
  private final String nonce;
  private final String to;
  private final String transactionIndex;
  private final String value;
  private final String v;
  private final String r;
  private final String s;

  public TransactionCompleteResult(final TransactionWithMetadata tx) {
    final Transaction transaction = tx.getTransaction();
    this.blockHash = tx.getBlockHash().get().toString();
    this.blockNumber = Quantity.create(tx.getBlockNumber().get());
    this.from = transaction.getSender().toString();
    this.gas = Quantity.create(transaction.getGasLimit());
    this.gasPrice = Quantity.create(transaction.getGasPrice());
    this.hash = transaction.hash().toString();
    this.input = transaction.getPayload().toString();
    this.nonce = Quantity.create(transaction.getNonce());
    this.to = transaction.getTo().map(BytesValue::toString).orElse(null);
    this.transactionIndex = Quantity.create(tx.getTransactionIndex().get());
    this.value = Quantity.create(transaction.getValue());
    this.v = Quantity.create(transaction.getV());
    this.r = Quantity.create(transaction.getR());
    this.s = Quantity.create(transaction.getS());
  }

  @JsonGetter(value = "blockHash")
  public String getBlockHash() {
    return blockHash;
  }

  @JsonGetter(value = "blockNumber")
  public String getBlockNumber() {
    return blockNumber;
  }

  @JsonGetter(value = "from")
  public String getFrom() {
    return from;
  }

  @JsonGetter(value = "gas")
  public String getGas() {
    return gas;
  }

  @JsonGetter(value = "gasPrice")
  public String getGasPrice() {
    return gasPrice;
  }

  @JsonGetter(value = "hash")
  public String getHash() {
    return hash;
  }

  @JsonGetter(value = "input")
  public String getInput() {
    return input;
  }

  @JsonGetter(value = "nonce")
  public String getNonce() {
    return nonce;
  }

  @JsonGetter(value = "to")
  public String getTo() {
    return to;
  }

  @JsonGetter(value = "transactionIndex")
  public String getTransactionIndex() {
    return transactionIndex;
  }

  @JsonGetter(value = "value")
  public String getValue() {
    return value;
  }

  @JsonGetter(value = "v")
  public String getV() {
    return v;
  }

  @JsonGetter(value = "r")
  public String getR() {
    return r;
  }

  @JsonGetter(value = "s")
  public String getS() {
    return s;
  }
}
