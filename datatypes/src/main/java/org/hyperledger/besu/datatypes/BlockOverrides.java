/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.datatypes;

import static com.google.common.base.Preconditions.checkArgument;

import java.math.BigInteger;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.checkerframework.checker.signedness.qual.Unsigned;

public class BlockOverrides {
  private final Optional<Long> timestamp;
  private final Optional<Long> blockNumber;
  private final Optional<Bytes32> prevRandao;
  private final Optional<Long> gasLimit;
  private final Optional<Address> feeRecipient;
  private final Optional<Wei> baseFeePerGas;
  private final Optional<Long> blobBaseFee;
  private final Optional<Hash> stateRoot;
  private final Optional<BigInteger> difficulty;
  private final Optional<Bytes> extraData;

  @JsonCreator
  public BlockOverrides(
      @JsonProperty("timestamp") final Optional<UnsignedLongParameter> timestamp,
      @JsonProperty("number") final Optional<UnsignedLongParameter> blockNumber,
      @JsonProperty("prevRandao") final Optional<Bytes32> prevRandao,
      @JsonProperty("gasLimit") final Optional<UnsignedLongParameter> gasLimit,
      @JsonProperty("feeRecipient") final Optional<Address> feeRecipient,
      @JsonProperty("baseFeePerGas") final Optional<Wei> baseFeePerGas,
      @JsonProperty("blobBaseFee") final Optional<UnsignedLongParameter> blobBaseFee,
      @JsonProperty("stateRoot") final Optional<Hash> stateRoot,
      @JsonProperty("difficult") final Optional<BigInteger> difficulty,
      @JsonProperty("extraData") final Optional<Bytes> extraData) {
    this.timestamp = timestamp.map(UnsignedLongParameter::getValue);
    this.blockNumber = blockNumber.map(UnsignedLongParameter::getValue);
    this.prevRandao = prevRandao;
    this.gasLimit = gasLimit.map(UnsignedLongParameter::getValue);
    this.feeRecipient = feeRecipient;
    this.baseFeePerGas = baseFeePerGas;
    this.blobBaseFee = blobBaseFee.map(UnsignedLongParameter::getValue);
    this.stateRoot = stateRoot;
    this.difficulty = difficulty;
    this.extraData = extraData;
  }

  private BlockOverrides(final Builder builder) {
    this.blockNumber = Optional.ofNullable(builder.blockNumber);
    this.prevRandao = Optional.ofNullable(builder.prevRandao);
    this.timestamp = Optional.ofNullable(builder.timestamp);
    this.gasLimit = Optional.ofNullable(builder.gasLimit);
    this.feeRecipient = Optional.ofNullable(builder.feeRecipient);
    this.baseFeePerGas = Optional.ofNullable(builder.baseFeePerGas);
    this.blobBaseFee = Optional.ofNullable(builder.blobBaseFee);
    this.stateRoot = Optional.ofNullable(builder.stateRoot);
    this.difficulty = Optional.ofNullable(builder.difficulty);
    this.extraData = Optional.ofNullable(builder.extraData);
  }

  public Optional<Long> getBlockNumber() {
    return blockNumber;
  }

  public Optional<Bytes32> getPrevRandao() {
    return prevRandao;
  }

  public Optional<Long> getTimestamp() {
    return timestamp;
  }

  public Optional<Long> getGasLimit() {
    return gasLimit;
  }

  public Optional<Address> getFeeRecipient() {
    return feeRecipient;
  }

  public Optional<Wei> getBaseFeePerGas() {
    return baseFeePerGas;
  }

  public Optional<Long> getBlobBaseFee() {
    return blobBaseFee;
  }

  public Optional<Hash> getStateRoot() {
    return stateRoot;
  }

  public Optional<BigInteger> getDifficulty() {
    return difficulty;
  }

  public Optional<Bytes> getExtraData() {
    return extraData;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private Long timestamp;
    private Long blockNumber;
    private Bytes32 prevRandao;
    private Long gasLimit;
    private Address feeRecipient;
    private Wei baseFeePerGas;
    private Long blobBaseFee;
    private Hash stateRoot;
    private BigInteger difficulty;
    private Bytes extraData;

    public Builder timestamp(final Long timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder blockNumber(final Long blockNumber) {
      this.blockNumber = blockNumber;
      return this;
    }

    public Builder prevRandao(final Bytes32 prevRandao) {
      this.prevRandao = prevRandao;
      return this;
    }

    public Builder gasLimit(final Long gasLimit) {
      this.gasLimit = gasLimit;
      return this;
    }

    public Builder feeRecipient(final Address feeRecipient) {
      this.feeRecipient = feeRecipient;
      return this;
    }

    public Builder baseFeePerGas(final Wei baseFeePerGas) {
      this.baseFeePerGas = baseFeePerGas;
      return this;
    }

    public Builder blobBaseFee(final Long blobBaseFee) {
      this.blobBaseFee = blobBaseFee;
      return this;
    }

    public Builder stateRoot(final Hash stateRoot) {
      this.stateRoot = stateRoot;
      return this;
    }

    public Builder difficulty(final BigInteger difficulty) {
      this.difficulty = difficulty;
      return this;
    }

    public Builder extraData(final Bytes extraData) {
      this.extraData = extraData;
      return this;
    }

    public BlockOverrides build() {
      return new BlockOverrides(this);
    }
  }

  private static class UnsignedLongParameter {

    @Unsigned private final long value;

    @JsonCreator
    public UnsignedLongParameter(final String value) {
      checkArgument(value != null);
      if (value.startsWith("0x")) {
        this.value = Long.parseUnsignedLong(value.substring(2), 16);
      } else {
        this.value = Long.parseUnsignedLong(value, 16);
      }
    }

    @JsonCreator
    public UnsignedLongParameter(final @Unsigned long value) {
      this.value = value;
    }

    public @Unsigned long getValue() {
      return value;
    }
  }
}
