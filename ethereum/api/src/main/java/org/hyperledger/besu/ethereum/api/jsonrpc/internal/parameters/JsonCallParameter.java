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

import org.hyperledger.besu.datatypes.AccessListEntry;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.json.HexLongDeserializer;
import org.hyperledger.besu.ethereum.core.json.HexStringDeserializer;
import org.hyperledger.besu.ethereum.transaction.CallParameter;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The type Json call parameter. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonCallParameter extends CallParameter {

  private static final Logger LOG = LoggerFactory.getLogger(JsonCallParameter.class);

  private final Optional<Boolean> strict;

  /**
   * Instantiates a new Json call parameter.
   *
   * @param from the from
   * @param to the to
   * @param gasLimit the gas limit
   * @param gasPrice the gas price
   * @param maxPriorityFeePerGas the max priority fee per gas
   * @param maxFeePerGas the max fee per gas
   * @param value the value
   * @param data the data
   * @param input the input
   * @param strict the strict
   * @param accessList the access list
   * @param maxFeePerBlobGas the max fee per blob gas
   * @param blobVersionedHashes the blob versioned hashes
   */
  @JsonCreator
  public JsonCallParameter(
      @JsonProperty("from") final Address from,
      @JsonProperty("to") final Address to,
      @JsonDeserialize(using = HexLongDeserializer.class) @JsonProperty("gas") final Long gasLimit,
      @JsonProperty("gasPrice") final Wei gasPrice,
      @JsonProperty("maxPriorityFeePerGas") final Wei maxPriorityFeePerGas,
      @JsonProperty("maxFeePerGas") final Wei maxFeePerGas,
      @JsonProperty("value") final Wei value,
      @JsonDeserialize(using = HexStringDeserializer.class) @JsonProperty("data") final Bytes data,
      @JsonDeserialize(using = HexStringDeserializer.class) @JsonProperty("input")
          final Bytes input,
      @JsonProperty("strict") final Boolean strict,
      @JsonProperty("accessList") final List<AccessListEntry> accessList,
      @JsonProperty("maxFeePerBlobGas") final Wei maxFeePerBlobGas,
      @JsonProperty("blobVersionedHashes") final List<VersionedHash> blobVersionedHashes) {

    super(
        from,
        to,
        gasLimit != null ? gasLimit : -1L,
        gasPrice,
        Optional.ofNullable(maxPriorityFeePerGas),
        Optional.ofNullable(maxFeePerGas),
        value,
        Optional.ofNullable(input != null ? input : data).orElse(null),
        Optional.ofNullable(accessList),
        Optional.ofNullable(maxFeePerBlobGas),
        Optional.ofNullable(blobVersionedHashes));

    if (input != null && data != null && !input.equals(data)) {
      throw new IllegalArgumentException("Only one of 'input' or 'data' should be provided");
    }

    this.strict = Optional.ofNullable(strict);
  }

  /**
   * Is maybe strict optional.
   *
   * @return the optional
   */
  public Optional<Boolean> isMaybeStrict() {
    return strict;
  }

  /**
   * Log unknown properties.
   *
   * @param key the key
   * @param value the value
   */
  @JsonAnySetter
  public void logUnknownProperties(final String key, final Object value) {
    LOG.debug(
        "unknown property - {} with value - {} and type - {} caught during serialization",
        key,
        value,
        value.getClass());
  }
}
