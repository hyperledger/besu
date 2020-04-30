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
package org.hyperledger.besu.ethereum.core;

import java.util.Objects;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class MiningParameters {

  private final Optional<Address> coinbase;
  private final Wei minTransactionGasPrice;
  private final Bytes extraData;
  private final boolean enabled;
  private final boolean stratumMiningEnabled;
  private final String stratumNetworkInterface;
  private final int stratumPort;
  private final String stratumExtranonce;
  private final Optional<Iterable<Long>> maybeNonceGenerator;
  private final Double minBlockOccupancyRatio;

  public MiningParameters(
      final Address coinbase,
      final Wei minTransactionGasPrice,
      final Bytes extraData,
      final boolean enabled) {
    this(
        coinbase,
        minTransactionGasPrice,
        extraData,
        enabled,
        false,
        "0.0.0.0",
        8008,
        "080c",
        Optional.empty(),
        0.8);
  }

  public MiningParameters(
      final Address coinbase,
      final Wei minTransactionGasPrice,
      final Bytes extraData,
      final boolean enabled,
      final boolean stratumMiningEnabled,
      final String stratumNetworkInterface,
      final int stratumPort,
      final String stratumExtranonce,
      final Optional<Iterable<Long>> maybeNonceGenerator,
      final Double minBlockOccupancyRatio) {
    this.coinbase = Optional.ofNullable(coinbase);
    this.minTransactionGasPrice = minTransactionGasPrice;
    this.extraData = extraData;
    this.enabled = enabled;
    this.stratumMiningEnabled = stratumMiningEnabled;
    this.stratumNetworkInterface = stratumNetworkInterface;
    this.stratumPort = stratumPort;
    this.stratumExtranonce = stratumExtranonce;
    this.maybeNonceGenerator = maybeNonceGenerator;
    this.minBlockOccupancyRatio = minBlockOccupancyRatio;
  }

  public Optional<Address> getCoinbase() {
    return coinbase;
  }

  public Wei getMinTransactionGasPrice() {
    return minTransactionGasPrice;
  }

  public Bytes getExtraData() {
    return extraData;
  }

  public boolean isMiningEnabled() {
    return enabled;
  }

  public boolean isStratumMiningEnabled() {
    return stratumMiningEnabled;
  }

  public String getStratumNetworkInterface() {
    return stratumNetworkInterface;
  }

  public int getStratumPort() {
    return stratumPort;
  }

  public String getStratumExtranonce() {
    return stratumExtranonce;
  }

  public Optional<Iterable<Long>> getNonceGenerator() {
    return maybeNonceGenerator;
  }

  public Double getMinBlockOccupancyRatio() {
    return minBlockOccupancyRatio;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MiningParameters that = (MiningParameters) o;
    return stratumPort == that.stratumPort
        && Objects.equals(coinbase, that.coinbase)
        && Objects.equals(minTransactionGasPrice, that.minTransactionGasPrice)
        && Objects.equals(extraData, that.extraData)
        && Objects.equals(enabled, that.enabled)
        && Objects.equals(stratumMiningEnabled, that.stratumMiningEnabled)
        && Objects.equals(stratumNetworkInterface, that.stratumNetworkInterface)
        && Objects.equals(stratumExtranonce, that.stratumExtranonce)
        && Objects.equals(minBlockOccupancyRatio, that.minBlockOccupancyRatio);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        coinbase,
        minTransactionGasPrice,
        extraData,
        enabled,
        stratumMiningEnabled,
        stratumNetworkInterface,
        stratumPort,
        stratumExtranonce,
        minBlockOccupancyRatio);
  }

  @Override
  public String toString() {
    return "MiningParameters{"
        + "coinbase="
        + coinbase
        + ", minTransactionGasPrice="
        + minTransactionGasPrice
        + ", extraData="
        + extraData
        + ", enabled="
        + enabled
        + ", stratumMiningEnabled="
        + stratumMiningEnabled
        + ", stratumNetworkInterface='"
        + stratumNetworkInterface
        + '\''
        + ", stratumPort="
        + stratumPort
        + ", stratumExtranonce='"
        + stratumExtranonce
        + '\''
        + '}';
  }
}
