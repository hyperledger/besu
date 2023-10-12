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

  Optional<Address> getCoinbase();

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

  Optional<Iterable<Long>> nonceGenerator();

  @Value.Default
  default Dynamic getDynamic() {
    return new Dynamic(this);
  }

  @Value.Default
  default Unstable getUnstable() {
    return Unstable.DEFAULT;
  }

  @Value.Immutable
  interface Unstable {
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
}
