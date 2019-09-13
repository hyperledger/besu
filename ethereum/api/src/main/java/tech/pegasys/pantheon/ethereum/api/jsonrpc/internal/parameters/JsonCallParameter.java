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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.transaction.CallParameter;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class JsonCallParameter extends CallParameter {
  @JsonCreator
  public JsonCallParameter(
      @JsonProperty("from") final String from,
      @JsonProperty("to") final String to,
      @JsonProperty("gas") final String gasLimit,
      @JsonProperty("gasPrice") final String gasPrice,
      @JsonProperty("value") final String value,
      @JsonProperty("data") final String payload) {
    super(
        from != null ? Address.fromHexString(from) : null,
        to != null ? Address.fromHexString(to) : null,
        gasLimit != null ? Long.decode(gasLimit) : -1,
        gasPrice != null ? Wei.fromHexString(gasPrice) : null,
        value != null ? Wei.fromHexString(value) : null,
        payload != null ? BytesValue.fromHexString(payload) : null);
  }
}
