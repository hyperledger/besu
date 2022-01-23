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
package org.hyperledger.besu.tests.acceptance.dsl.privacy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PrivateTransactionGroupResponse {
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
  private final String privacyGroupId;

  @JsonCreator
  public PrivateTransactionGroupResponse(
      @JsonProperty("from") final String from,
      @JsonProperty("gas") final String gas,
      @JsonProperty("gasPrice") final String gasPrice,
      @JsonProperty("hash") final String hash,
      @JsonProperty("input") final String input,
      @JsonProperty("nonce") final String nonce,
      @JsonProperty("to") final String to,
      @JsonProperty("value") final String value,
      @JsonProperty("v") final String v,
      @JsonProperty("r") final String r,
      @JsonProperty("s") final String s,
      @JsonProperty("privateFrom") final String privateFrom,
      @JsonProperty("restriction") final String restriction,
      @JsonProperty("privacyGroupId") final String privacyGroupId) {
    this.from = from;
    this.gas = gas;
    this.gasPrice = gasPrice;
    this.input = input;
    this.nonce = nonce;
    this.to = to;
    this.value = value;
    this.v = v;
    this.r = r;
    this.s = s;
    this.privateFrom = privateFrom;
    this.restriction = restriction;
    this.privacyGroupId = privacyGroupId;
  }

  public String getFrom() {
    return from;
  }

  public String getGas() {
    return gas;
  }

  public String getGasPrice() {
    return gasPrice;
  }

  public String getInput() {
    return input;
  }

  public String getNonce() {
    return nonce;
  }

  public String getTo() {
    return to;
  }

  public String getValue() {
    return value;
  }

  public String getV() {
    return v;
  }

  public String getR() {
    return r;
  }

  public String getS() {
    return s;
  }

  public String getPrivateFrom() {
    return privateFrom;
  }

  public String getRestriction() {
    return restriction;
  }

  public String getPrivacyGroupId() {
    return privacyGroupId;
  }
}
