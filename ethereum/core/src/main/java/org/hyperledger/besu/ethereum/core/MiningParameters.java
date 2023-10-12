/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import org.apache.tuweni.bytes.Bytes;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(allParameters = true)
@Value.Enclosing
public interface MiningParameters {
  class Dynamic {
    public static final Bytes DEFAULT_EXTRA_DATA = Bytes.EMPTY;
    public static final Wei DEFAULT_MIN_TRANSACTION_GAS_PRICE = Wei.of(1000);
    public static final double DEFAULT_MIN_BLOCK_OCCUPANCY_RATIO = 0.8;

    private final MiningParameters miningParameters;
    private volatile Bytes extraData = DEFAULT_EXTRA_DATA;
    private volatile Wei minTransactionGasPrice = DEFAULT_MIN_TRANSACTION_GAS_PRICE;
    private volatile double minBlockOccupancyRatio = DEFAULT_MIN_BLOCK_OCCUPANCY_RATIO;
    private volatile OptionalLong targetGasLimit = OptionalLong.empty();

    private Dynamic(final MiningParameters miningParameters) {
      this.miningParameters = miningParameters;
    }

    public MiningParameters toParameters() {
      return miningParameters;
    }

    public Bytes getExtraData() {
      return extraData;
    }

    public Dynamic setExtraData(final Bytes extraData) {
      this.extraData = extraData;
      return this;
    }

    public Wei getMinTransactionGasPrice() {
      return minTransactionGasPrice;
    }

    public Dynamic setMinTransactionGasPrice(final Wei minTransactionGasPrice) {
      this.minTransactionGasPrice = minTransactionGasPrice;
      return this;
    }

    public double getMinBlockOccupancyRatio() {
      return minBlockOccupancyRatio;
    }

    public Dynamic setMinBlockOccupancyRatio(final double minBlockOccupancyRatio) {
      this.minBlockOccupancyRatio = minBlockOccupancyRatio;
      return this;
    }

    public OptionalLong getTargetGasLimit() {
      return targetGasLimit;
    }

    public Dynamic setTargetGasLimit(final long targetGasLimit) {
      this.targetGasLimit = OptionalLong.of(targetGasLimit);
      return this;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final Dynamic dynamic = (Dynamic) o;
      return Double.compare(minBlockOccupancyRatio, dynamic.minBlockOccupancyRatio) == 0
          && Objects.equals(extraData, dynamic.extraData)
          && Objects.equals(minTransactionGasPrice, dynamic.minTransactionGasPrice)
          && Objects.equals(targetGasLimit, dynamic.targetGasLimit);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          extraData, minTransactionGasPrice, minBlockOccupancyRatio, targetGasLimit);
    }

    @Override
    public String toString() {
      return "Dynamic{"
          + "extraData="
          + extraData
          + ", minTransactionGasPrice="
          + minTransactionGasPrice
          + ", minBlockOccupancyRatio="
          + minBlockOccupancyRatio
          + ", targetGasLimit="
          + targetGasLimit
          + '}';
    }
  }

  //  Double DEFAULT_MIN_BLOCK_OCCUPANCY_RATIO = 0.8;
  int DEFAULT_REMOTE_SEALERS_LIMIT = 1000;
  long DEFAULT_REMOTE_SEALERS_TTL = Duration.ofMinutes(10).toMinutes();
  long DEFAULT_POW_JOB_TTL = Duration.ofMinutes(5).toMillis();
  int DEFAULT_MAX_OMMERS_DEPTH = 8;
  long DEFAULT_POS_BLOCK_CREATION_MAX_TIME = Duration.ofSeconds(12).toMillis();
  long DEFAULT_POS_BLOCK_CREATION_REPETITION_MIN_DURATION = Duration.ofMillis(500).toMillis();
  //  private final Optional<Address> coinbase;
  //  private final Optional<AtomicLong> targetGasLimit;
  //  private final Wei minTransactionGasPrice;
  //  private final Bytes extraData;
  //  private final boolean miningEnabled;
  //  private final boolean stratumMiningEnabled;
  //  private final String stratumNetworkInterface;
  //  private final int stratumPort;
  //  private final String stratumExtranonce;
  //  private final Optional<Iterable<Long>> maybeNonceGenerator;
  //  private final Double minBlockOccupancyRatio;
  //  private final int remoteSealersLimit;
  //  private final long remoteSealersTimeToLive;
  //  private final long powJobTimeToLive;
  //  private final int maxOmmerDepth;
  //  private final long posBlockCreationMaxTime;
  //  private final long posBlockCreationRepetitionMinDuration;

  //  private MiningParameters(
  //      final Address coinbase,
  //      final Long targetGasLimit,
  //      final Wei minTransactionGasPrice,
  //      final Bytes extraData,
  //      final boolean miningEnabled,
  //      final boolean stratumMiningEnabled,
  //      final String stratumNetworkInterface,
  //      final int stratumPort,
  //      final String stratumExtranonce,
  //      final Optional<Iterable<Long>> maybeNonceGenerator,
  //      final Double minBlockOccupancyRatio,
  //      final int remoteSealersLimit,
  //      final long remoteSealersTimeToLive,
  //      final long powJobTimeToLive,
  //      final int maxOmmerDepth,
  //      final long posBlockCreationMaxTime,
  //      final long posBlockCreationRepetitionMinDuration) {
  //    this.coinbase = Optional.ofNullable(coinbase);
  //    this.targetGasLimit = Optional.ofNullable(targetGasLimit).map(AtomicLong::new);
  //    this.minTransactionGasPrice = minTransactionGasPrice;
  //    this.extraData = extraData;
  //    this.miningEnabled = miningEnabled;
  //    this.stratumMiningEnabled = stratumMiningEnabled;
  //    this.stratumNetworkInterface = stratumNetworkInterface;
  //    this.stratumPort = stratumPort;
  //    this.stratumExtranonce = stratumExtranonce;
  //    this.maybeNonceGenerator = maybeNonceGenerator;
  //    this.minBlockOccupancyRatio = minBlockOccupancyRatio;
  //    this.remoteSealersLimit = remoteSealersLimit;
  //    this.remoteSealersTimeToLive = remoteSealersTimeToLive;
  //    this.powJobTimeToLive = powJobTimeToLive;
  //    this.maxOmmerDepth = maxOmmerDepth;
  //    this.posBlockCreationMaxTime = posBlockCreationMaxTime;
  //    this.posBlockCreationRepetitionMinDuration = posBlockCreationRepetitionMinDuration;
  //  }

  @Value.Default
  default Dynamic getDynamic() {
    return new Dynamic(this);
  }

  Optional<Address> getCoinbase();
  //
  //  Optional<Long> getTargetGasLimit();
  //
  //  @Value.Default
  //  default Wei getMinTransactionGasPrice() {
  //    return Wei.ZERO;
  //  }

  //  @Value.Default
  //  default Bytes getExtraData() {
  //    return DEFAULT_EXTRA_DATA;
  //  }

  @Value.Default
  default boolean isMiningEnabled() {
    return false;
  }

  @Value.Default
  default boolean isStratumMiningEnabled() {
    return false;
  }

  @Value.Default
  default String getStratumNetworkInterface() {
    return "0.0.0.0";
  }

  @Value.Default
  default int getStratumPort() {
    return 8008;
  }

  @Value.Default
  default String getStratumExtranonce() {
    return "080c";
  }
  //
  //  @Value.Default
  //  default Double getMinBlockOccupancyRatio() {
  //    return DEFAULT_MIN_BLOCK_OCCUPANCY_RATIO;
  //  }

  @Value.Default
  default int getRemoteSealersLimit() {
    return DEFAULT_REMOTE_SEALERS_LIMIT;
  }

  @Value.Default
  default long getRemoteSealersTimeToLive() {
    return DEFAULT_REMOTE_SEALERS_TTL;
  }

  @Value.Default
  default long getPowJobTimeToLive() {
    return DEFAULT_POW_JOB_TTL;
  }

  @Value.Default
  default int getMaxOmmerDepth() {
    return DEFAULT_MAX_OMMERS_DEPTH;
  }

  @Value.Default
  default long getPosBlockCreationMaxTime() {
    return DEFAULT_POS_BLOCK_CREATION_MAX_TIME;
  }

  @Value.Default
  default long getPosBlockCreationRepetitionMinDuration() {
    return DEFAULT_POS_BLOCK_CREATION_REPETITION_MIN_DURATION;
  }

  Optional<Iterable<Long>> nonceGenerator();

  //
  //  @Override
  //  public boolean equals(final Object o) {
  //    if (this == o) return true;
  //    if (o == null || getClass() != o.getClass()) return false;
  //    MiningParameters that = (MiningParameters) o;
  //    return stratumPort == that.stratumPort
  //        && Objects.equals(coinbase, that.coinbase)
  //        && Objects.equals(targetGasLimit, that.targetGasLimit)
  //        && Objects.equals(minTransactionGasPrice, that.minTransactionGasPrice)
  //        && Objects.equals(extraData, that.extraData)
  //        && miningEnabled == that.miningEnabled
  //        && stratumMiningEnabled == that.stratumMiningEnabled
  //        && Objects.equals(stratumNetworkInterface, that.stratumNetworkInterface)
  //        && Objects.equals(stratumExtranonce, that.stratumExtranonce)
  //        && Objects.equals(minBlockOccupancyRatio, that.minBlockOccupancyRatio)
  //        && remoteSealersTimeToLive == that.remoteSealersTimeToLive
  //        && remoteSealersLimit == that.remoteSealersLimit
  //        && powJobTimeToLive == that.powJobTimeToLive
  //        && posBlockCreationMaxTime == that.posBlockCreationMaxTime
  //        && posBlockCreationRepetitionMinDuration == that.posBlockCreationRepetitionMinDuration;
  //  }
  //
  //  @Override
  //  public int hashCode() {
  //    return Objects.hash(
  //        coinbase,
  //        targetGasLimit,
  //        minTransactionGasPrice,
  //        extraData,
  //        miningEnabled,
  //        stratumMiningEnabled,
  //        stratumNetworkInterface,
  //        stratumPort,
  //        stratumExtranonce,
  //        minBlockOccupancyRatio,
  //        remoteSealersLimit,
  //        remoteSealersTimeToLive,
  //        powJobTimeToLive,
  //        posBlockCreationMaxTime,
  //        posBlockCreationRepetitionMinDuration);
  //  }
  //
  //  @Override
  //  public String toString() {
  //    return "MiningParameters{"
  //        + "coinbase="
  //        + coinbase
  //        + ", targetGasLimit="
  //        + targetGasLimit.map(Object::toString).orElse("null")
  //        + ", minTransactionGasPrice="
  //        + minTransactionGasPrice
  //        + ", extraData="
  //        + extraData
  //        + ", miningEnabled="
  //        + miningEnabled
  //        + ", stratumMiningEnabled="
  //        + stratumMiningEnabled
  //        + ", stratumNetworkInterface='"
  //        + stratumNetworkInterface
  //        + '\''
  //        + ", stratumPort="
  //        + stratumPort
  //        + ", stratumExtranonce='"
  //        + stratumExtranonce
  //        + '\''
  //        + ", maybeNonceGenerator="
  //        + maybeNonceGenerator
  //        + ", minBlockOccupancyRatio="
  //        + minBlockOccupancyRatio
  //        + ", remoteSealersLimit="
  //        + remoteSealersLimit
  //        + ", remoteSealersTimeToLive="
  //        + remoteSealersTimeToLive
  //        + ", powJobTimeToLive="
  //        + powJobTimeToLive
  //        + ", posBlockCreationMaxTime="
  //        + posBlockCreationMaxTime
  //        + ", posBlockCreationRepetitionMinDuration="
  //        + posBlockCreationRepetitionMinDuration
  //        + '}';
  //  }

  //  public static class Builder {
  //
  //    private Address coinbase = null;
  //    private Long targetGasLimit = null;
  //    private Wei minTransactionGasPrice = Wei.ZERO;
  //    private Bytes extraData = Bytes.EMPTY;
  //    private boolean miningEnabled = false;
  //    private boolean stratumMiningEnabled = false;
  //    private String stratumNetworkInterface = "0.0.0.0";
  //    private int stratumPort = 8008;
  //    private String stratumExtranonce = "080c";
  //    private Iterable<Long> maybeNonceGenerator;
  //    private Double minBlockOccupancyRatio = 0.8;
  //    private int remoteSealersLimit = DEFAULT_REMOTE_SEALERS_LIMIT;
  //    private long remoteSealersTimeToLive = DEFAULT_REMOTE_SEALERS_TTL;
  //    private long powJobTimeToLive = DEFAULT_POW_JOB_TTL;
  //    private int maxOmmerDepth = DEFAULT_MAX_OMMERS_DEPTH;
  //    private long posBlockCreationMaxTime = DEFAULT_POS_BLOCK_CREATION_MAX_TIME;
  //
  //    private long posBlockCreationRepetitionMinDuration =
  //        DEFAULT_POS_BLOCK_CREATION_REPETITION_MIN_DURATION;
  //
  //    public Builder() {
  //      // zero arg
  //    }
  //
  //    public Builder(final MiningParameters existing) {
  //      existing.getCoinbase().ifPresent(cb -> this.coinbase = cb);
  //      existing
  //          .getTargetGasLimit()
  //          .map(AtomicLong::longValue)
  //          .ifPresent(gasLimit -> this.targetGasLimit = gasLimit);
  //      this.minTransactionGasPrice = existing.getMinTransactionGasPrice();
  //      this.extraData = existing.getExtraData();
  //      this.miningEnabled = existing.isMiningEnabled();
  //      this.stratumMiningEnabled = existing.isStratumMiningEnabled();
  //      this.stratumNetworkInterface = existing.getStratumNetworkInterface();
  //      this.stratumPort = existing.getStratumPort();
  //      this.stratumExtranonce = existing.getStratumExtranonce();
  //      existing.getNonceGenerator().ifPresent(ng -> this.maybeNonceGenerator = ng);
  //      this.minBlockOccupancyRatio = existing.getMinBlockOccupancyRatio();
  //      this.remoteSealersLimit = existing.getRemoteSealersLimit();
  //      this.remoteSealersTimeToLive = existing.getRemoteSealersTimeToLive();
  //      this.powJobTimeToLive = existing.getPowJobTimeToLive();
  //      this.maxOmmerDepth = existing.getMaxOmmerDepth();
  //      this.posBlockCreationMaxTime = existing.getPosBlockCreationMaxTime();
  //      this.posBlockCreationRepetitionMinDuration =
  //          existing.getPosBlockCreationRepetitionMinDuration();
  //    }
  //
  //    public Builder coinbase(final Address address) {
  //      this.coinbase = address;
  //      return this;
  //    }
  //
  //    public Builder targetGasLimit(final Long targetGasLimit) {
  //      this.targetGasLimit = targetGasLimit;
  //      return this;
  //    }
  //
  //    public Builder minTransactionGasPrice(final Wei minTransactionGasPrice) {
  //      this.minTransactionGasPrice = minTransactionGasPrice;
  //      return this;
  //    }
  //
  //    public Builder extraData(final Bytes extraData) {
  //      this.extraData = extraData;
  //      return this;
  //    }
  //
  //    public Builder miningEnabled(final boolean miningEnabled) {
  //      this.miningEnabled = miningEnabled;
  //      return this;
  //    }
  //
  //    public Builder stratumMiningEnabled(final boolean stratumMiningEnabled) {
  //      this.stratumMiningEnabled = stratumMiningEnabled;
  //      return this;
  //    }
  //
  //    public Builder stratumNetworkInterface(final String stratumNetworkInterface) {
  //      this.stratumNetworkInterface = stratumNetworkInterface;
  //      return this;
  //    }
  //
  //    public Builder stratumPort(final int stratumPort) {
  //      this.stratumPort = stratumPort;
  //      return this;
  //    }
  //
  //    public Builder stratumExtranonce(final String stratumExtranonce) {
  //      this.stratumExtranonce = stratumExtranonce;
  //      return this;
  //    }
  //
  //    public Builder maybeNonceGenerator(final Iterable<Long> maybeNonceGenerator) {
  //      this.maybeNonceGenerator = maybeNonceGenerator;
  //      return this;
  //    }
  //
  //    public Builder minBlockOccupancyRatio(final Double minBlockOccupancyRatio) {
  //      this.minBlockOccupancyRatio = minBlockOccupancyRatio;
  //      return this;
  //    }
  //
  //    public Builder remoteSealersLimit(final int remoteSealersLimit) {
  //      this.remoteSealersLimit = remoteSealersLimit;
  //      return this;
  //    }
  //
  //    public Builder remoteSealersTimeToLive(final long remoteSealersTimeToLive) {
  //      this.remoteSealersTimeToLive = remoteSealersTimeToLive;
  //      return this;
  //    }
  //
  //    public Builder powJobTimeToLive(final long powJobTimeToLive) {
  //      this.powJobTimeToLive = powJobTimeToLive;
  //      return this;
  //    }
  //
  //    public Builder maxOmmerDepth(final int maxOmmerDepth) {
  //      this.maxOmmerDepth = maxOmmerDepth;
  //      return this;
  //    }
  //
  //    public Builder posBlockCreationMaxTime(final long posBlockCreationMaxTime) {
  //      this.posBlockCreationMaxTime = posBlockCreationMaxTime;
  //      return this;
  //    }
  //
  //    public Builder posBlockCreationRepetitionMinDuration(
  //        final long posBlockCreationRepetitionMinDuration) {
  //      this.posBlockCreationRepetitionMinDuration = posBlockCreationRepetitionMinDuration;
  //      return this;
  //    }
  //
  //    public MiningParameters build() {
  //      return new MiningParameters(
  //          coinbase,
  //          targetGasLimit,
  //          minTransactionGasPrice,
  //          extraData,
  //          miningEnabled,
  //          stratumMiningEnabled,
  //          stratumNetworkInterface,
  //          stratumPort,
  //          stratumExtranonce,
  //          Optional.ofNullable(maybeNonceGenerator),
  //          minBlockOccupancyRatio,
  //          remoteSealersLimit,
  //          remoteSealersTimeToLive,
  //          powJobTimeToLive,
  //          maxOmmerDepth,
  //          posBlockCreationMaxTime,
  //          posBlockCreationRepetitionMinDuration);
  //    }
  //  }
}
