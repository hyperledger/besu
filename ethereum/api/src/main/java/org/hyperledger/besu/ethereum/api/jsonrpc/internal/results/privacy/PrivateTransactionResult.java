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

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;

import com.fasterxml.jackson.annotation.JsonGetter;

public abstract class PrivateTransactionResult {
  private final String from;
  private final String gas;
  private final String gasPrice;
  private final String input;
  private final String nonce;
  private final String to;
  private final String value;
  private final String v;
  private final String r;
  private final String s;
  private final String privateFrom;
  private final String restriction;

  protected PrivateTransactionResult(final PrivateTransaction tx) {
    this.from = tx.getSender().toString();
    this.gas = Quantity.create(tx.getGasLimit());
    this.gasPrice = Quantity.create(tx.getGasPrice());
    this.input = tx.getPayload().toString();
    this.nonce = Quantity.create(tx.getNonce());
    this.to = tx.getTo().map(Address::toHexString).orElse(null);
    this.value = Quantity.create(tx.getValue());
    this.v = Quantity.create(tx.getV());
    this.r = Quantity.create(tx.getR());
    this.s = Quantity.create(tx.getS());
    this.privateFrom = tx.getPrivateFrom().toBase64String();
    this.restriction = new String(tx.getRestriction().getBytes().toArrayUnsafe(), UTF_8);
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

  @JsonGetter(value = "privateFrom")
  public String getPrivateFrom() {
    return privateFrom;
  }

  @JsonGetter(value = "restriction")
  public String getRestriction() {
    return restriction;
  }
}
