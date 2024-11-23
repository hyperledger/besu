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

import java.math.BigInteger;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BlockOverrides {
  private final Optional<Long> timestamp;
  private final Optional<Long> blockNumber;
  private final Optional<BigInteger> prevRandao;
  private final Optional<Long> gasLimit;
  private final Optional<Address> feeRecipient;
  private final Optional<Wei> baseFeePerGas;
  private final Optional<Long> blobBaseFee;

  @JsonCreator
  public BlockOverrides(
      @JsonProperty("timestamp") final Optional<Long> timestamp,
      @JsonProperty("blockNumber") final Optional<Long> blockNumber,
      @JsonProperty("prevRandao") final Optional<BigInteger> prevRandao,
      @JsonProperty("gasLimit") final Optional<Long> gasLimit,
      @JsonProperty("feeRecipient") final Optional<Address> feeRecipient,
      @JsonProperty("baseFeePerGas") final Optional<Wei> baseFeePerGas,
      @JsonProperty("blobBaseFee") final Optional<Long> blobBaseFee) {
    this.timestamp = timestamp;
    this.blockNumber = blockNumber;
    this.prevRandao = prevRandao;
    this.gasLimit = gasLimit;
    this.feeRecipient = feeRecipient;
    this.baseFeePerGas = baseFeePerGas;
    this.blobBaseFee = blobBaseFee;
  }

  private BlockOverrides(final Builder builder) {
    this.blockNumber = Optional.ofNullable(builder.blockNumber);
    this.prevRandao = Optional.ofNullable(builder.prevRandao);
    this.timestamp = Optional.ofNullable(builder.timestamp);
    this.gasLimit = Optional.ofNullable(builder.gasLimit);
    this.feeRecipient = Optional.ofNullable(builder.feeRecipient);
    this.baseFeePerGas = Optional.ofNullable(builder.baseFeePerGas);
    this.blobBaseFee = Optional.ofNullable(builder.blobBaseFee);
  }

  public Optional<Long> getBlockNumber() {
    return blockNumber;
  }

  public Optional<BigInteger> getPrevRandao() {
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

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private Long timestamp;
    private Long blockNumber;
    private BigInteger prevRandao;
    private Long gasLimit;
    private Address feeRecipient;
    private Wei baseFeePerGas;
    private Long blobBaseFee;

    public Builder timestamp(final Long timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder blockNumber(final Long blockNumber) {
      this.blockNumber = blockNumber;
      return this;
    }

    public Builder prevRandao(final BigInteger prevRandao) {
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

    public BlockOverrides build() {
      return new BlockOverrides(this);
    }
  }
}
