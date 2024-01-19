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
import org.hyperledger.besu.util.number.PositiveNumber;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.immutables.value.Value;

@Value.Immutable
@Value.Enclosing
public abstract class MiningParameters {
  public static final PositiveNumber DEFAULT_NON_POA_BLOCK_TXS_SELECTION_MAX_TIME =
      PositiveNumber.fromInt((int) Duration.ofSeconds(5).toMillis());
  public static final PositiveNumber DEFAULT_POA_BLOCK_TXS_SELECTION_MAX_TIME =
      PositiveNumber.fromInt(75);
  public static final MiningParameters MINING_DISABLED =
      ImmutableMiningParameters.builder()
          .mutableInitValues(
              ImmutableMiningParameters.MutableInitValues.builder().isMiningEnabled(false).build())
          .build();

  @VisibleForTesting
  public static final MiningParameters newDefault() {
    return ImmutableMiningParameters.builder().build();
  }

  public boolean isMiningEnabled() {
    return getMutableRuntimeValues().miningEnabled;
  }

  public MiningParameters setMiningEnabled(final boolean miningEnabled) {
    getMutableRuntimeValues().miningEnabled = miningEnabled;
    return this;
  }

  public Bytes getExtraData() {
    return getMutableRuntimeValues().extraData;
  }

  public MiningParameters setExtraData(final Bytes extraData) {
    getMutableRuntimeValues().extraData = extraData;
    return this;
  }

  public Wei getMinTransactionGasPrice() {
    return getMutableRuntimeValues().minTransactionGasPrice;
  }

  public MiningParameters setMinTransactionGasPrice(final Wei minTransactionGasPrice) {
    getMutableRuntimeValues().minTransactionGasPrice = minTransactionGasPrice;
    return this;
  }

  public Wei getMinPriorityFeePerGas() {
    return getMutableRuntimeValues().minPriorityFeePerGas;
  }

  public MiningParameters setMinPriorityFeePerGas(final Wei minPriorityFeePerGas) {
    getMutableRuntimeValues().minPriorityFeePerGas = minPriorityFeePerGas;
    return this;
  }

  public Optional<Address> getCoinbase() {
    return getMutableRuntimeValues().coinbase;
  }

  public MiningParameters setCoinbase(final Address coinbase) {
    getMutableRuntimeValues().coinbase = Optional.of(coinbase);
    return this;
  }

  public OptionalLong getTargetGasLimit() {
    return getMutableRuntimeValues().targetGasLimit;
  }

  public MiningParameters setTargetGasLimit(final long targetGasLimit) {
    getMutableRuntimeValues().targetGasLimit = OptionalLong.of(targetGasLimit);
    return this;
  }

  public double getMinBlockOccupancyRatio() {
    return getMutableRuntimeValues().minBlockOccupancyRatio;
  }

  public MiningParameters setMinBlockOccupancyRatio(final double minBlockOccupancyRatio) {
    getMutableRuntimeValues().minBlockOccupancyRatio = minBlockOccupancyRatio;
    return this;
  }

  public Optional<Iterable<Long>> getNonceGenerator() {
    return getMutableRuntimeValues().nonceGenerator;
  }

  public MiningParameters setNonceGenerator(final Iterable<Long> nonceGenerator) {
    getMutableRuntimeValues().nonceGenerator = Optional.of(nonceGenerator);
    return this;
  }

  @Value.Default
  public boolean isStratumMiningEnabled() {
    return false;
  }

  @Value.Default
  public String getStratumNetworkInterface() {
    return "0.0.0.0";
  }

  @Value.Default
  public int getStratumPort() {
    return 8008;
  }

  @Value.Default
  public PositiveNumber getNonPoaBlockTxsSelectionMaxTime() {
    return DEFAULT_NON_POA_BLOCK_TXS_SELECTION_MAX_TIME;
  }

  @Value.Default
  public PositiveNumber getPoaBlockTxsSelectionMaxTime() {
    return DEFAULT_POA_BLOCK_TXS_SELECTION_MAX_TIME;
  }

  public abstract OptionalInt getGenesisBlockPeriodSeconds();

  @Value.Derived
  public long getBlockTxsSelectionMaxTime() {
    if (getGenesisBlockPeriodSeconds().isPresent()) {
      return (TimeUnit.SECONDS.toMillis(getGenesisBlockPeriodSeconds().getAsInt())
              * getPoaBlockTxsSelectionMaxTime().getValue())
          / 100;
    }
    return getNonPoaBlockTxsSelectionMaxTime().getValue();
  }

  @Value.Default
  protected MutableRuntimeValues getMutableRuntimeValues() {
    return new MutableRuntimeValues(getMutableInitValues());
  }

  @Value.Default
  public Unstable getUnstable() {
    return Unstable.DEFAULT;
  }

  @Value.Default
  public MutableInitValues getMutableInitValues() {
    return MutableInitValues.DEFAULT;
  }

  @Value.Immutable
  public interface MutableInitValues {
    Bytes DEFAULT_EXTRA_DATA = Bytes.EMPTY;
    Wei DEFAULT_MIN_TRANSACTION_GAS_PRICE = Wei.of(1000);
    Wei DEFAULT_MIN_PRIORITY_FEE_PER_GAS = Wei.ZERO;
    double DEFAULT_MIN_BLOCK_OCCUPANCY_RATIO = 0.8;

    MutableInitValues DEFAULT = ImmutableMiningParameters.MutableInitValues.builder().build();

    @Value.Default
    default boolean isMiningEnabled() {
      return false;
    }

    @Value.Default
    default Bytes getExtraData() {
      return DEFAULT_EXTRA_DATA;
    }

    @Value.Default
    default Wei getMinTransactionGasPrice() {
      return DEFAULT_MIN_TRANSACTION_GAS_PRICE;
    }

    @Value.Default
    default Wei getMinPriorityFeePerGas() {
      return DEFAULT_MIN_PRIORITY_FEE_PER_GAS;
    }

    @Value.Default
    default double getMinBlockOccupancyRatio() {
      return DEFAULT_MIN_BLOCK_OCCUPANCY_RATIO;
    }

    Optional<Address> getCoinbase();

    OptionalLong getTargetGasLimit();

    Optional<Iterable<Long>> nonceGenerator();
  }

  static class MutableRuntimeValues {
    private volatile boolean miningEnabled;
    private volatile Bytes extraData;
    private volatile Wei minTransactionGasPrice;
    private volatile Wei minPriorityFeePerGas;
    private volatile double minBlockOccupancyRatio;
    private volatile Optional<Address> coinbase;
    private volatile OptionalLong targetGasLimit;
    private volatile Optional<Iterable<Long>> nonceGenerator;

    private MutableRuntimeValues(final MutableInitValues initValues) {
      miningEnabled = initValues.isMiningEnabled();
      extraData = initValues.getExtraData();
      minTransactionGasPrice = initValues.getMinTransactionGasPrice();
      minPriorityFeePerGas = initValues.getMinPriorityFeePerGas();
      minBlockOccupancyRatio = initValues.getMinBlockOccupancyRatio();
      coinbase = initValues.getCoinbase();
      targetGasLimit = initValues.getTargetGasLimit();
      nonceGenerator = initValues.nonceGenerator();
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final MutableRuntimeValues that = (MutableRuntimeValues) o;
      return miningEnabled == that.miningEnabled
          && Double.compare(minBlockOccupancyRatio, that.minBlockOccupancyRatio) == 0
          && Objects.equals(extraData, that.extraData)
          && Objects.equals(minTransactionGasPrice, that.minTransactionGasPrice)
          && Objects.equals(coinbase, that.coinbase)
          && Objects.equals(minPriorityFeePerGas, that.minPriorityFeePerGas)
          && Objects.equals(targetGasLimit, that.targetGasLimit)
          && Objects.equals(nonceGenerator, that.nonceGenerator);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          miningEnabled,
          extraData,
          minTransactionGasPrice,
          minPriorityFeePerGas,
          minBlockOccupancyRatio,
          coinbase,
          targetGasLimit,
          nonceGenerator);
    }

    @Override
    public String toString() {
      return "UpdatableRuntimeValues{"
          + "miningEnabled="
          + miningEnabled
          + ", extraData="
          + extraData
          + ", minTransactionGasPrice="
          + minTransactionGasPrice
          + ", minPriorityFeePerGas="
          + minPriorityFeePerGas
          + ", minBlockOccupancyRatio="
          + minBlockOccupancyRatio
          + ", coinbase="
          + coinbase
          + ", targetGasLimit="
          + targetGasLimit
          + ", nonceGenerator="
          + nonceGenerator
          + '}';
    }
  }

  @Value.Immutable
  public interface Unstable {
    int DEFAULT_REMOTE_SEALERS_LIMIT = 1000;
    long DEFAULT_REMOTE_SEALERS_TTL = Duration.ofMinutes(10).toMinutes();
    long DEFAULT_POW_JOB_TTL = Duration.ofMinutes(5).toMillis();
    int DEFAULT_MAX_OMMERS_DEPTH = 8;
    long DEFAULT_POS_BLOCK_CREATION_MAX_TIME = Duration.ofSeconds(12).toMillis();
    long DEFAULT_POS_BLOCK_CREATION_REPETITION_MIN_DURATION = Duration.ofMillis(500).toMillis();

    MiningParameters.Unstable DEFAULT = ImmutableMiningParameters.Unstable.builder().build();

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

    @Value.Default
    default String getStratumExtranonce() {
      return "080c";
    }
  }
}
