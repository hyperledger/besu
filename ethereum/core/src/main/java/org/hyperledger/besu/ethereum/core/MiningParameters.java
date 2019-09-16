/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Objects;
import java.util.Optional;

import com.google.common.base.MoreObjects;

public class MiningParameters {

  private final Optional<Address> coinbase;
  private final Wei minTransactionGasPrice;
  private final BytesValue extraData;
  private final Boolean enabled;

  public MiningParameters(
      final Address coinbase,
      final Wei minTransactionGasPrice,
      final BytesValue extraData,
      final Boolean enabled) {
    this.coinbase = Optional.ofNullable(coinbase);
    this.minTransactionGasPrice = minTransactionGasPrice;
    this.extraData = extraData;
    this.enabled = enabled;
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

  public Boolean isMiningEnabled() {
    return enabled;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final MiningParameters that = (MiningParameters) o;
    return Objects.equals(coinbase, that.coinbase)
        && Objects.equals(minTransactionGasPrice, that.minTransactionGasPrice)
        && Objects.equals(extraData, that.extraData)
        && Objects.equals(enabled, that.enabled);
  }

  @Override
  public int hashCode() {
    return Objects.hash(coinbase, minTransactionGasPrice, extraData, enabled);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("coinbase", coinbase)
        .add("minTransactionGasPrice", minTransactionGasPrice)
        .add("extraData", extraData)
        .add("enabled", enabled)
        .toString();
  }
}
