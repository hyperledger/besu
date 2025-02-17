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
import org.hyperledger.besu.datatypes.parameters.UnsignedLongParameter;
import org.hyperledger.besu.ethereum.core.json.ChainIdDeserializer;
import org.hyperledger.besu.ethereum.core.json.GasDeserializer;
import org.hyperledger.besu.ethereum.core.json.HexStringDeserializer;
import org.hyperledger.besu.ethereum.transaction.CallParameter;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to deserialize JSON parameters for a call to the JSON-RPC method eth_call. It
 * extends {@link CallParameter} by adding support for JSON-specific fields such as strict mode,
 * access lists, and blob versioned hashes. It also handles unknown JSON properties gracefully.
 *
 * <p>To build an instance of this class, use the {@link JsonCallParameterBuilder}:
 *
 * <pre>{@code
 * JsonCallParameter param = new JsonCallParameter.JsonCallParameterBuilder()
 *     .withChainId(Optional.of(BigInteger.ONE))
 *     .withFrom(Address.fromHexString("0x..."))
 *     .withTo(Address.fromHexString("0x..."))
 *     .withGas(21000L)
 *     .withGasPrice(Wei.of(1000000000L))
 *     .withValue(Wei.ZERO)
 *     .withInput(Bytes.fromHexString("0x..."))
 *     .withStrict(true) // Optional
 *     .withAccessList(accessList) // Optional
 *     .withMaxFeePerGas(Wei.of(2)) // Optional
 *     .withMaxPriorityFeePerGas(Wei.of(1)) // Optional
 *     .withMaxFeePerBlobGas(Wei.of(3)) // Optional
 *     .withBlobVersionedHashes(blobVersionedHashes) // Optional
 *     .withNonce(new UnsignedLongParameter(1L)) // Optional
 *     .build();
 * }</pre>
 *
 * <p>Note: Only one of 'data' or 'input' should be provided to the builder. If both are provided
 * and their values differ, an {@link IllegalArgumentException} is thrown.
 *
 * <p>Unknown JSON properties encountered during deserialization are logged but do not affect the
 * deserialization process, allowing for flexibility in JSON formats.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = JsonCallParameter.JsonCallParameterBuilder.class)
public class JsonCallParameter extends CallParameter {

  private static final Logger LOG = LoggerFactory.getLogger(JsonCallParameter.class);

  private final Optional<Boolean> strict;

  private JsonCallParameter(
      final Optional<BigInteger> chainId,
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
      final Optional<List<VersionedHash>> blobVersionedHashes,
      final Optional<Long> nonce) {

    super(
        chainId,
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
        blobVersionedHashes,
        nonce);

    this.strict = strict;
  }

  /**
   * Returns whether the call should be executed in strict mode.
   *
   * @return Optional strict mode flag
   */
  public Optional<Boolean> isMaybeStrict() {
    return strict;
  }

  /**
   * Builder for {@link JsonCallParameter}. Used by Jackson to deserialize {@code
   * JsonCallParameter}.
   */
  public static final class JsonCallParameterBuilder {
    private Optional<Boolean> strict = Optional.empty();
    private Optional<BigInteger> chainId = Optional.empty();
    private Address from;
    private Address to;
    private long gas = -1;
    private Optional<Wei> maxPriorityFeePerGas = Optional.empty();
    private Optional<Wei> maxFeePerGas = Optional.empty();
    private Optional<Wei> maxFeePerBlobGas = Optional.empty();
    private Wei gasPrice;
    private Wei value;
    private Bytes data;
    private Bytes input;
    private Optional<List<AccessListEntry>> accessList = Optional.empty();
    private Optional<List<VersionedHash>> blobVersionedHashes = Optional.empty();
    private Optional<Long> nonce = Optional.empty();

    /** Default constructor. */
    public JsonCallParameterBuilder() {}

    /**
     * Sets the strict mode for the {@link JsonCallParameter}. If strict mode is enabled, the call
     * will be executed with stricter validation rules. This is optional and defaults to not being
     * in strict mode if not specified.
     *
     * @param strict the strict mode flag, can be {@code null} to indicate the absence of a strict
     *     mode preference
     * @return the {@link JsonCallParameterBuilder} instance for chaining
     */
    public JsonCallParameterBuilder withStrict(final Boolean strict) {
      this.strict = Optional.ofNullable(strict);
      return this;
    }

    /**
     * Sets the optional "chainId" for the {@link JsonCallParameter}.
     *
     * @param chainId the chainId
     * @return the {@link JsonCallParameterBuilder} instance for chaining
     */
    @JsonDeserialize(using = ChainIdDeserializer.class)
    public JsonCallParameterBuilder withChainId(final BigInteger chainId) {
      this.chainId = Optional.of(chainId);
      return this;
    }

    /**
     * Sets the "from" address for the {@link JsonCallParameter}. This address represents the sender
     * of the call.
     *
     * @param from the sender's address
     * @return the {@link JsonCallParameterBuilder} instance for chaining
     */
    public JsonCallParameterBuilder withFrom(final Address from) {
      this.from = from;
      return this;
    }

    /**
     * Sets the "to" address for the {@link JsonCallParameter}. This address represents the
     * recipient of the call. It can be null for contract creation transactions.
     *
     * @param to the recipient's address
     * @return the {@link JsonCallParameterBuilder} instance for chaining
     */
    public JsonCallParameterBuilder withTo(final Address to) {
      this.to = to;
      return this;
    }

    /**
     * Sets the gas for the {@link JsonCallParameter} used for transaction execution. eth_call uses
     * 0 gas but this parameter may be needed by some executions. By default, if not specified, the
     * gas is set to -1, indicating that it is not set.
     *
     * @param gas the gas limit for the call, can be {@code null} to indicate that the gas limit is
     *     not set
     * @return the {@link JsonCallParameterBuilder} instance for chaining
     */
    @JsonDeserialize(using = GasDeserializer.class)
    public JsonCallParameterBuilder withGas(final Long gas) {
      this.gas = Optional.ofNullable(gas).orElse(-1L);
      return this;
    }

    /**
     * Sets the maximum priority fee per gas for the {@link JsonCallParameter}. This fee is used to
     * incentivize miners to include the transaction in a block. It is an optional parameter, and if
     * not specified, it defaults to an empty {@link Optional}.
     *
     * @param maxPriorityFeePerGas the maximum priority fee per gas, can be {@code null} to indicate
     *     no preference
     * @return the {@link JsonCallParameterBuilder} instance for chaining
     */
    public JsonCallParameterBuilder withMaxPriorityFeePerGas(final Wei maxPriorityFeePerGas) {
      this.maxPriorityFeePerGas = Optional.ofNullable(maxPriorityFeePerGas);
      return this;
    }

    /**
     * Sets the maximum fee per gas for the {@link JsonCallParameter}. This fee represents the
     * maximum amount of gas that the sender is willing to pay. It is an optional parameter, and if
     * not specified, it defaults to an empty {@link Optional}.
     *
     * @param maxFeePerGas the maximum fee per gas, can be {@code null} to indicate no preference
     * @return the {@link JsonCallParameterBuilder} instance for chaining
     */
    public JsonCallParameterBuilder withMaxFeePerGas(final Wei maxFeePerGas) {
      this.maxFeePerGas = Optional.ofNullable(maxFeePerGas);
      return this;
    }

    /**
     * Sets the maximum fee per blob gas for the {@link JsonCallParameter}. This fee is specific to
     * certain types of transactions and is an optional parameter. If not specified, it defaults to
     * an empty {@link Optional}.
     *
     * @param maxFeePerBlobGas the maximum fee per blob gas, can be {@code null} to indicate no
     *     preference
     * @return the {@link JsonCallParameterBuilder} instance for chaining
     */
    public JsonCallParameterBuilder withMaxFeePerBlobGas(final Wei maxFeePerBlobGas) {
      this.maxFeePerBlobGas = Optional.ofNullable(maxFeePerBlobGas);
      return this;
    }

    /**
     * Sets the gas price for the {@link JsonCallParameter}. The gas price is used to calculate the
     * transaction fee as the product of gas price and gas used. It is an optional parameter, and if
     * not specified, it defaults to the network's current gas price.
     *
     * @param gasPrice the gas price, can be {@code null} to indicate that the network's current gas
     *     price should be used
     * @return the {@link JsonCallParameterBuilder} instance for chaining
     */
    public JsonCallParameterBuilder withGasPrice(final Wei gasPrice) {
      this.gasPrice = gasPrice;
      return this;
    }

    /**
     * Sets the value to be transferred with the call for the {@link JsonCallParameter}. This value
     * is the amount of Wei to be transferred from the sender to the recipient. It is an optional
     * parameter, and if not specified, it defaults to 0, indicating that no value is transferred.
     *
     * @param value the value to be transferred, can be {@code null} to indicate no value is
     *     transferred
     * @return the {@link JsonCallParameterBuilder} instance for chaining
     */
    public JsonCallParameterBuilder withValue(final Wei value) {
      this.value = value;
      return this;
    }

    /**
     * Sets the data for the {@link JsonCallParameter}. This data represents the payload of the
     * call. Note: Only one of 'data' or 'input' should be provided. If both are provided and their
     * values differ, an exception will be thrown during the build process.
     *
     * @param data the payload data
     * @return the {@link JsonCallParameterBuilder} instance for chaining
     */
    @JsonDeserialize(using = HexStringDeserializer.class)
    public JsonCallParameterBuilder withData(final Bytes data) {
      this.data = data;
      return this;
    }

    /**
     * Sets the input for the {@link JsonCallParameter}. This input is an alternative representation
     * of the payload for the call. Note: Only one of 'input' or 'data' should be provided. If both
     * are provided and their values differ, an exception will be thrown during the build process.
     *
     * @param input the payload input
     * @return the {@link JsonCallParameterBuilder} instance for chaining
     */
    @JsonDeserialize(using = HexStringDeserializer.class)
    public JsonCallParameterBuilder withInput(final Bytes input) {
      this.input = input;
      return this;
    }

    /**
     * Sets the access list for the {@link JsonCallParameter}. The access list is a list of
     * addresses and storage keys that the transaction plans to access. This is an optional
     * parameter, and if not specified, it defaults to an empty {@link Optional}.
     *
     * @param accessList the access list, can be {@code null} to indicate no access list is provided
     * @return the {@link JsonCallParameterBuilder} instance for chaining
     */
    public JsonCallParameterBuilder withAccessList(final List<AccessListEntry> accessList) {
      this.accessList = Optional.ofNullable(accessList);
      return this;
    }

    /**
     * Sets the blob versioned hashes for the {@link JsonCallParameter}. This is a list of versioned
     * hashes related to blob transactions, allowing for more complex transaction types. It is an
     * optional parameter, and if not specified, it defaults to an empty {@link Optional}.
     *
     * @param blobVersionedHashes the list of versioned hashes, can be {@code null} to indicate no
     *     versioned hashes are provided
     * @return the {@link JsonCallParameterBuilder} instance for chaining
     */
    public JsonCallParameterBuilder withBlobVersionedHashes(
        final List<VersionedHash> blobVersionedHashes) {
      this.blobVersionedHashes = Optional.ofNullable(blobVersionedHashes);
      return this;
    }

    /**
     * Sets the nonce for the {@link JsonCallParameter}. It is an optional parameter, and if not
     * specified, it defaults to an empty {@link Optional}.
     *
     * @param nonce the nonce, can be {@code null} to indicate no nonce is provided
     * @return the {@link JsonCallParameterBuilder} instance for chaining
     */
    public JsonCallParameterBuilder withNonce(final UnsignedLongParameter nonce) {
      this.nonce = Optional.ofNullable(nonce).map(UnsignedLongParameter::getValue);
      return this;
    }

    /**
     * Handles unknown JSON properties during deserialization. This method is invoked when an
     * unknown property is encountered in the JSON being deserialized into a {@link
     * JsonCallParameter} object. It logs the unknown property's key, value, and the value's type if
     * the value is not null. This allows for flexibility in JSON formats and aids in debugging
     * issues related to unexpected properties.
     *
     * @param key the key of the unknown property
     * @param value the value of the unknown property, which can be any type
     */
    @JsonAnySetter
    public void withUnknownProperties(final String key, final Object value) {
      LOG.debug(
          "unknown property - {} with value - {} and type - {} caught during serialization",
          key,
          value,
          value != null ? value.getClass() : "NULL");
    }

    /**
     * Builds a {@link JsonCallParameter} instance based on the provided parameters. This method
     * also validates that only one of 'input' or 'data' is provided. If both are provided and their
     * values differ, an {@link IllegalArgumentException} is thrown. This ensures the integrity of
     * the payload data for the call.
     *
     * @return a new {@link JsonCallParameter} instance with the specified configuration
     * @throws IllegalArgumentException if both 'input' and 'data' are provided and their values are
     *     not equal
     */
    public JsonCallParameter build() {
      if (input != null && data != null && !input.equals(data)) {
        throw new IllegalArgumentException("Only one of 'input' or 'data' should be provided");
      }

      final Bytes payload = input != null ? input : data;

      return new JsonCallParameter(
          chainId,
          from,
          to,
          gas,
          gasPrice,
          maxPriorityFeePerGas,
          maxFeePerGas,
          value,
          payload,
          strict,
          accessList,
          maxFeePerBlobGas,
          blobVersionedHashes,
          nonce);
    }
  }
}
