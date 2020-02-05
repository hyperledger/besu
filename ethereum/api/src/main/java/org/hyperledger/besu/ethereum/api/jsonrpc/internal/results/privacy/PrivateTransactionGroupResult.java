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

import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
  "s",
  "privateFrom",
  "privacyGroupId",
  "restriction"
})
/*
 The original deserialised private transaction sent via eea_sendRawTransaction
 This class is used if the original request was sent with privacyGroupId
*/
public class PrivateTransactionGroupResult extends PrivateTransactionResult {
  private final String privacyGroupId;

  public PrivateTransactionGroupResult(final PrivateTransaction tx) {
    super(tx);
    this.privacyGroupId = tx.getPrivacyGroupId().get().toBase64String();
  }

  @JsonCreator
  public PrivateTransactionGroupResult(
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
    super(from, gas, gasPrice, hash, input, nonce, to, value, v, r, s, privateFrom, restriction);
    this.privacyGroupId = privacyGroupId;
  }

  @JsonGetter(value = "privacyGroupId")
  public String getPrivacyGroupId() {
    return privacyGroupId;
  }
}
