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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy;

import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonGetter;
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
  "privateFor",
  "restriction"
})
/*
 The original deserialised private transaction sent via eea_sendRawTransaction
 This class is used if the original request was sent with privateFrom and privateFor
*/
public class PrivateTransactionLegacyResult extends PrivateTransactionResult {
  private final List<String> privateFor;

  public PrivateTransactionLegacyResult(final PrivateTransaction tx) {
    super(tx);
    this.privateFor =
        tx.getPrivateFor().get().stream()
            .map(BytesValues::asBase64String)
            .collect(Collectors.toList());
  }

  @JsonGetter(value = "privateFor")
  public List<String> getPrivateFor() {
    return privateFor;
  }
}
