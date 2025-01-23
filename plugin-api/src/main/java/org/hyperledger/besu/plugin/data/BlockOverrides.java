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
package org.hyperledger.besu.plugin.data;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.datatypes.parameters.UnsignedLongParameter;

import java.math.BigInteger;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** BlockOverrides represents the block overrides for a block. */
public class BlockOverrides {
  private final Optional<Long> timestamp;
  private final Optional<Long> blockNumber;
  private final Optional<Hash> blockHash;
  private final Optional<Bytes32> prevRandao;
  private final Optional<Long> gasLimit;
  private final Optional<Address> feeRecipient;
  private final Optional<Wei> baseFeePerGas;
  private final Optional<Long> blobBaseFee;
  private final Optional<Hash> stateRoot;
  private final Optional<BigInteger> difficulty;
  private final Optional<Bytes> extraData;
  private final Optional<Hash> mixHashOrPrevRandao;

  /**
   * Constructs a new BlockOverrides instance.
   *
   * @param timestamp the optional timestamp
   * @param blockNumber the optional block number
   * @param blockHash the optional block hash
   * @param prevRandao the optional previous Randao
   * @param gasLimit the optional gas limit
   * @param feeRecipient the optional fee recipient
   * @param baseFeePerGas the optional base fee per gas
   * @param blobBaseFee the optional blob base fee
   * @param stateRoot the optional state root
   * @param difficulty the optional difficulty
   * @param extraData the optional extra data
   * @param mixHashOrPrevRandao the optional mix hash or previous Randao
   */
  public BlockOverrides(
      final Optional<UnsignedLongParameter> timestamp,
      final Optional<UnsignedLongParameter> blockNumber,
      final Optional<Hash> blockHash,
      final Optional<Bytes32> prevRandao,
      final Optional<UnsignedLongParameter> gasLimit,
      final Optional<Address> feeRecipient,
      final Optional<Wei> baseFeePerGas,
      final Optional<UnsignedLongParameter> blobBaseFee,
      final Optional<Hash> stateRoot,
      final Optional<BigInteger> difficulty,
      final Optional<Bytes> extraData,
      final Optional<Hash> mixHashOrPrevRandao) {
    this.timestamp = timestamp.map(UnsignedLongParameter::getValue);
    this.blockNumber = blockNumber.map(UnsignedLongParameter::getValue);
    this.blockHash = blockHash;
    this.prevRandao = prevRandao;
    this.gasLimit = gasLimit.map(UnsignedLongParameter::getValue);
    this.feeRecipient = feeRecipient;
    this.baseFeePerGas = baseFeePerGas;
    this.blobBaseFee = blobBaseFee.map(UnsignedLongParameter::getValue);
    this.stateRoot = stateRoot;
    this.difficulty = difficulty;
    this.extraData = extraData;
    this.mixHashOrPrevRandao = mixHashOrPrevRandao;
  }

  /**
   * Constructs a new BlockOverrides instance from a Builder.
   *
   * @param builder the builder to construct from
   */
  private BlockOverrides(final Builder builder) {
    this.blockNumber = Optional.ofNullable(builder.blockNumber);
    this.blockHash = Optional.ofNullable(builder.blockHash);
    this.prevRandao = Optional.ofNullable(builder.prevRandao);
    this.timestamp = Optional.ofNullable(builder.timestamp);
    this.gasLimit = Optional.ofNullable(builder.gasLimit);
    this.feeRecipient = Optional.ofNullable(builder.feeRecipient);
    this.baseFeePerGas = Optional.ofNullable(builder.baseFeePerGas);
    this.blobBaseFee = Optional.ofNullable(builder.blobBaseFee);
    this.stateRoot = Optional.ofNullable(builder.stateRoot);
    this.difficulty = Optional.ofNullable(builder.difficulty);
    this.extraData = Optional.ofNullable(builder.extraData);
    this.mixHashOrPrevRandao = Optional.ofNullable(builder.mixHashOrPrevRandao);
  }

  /**
   * Gets the block number.
   *
   * @return the optional block number
   */
  public Optional<Long> getBlockNumber() {
    return blockNumber;
  }

  /**
   * Gets the block hash.
   *
   * @return the optional block hash
   */
  public Optional<Hash> getBlockHash() {
    return blockHash;
  }

  /**
   * Gets the previous Randao.
   *
   * @return the optional previous Randao
   */
  public Optional<Bytes32> getPrevRandao() {
    return prevRandao;
  }

  /**
   * Gets the timestamp.
   *
   * @return the optional timestamp
   */
  public Optional<Long> getTimestamp() {
    return timestamp;
  }

  /**
   * Gets the gas limit.
   *
   * @return the optional gas limit
   */
  public Optional<Long> getGasLimit() {
    return gasLimit;
  }

  /**
   * Gets the fee recipient.
   *
   * @return the optional fee recipient
   */
  public Optional<Address> getFeeRecipient() {
    return feeRecipient;
  }

  /**
   * Gets the base fee per gas.
   *
   * @return the optional base fee per gas
   */
  public Optional<Wei> getBaseFeePerGas() {
    return baseFeePerGas;
  }

  /**
   * Gets the blob base fee.
   *
   * @return the optional blob base fee
   */
  public Optional<Long> getBlobBaseFee() {
    return blobBaseFee;
  }

  /**
   * Gets the state root.
   *
   * @return the optional state root
   */
  public Optional<Hash> getStateRoot() {
    return stateRoot;
  }

  /**
   * Gets the difficulty.
   *
   * @return the optional difficulty
   */
  public Optional<BigInteger> getDifficulty() {
    return difficulty;
  }

  /**
   * Gets the extra data.
   *
   * @return the optional extra data
   */
  public Optional<Bytes> getExtraData() {
    return extraData;
  }

  /**
   * Gets the mix hash or previous Randao.
   *
   * @return the optional mix hash or previous Randao
   */
  public Optional<Hash> getMixHashOrPrevRandao() {
    return mixHashOrPrevRandao;
  }

  /**
   * Creates a new Builder instance.
   *
   * @return a new Builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for BlockOverrides. */
  public static class Builder {
    private Long timestamp;
    private Long blockNumber;
    private Hash blockHash;
    private Bytes32 prevRandao;
    private Long gasLimit;
    private Address feeRecipient;
    private Wei baseFeePerGas;
    private Long blobBaseFee;
    private Hash stateRoot;
    private BigInteger difficulty;
    private Bytes extraData;
    private Hash mixHashOrPrevRandao;

    /** Constructs a new Builder instance. */
    public Builder() {}

    /**
     * Sets the timestamp.
     *
     * @param timestamp the timestamp to set
     * @return the builder instance
     */
    public Builder timestamp(final Long timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    /**
     * Sets the block number.
     *
     * @param blockNumber the block number to set
     * @return the builder instance
     */
    public Builder blockNumber(final Long blockNumber) {
      this.blockNumber = blockNumber;
      return this;
    }

    /**
     * Sets the block hash.
     *
     * @param blockHash the block hash to set
     * @return the builder instance
     */
    public Builder blockHash(final Hash blockHash) {
      this.blockHash = blockHash;
      return this;
    }

    /**
     * Sets the previous Randao.
     *
     * @param prevRandao the previous Randao to set
     * @return the builder instance
     */
    public Builder prevRandao(final Bytes32 prevRandao) {
      this.prevRandao = prevRandao;
      return this;
    }

    /**
     * Sets the gas limit.
     *
     * @param gasLimit the gas limit to set
     * @return the builder instance
     */
    public Builder gasLimit(final Long gasLimit) {
      this.gasLimit = gasLimit;
      return this;
    }

    /**
     * Sets the fee recipient.
     *
     * @param feeRecipient the fee recipient to set
     * @return the builder instance
     */
    public Builder feeRecipient(final Address feeRecipient) {
      this.feeRecipient = feeRecipient;
      return this;
    }

    /**
     * Sets the base fee per gas.
     *
     * @param baseFeePerGas the base fee per gas to set
     * @return the builder instance
     */
    public Builder baseFeePerGas(final Wei baseFeePerGas) {
      this.baseFeePerGas = baseFeePerGas;
      return this;
    }

    /**
     * Sets the blob base fee.
     *
     * @param blobBaseFee the blob base fee to set
     * @return the builder instance
     */
    public Builder blobBaseFee(final Long blobBaseFee) {
      this.blobBaseFee = blobBaseFee;
      return this;
    }

    /**
     * Sets the state root.
     *
     * @param stateRoot the state root to set
     * @return the builder instance
     */
    public Builder stateRoot(final Hash stateRoot) {
      this.stateRoot = stateRoot;
      return this;
    }

    /**
     * Sets the difficulty.
     *
     * @param difficulty the difficulty to set
     * @return the builder instance
     */
    public Builder difficulty(final BigInteger difficulty) {
      this.difficulty = difficulty;
      return this;
    }

    /**
     * Sets the extra data.
     *
     * @param extraData the extra data to set
     * @return the builder instance
     */
    public Builder extraData(final Bytes extraData) {
      this.extraData = extraData;
      return this;
    }

    /**
     * Sets the mix hash or previous Randao.
     *
     * @param mixHashOrPrevRandao the mix hash or previous Randao to set
     * @return the builder instance
     */
    public Builder mixHashOrPrevRandao(final Hash mixHashOrPrevRandao) {
      this.mixHashOrPrevRandao = mixHashOrPrevRandao;
      return this;
    }

    /**
     * Builds a new BlockOverrides instance.
     *
     * @return the new BlockOverrides instance
     */
    public BlockOverrides build() {
      return new BlockOverrides(this);
    }
  }
}
