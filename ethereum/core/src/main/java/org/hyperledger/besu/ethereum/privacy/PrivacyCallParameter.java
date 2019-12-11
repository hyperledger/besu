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
package org.hyperledger.besu.ethereum.privacy;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

// Represents parameters for a priv_call or eth_estimateGas JSON-RPC methods.
public class PrivacyCallParameter {

  private final Address from;

  private final Address to;

  private final long gasLimit;

  private final Wei gasPrice;

  private final Wei value;

  private final BytesValue payload;

  private final BytesValue privateFrom;

  private final Optional<List<BytesValue>> privateFor;

  private final Optional<BytesValue> privacyGroupId;

  public PrivacyCallParameter(
      final Address from,
      final Address to,
      final long gasLimit,
      final Wei gasPrice,
      final Wei value,
      final BytesValue payload,
      final BytesValue privateFrom) {
    this.from = from;
    this.to = to;
    this.gasLimit = gasLimit;
    this.gasPrice = gasPrice;
    this.value = value;
    this.payload = payload;
    this.privateFrom = privateFrom;
    this.privateFor = Optional.empty();
    this.privacyGroupId = Optional.empty();
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

  public Wei getValue() {
    return value;
  }

  public BytesValue getPayload() {
    return payload;
  }

  public BytesValue getPrivateFrom() {
    return privateFrom;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final PrivacyCallParameter that = (PrivacyCallParameter) o;
    return gasLimit == that.gasLimit &&
            from.equals(that.from) &&
            to.equals(that.to) &&
            gasPrice.equals(that.gasPrice) &&
            value.equals(that.value) &&
            payload.equals(that.payload) &&
            privateFrom.equals(that.privateFrom) &&
            privateFor.equals(that.privateFor) &&
            privacyGroupId.equals(that.privacyGroupId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(from, to, gasLimit, gasPrice, value, payload, privateFrom, privateFor, privacyGroupId);
  }
}
