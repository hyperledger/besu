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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.pending;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.JsonRpcResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.core.Transaction;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.tuweni.bytes.Bytes;

@JsonPropertyOrder({
  "from",
  "gas",
  "gasPrice",
  "hash",
  "input",
  "nonce",
  "to",
  "value",
  "v",
  "r",
  "s"
})
public class PendingTransactionDetailResult implements JsonRpcResult {
  private final String from;
  private final String gas;
  private final String gasPrice;
  private final String hash;
  private final String input;
  private final String nonce;
  private final String to;
  private final String value;
  private final String v;
  private final String r;
  private final String s;

  public PendingTransactionDetailResult(final Transaction tx) {
    this.from = tx.getSender().toString();
    this.gas = Quantity.create(tx.getGasLimit());
    this.gasPrice = tx.getGasPrice().map(Quantity::create).orElse(null);
    this.hash = tx.getHash().toString();
    this.input = tx.getPayload().toString();
    this.nonce = Quantity.create(tx.getNonce());
    this.to = tx.getTo().map(Bytes::toHexString).orElse(null);
    this.value = Quantity.create(tx.getValue());
    this.v = Quantity.create(tx.getV());
    this.r = Quantity.create(tx.getR());
    this.s = Quantity.create(tx.getS());
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
