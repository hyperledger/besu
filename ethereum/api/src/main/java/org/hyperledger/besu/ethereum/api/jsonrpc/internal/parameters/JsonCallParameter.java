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
import org.hyperledger.besu.ethereum.core.json.GasDeserializer;
import org.hyperledger.besu.ethereum.core.json.HexStringDeserializer;
import org.hyperledger.besu.ethereum.transaction.CallParameter;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to deserialize JSON parameters for a call to the JSON-RPC method eth_call.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = JsonCallParameter.JsonCallParameterBuilder.class)
public class JsonCallParameter extends CallParameter {

  private static final Logger LOG = LoggerFactory.getLogger(JsonCallParameter.class);

  private final Optional<Boolean> strict;

  private JsonCallParameter(
      final Address from,
      final Address to,
      final Long gasLimit,
      final Wei gasPrice,
      final Optional<Wei> maxPriorityFeePerGas,
      final Optional<Wei> maxFeePerGas,
      final Wei value,
      final Bytes payload,
      final Optional<Boolean> strict,
      final Optional<List<AccessListEntry>> accessList,
      final Optional<Wei> maxFeePerBlobGas,
      final Optional<List<VersionedHash>> blobVersionedHashes) {

    super(
        from,
        to,
        gasLimit,
        gasPrice,
        maxPriorityFeePerGas,
        maxFeePerGas,
        value,
        payload,
        accessList,
        maxFeePerBlobGas,
        blobVersionedHashes);

    this.strict = strict;
  }

  /**
   * Returns whether the call should be executed in strict mode.
   * @return Optional strict mode flag
   */
  public Optional<Boolean> isMaybeStrict() {
    return strict;
  }

  /**
   * Builder for {@link JsonCallParameter}. Used by Jackson to deserialize {@code JsonCallParameter}.
   */
  public static final class JsonCallParameterBuilder {
    private Optional<Boolean> strict = Optional.empty();
    private Address from;
    private Address to;
    private long gasLimit = -1;
    private Optional<Wei> maxPriorityFeePerGas = Optional.empty();
    private Optional<Wei> maxFeePerGas = Optional.empty();
    private Optional<Wei> maxFeePerBlobGas = Optional.empty();
    private Wei gasPrice;
    private Wei value;
    private Bytes data;
    private Bytes input;
    private Optional<List<AccessListEntry>> accessList = Optional.empty();
    private Optional<List<VersionedHash>> blobVersionedHashes = Optional.empty();

    public JsonCallParameterBuilder() {}

    public JsonCallParameterBuilder withStrict(final Boolean strict) {
      this.strict = Optional.ofNullable(strict);
      return this;
    }

    public JsonCallParameterBuilder withFrom(final Address from) {
      this.from = from;
      return this;
    }

    public JsonCallParameterBuilder withTo(final Address to) {
      this.to = to;
      return this;
    }

    @JsonDeserialize(using = GasDeserializer.class)
    @JsonProperty("gas")
    public JsonCallParameterBuilder withGasLimit(final Long gasLimit) {
      this.gasLimit = Optional.ofNullable(gasLimit).orElse(-1L);
      return this;
    }

    public JsonCallParameterBuilder withMaxPriorityFeePerGas(final Wei maxPriorityFeePerGas) {
      this.maxPriorityFeePerGas = Optional.ofNullable(maxPriorityFeePerGas);
      return this;
    }

    public JsonCallParameterBuilder withMaxFeePerGas(final Wei maxFeePerGas) {
      this.maxFeePerGas = Optional.ofNullable(maxFeePerGas);
      return this;
    }

    public JsonCallParameterBuilder withMaxFeePerBlobGas(final Wei maxFeePerBlobGas) {
      this.maxFeePerBlobGas = Optional.ofNullable(maxFeePerBlobGas);
      return this;
    }

    public JsonCallParameterBuilder withGasPrice(final Wei gasPrice) {
      this.gasPrice = gasPrice;
      return this;
    }

    public JsonCallParameterBuilder withValue(final Wei value) {
      this.value = value;
      return this;
    }

    @JsonDeserialize(using = HexStringDeserializer.class)
    public JsonCallParameterBuilder withData(final Bytes data) {
      this.data = data;
      return this;
    }

    @JsonDeserialize(using = HexStringDeserializer.class)
    public JsonCallParameterBuilder withInput(final Bytes input) {
      this.input = input;
      return this;
    }

    public JsonCallParameterBuilder withAccessList(final List<AccessListEntry> accessList) {
      this.accessList = Optional.ofNullable(accessList);
      return this;
    }

    public JsonCallParameterBuilder withBlobVersionedHashes(
        final List<VersionedHash> blobVersionedHashes) {
      this.blobVersionedHashes = Optional.ofNullable(blobVersionedHashes);
      return this;
    }

    @JsonAnySetter
    public void withUnknownProperties(final String key, final Object value) {
      LOG.debug(
          "unknown property - {} with value - {} and type - {} caught during serialization",
          key,
          value,
          value != null ? value.getClass() : "NULL");
    }

    public JsonCallParameter build() {
      if (input != null && data != null && !input.equals(data)) {
        throw new IllegalArgumentException("Only one of 'input' or 'data' should be provided");
      }

      final Bytes payload = input != null ? input : data;

      return new JsonCallParameter(
          from,
          to,
          gasLimit,
          gasPrice,
          maxPriorityFeePerGas,
          maxFeePerGas,
          value,
          payload,
          strict,
          accessList,
          maxFeePerBlobGas,
          blobVersionedHashes);
    }
  }
}
