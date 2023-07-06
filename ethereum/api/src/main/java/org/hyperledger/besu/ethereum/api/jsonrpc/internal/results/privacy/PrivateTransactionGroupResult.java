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

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
  "from",
  "gas",
  "gasPrice",
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
 The original deserialized private transaction sent via eea_sendRawTransaction
 This class is used if the original request was sent with privacyGroupId
*/
public class PrivateTransactionGroupResult extends PrivateTransactionResult {
  private final String privacyGroupId;

  public PrivateTransactionGroupResult(final PrivateTransaction tx) {
    super(tx);
    this.privacyGroupId = tx.getPrivacyGroupId().get().toBase64String();
  }

  @JsonGetter(value = "privacyGroupId")
  public String getPrivacyGroupId() {
    return privacyGroupId;
  }
}
