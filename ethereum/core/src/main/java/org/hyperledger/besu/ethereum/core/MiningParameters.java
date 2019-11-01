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

import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Objects;
import java.util.Optional;

public class MiningParameters {

  private final Optional<Address> coinbase;
  private final Wei minTransactionGasPrice;
  private final BytesValue extraData;
  private final boolean enabled;
  private final boolean cpuMiningEnabled;
  private final String stratumNetworkInterface;
  private final int stratumPort;

  public MiningParameters(
      final Address coinbase,
      final Wei minTransactionGasPrice,
      final BytesValue extraData,
      final boolean enabled) {
    this(coinbase, minTransactionGasPrice, extraData, enabled, false, "0.0.0.0", 8008);
  }

  public MiningParameters(
      final Address coinbase,
      final Wei minTransactionGasPrice,
      final BytesValue extraData,
      final boolean enabled,
      final boolean cpuMiningEnabled,
      final String stratumNetworkInterface,
      final int stratumPort) {
    this.coinbase = Optional.ofNullable(coinbase);
    this.minTransactionGasPrice = minTransactionGasPrice;
    this.extraData = extraData;
    this.enabled = enabled;
    this.cpuMiningEnabled = cpuMiningEnabled;
    this.stratumNetworkInterface = stratumNetworkInterface;
    this.stratumPort = stratumPort;
  }

  public Optional<Address> getCoinbase() {
    return coinbase;
  }

  public Wei getMinTransactionGasPrice() {
    return minTransactionGasPrice;
  }

  public BytesValue getExtraData() {
    return extraData;
  }

  public boolean isMiningEnabled() {
    return enabled;
  }

  public boolean isCpuMiningEnabled() {
    return cpuMiningEnabled;
  }

  public String getStratumNetworkInterface() {
    return stratumNetworkInterface;
  }

  public int getStratumPort() {
    return stratumPort;
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
        && Objects.equals(cpuMiningEnabled, that.cpuMiningEnabled)
        && Objects.equals(stratumNetworkInterface, that.stratumNetworkInterface);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        coinbase,
        minTransactionGasPrice,
        extraData,
        enabled,
        cpuMiningEnabled,
        stratumNetworkInterface,
        stratumPort);
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
        + ", cpuMiningEnabled="
        + cpuMiningEnabled
        + ", stratumNetworkInterface='"
        + stratumNetworkInterface
        + '\''
        + ", stratumPort="
        + stratumPort
        + '}';
  }
}
