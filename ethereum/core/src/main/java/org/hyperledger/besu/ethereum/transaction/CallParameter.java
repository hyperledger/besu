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
package org.hyperledger.besu.ethereum.transaction;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Wei;

import java.util.Objects;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

// Represents parameters for a eth_call or eth_estimateGas JSON-RPC methods.
public class CallParameter {

  private final Address from;

  private final Address to;

  private final long gasLimit;

  private final Optional<Wei> gasPremium;

  private final Optional<Wei> feeCap;

  private final Wei gasPrice;

  private final Wei value;

  private final Bytes payload;

  public CallParameter(
      final Address from,
      final Address to,
      final long gasLimit,
      final Wei gasPrice,
      final Wei value,
      final Bytes payload) {
    this.from = from;
    this.to = to;
    this.gasLimit = gasLimit;
    this.gasPremium = Optional.empty();
    this.feeCap = Optional.empty();
    this.gasPrice = gasPrice;
    this.value = value;
    this.payload = payload;
  }

  public CallParameter(
      final Address from,
      final Address to,
      final long gasLimit,
      final Wei gasPrice,
      final Optional<Wei> gasPremium,
      final Optional<Wei> feeCap,
      final Wei value,
      final Bytes payload) {
    this.from = from;
    this.to = to;
    this.gasLimit = gasLimit;
    this.gasPremium = gasPremium;
    this.feeCap = feeCap;
    this.gasPrice = gasPrice;
    this.value = value;
    this.payload = payload;
  }

  public Address getFrom() {
    return from;
  }

  public Address getTo() {
    return to;
  }

  public long getGasLimit() {
    return gasLimit;
  }

  public Wei getGasPrice() {
    return gasPrice;
  }

  public Optional<Wei> getGasPremium() {
    return gasPremium;
  }

  public Optional<Wei> getFeeCap() {
    return feeCap;
  }

  public Wei getValue() {
    return value;
  }

  public Bytes getPayload() {
    return payload;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CallParameter that = (CallParameter) o;
    return gasLimit == that.gasLimit
        && Objects.equals(from, that.from)
        && Objects.equals(to, that.to)
        && Objects.equals(gasPrice, that.gasPrice)
        && Objects.equals(gasPremium, that.gasPremium)
        && Objects.equals(feeCap, that.feeCap)
        && Objects.equals(value, that.value)
        && Objects.equals(payload, that.payload);
  }

  @Override
  public int hashCode() {
    return Objects.hash(from, to, gasLimit, gasPrice, gasPremium, feeCap, value, payload);
  }
}
