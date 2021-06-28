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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.json.GasDeserializer;
import org.hyperledger.besu.ethereum.core.json.HexStringDeserializer;
import org.hyperledger.besu.ethereum.transaction.CallParameter;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.tuweni.bytes.Bytes;

@JsonIgnoreProperties({"nonce", "privateFor", "timestamp"})
public class JsonCallParameter extends CallParameter {

  private final Optional<Boolean> strict;

  @JsonCreator
  public JsonCallParameter(
      @JsonProperty("from") final Address from,
      @JsonProperty("to") final Address to,
      @JsonDeserialize(using = GasDeserializer.class) @JsonProperty("gas") final Gas gasLimit,
      @JsonProperty("gasPrice") final Wei gasPrice,
      @JsonProperty("maxPriorityFeePerGas") final Wei maxPriorityFeePerGas,
      @JsonProperty("maxFeePerGas") final Wei maxFeePerGas,
      @JsonProperty("value") final Wei value,
      @JsonDeserialize(using = HexStringDeserializer.class) @JsonProperty("data")
          final Bytes payload,
      @JsonProperty("strict") final Boolean strict) {
    super(
        from,
        to,
        gasLimit != null ? gasLimit.toLong() : -1,
        gasPrice,
        Optional.ofNullable(maxPriorityFeePerGas),
        Optional.ofNullable(maxFeePerGas),
        value,
        payload);
    this.strict = Optional.ofNullable(strict);
  }

  public Optional<Boolean> isMaybeStrict() {
    return strict;
  }
}
