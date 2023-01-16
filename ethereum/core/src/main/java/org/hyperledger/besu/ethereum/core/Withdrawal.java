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

import java.util.Objects;

import org.apache.tuweni.units.bigints.UInt64;

public class Withdrawal {
  private final UInt64 index;
  private final UInt64 validatorIndex;
  private final Address address;
  private final Wei amount;

  public Withdrawal(
      final UInt64 index, final UInt64 validatorIndex, final Address address, final Wei amount) {
    this.index = index;
    this.validatorIndex = validatorIndex;
    this.address = address;
    this.amount = amount;
  }

  public UInt64 getIndex() {
    return index;
  }

  public UInt64 getValidatorIndex() {
    return validatorIndex;
  }

  public Address getAddress() {
    return address;
  }

  public Wei getAmount() {
    return amount;
  }

  @Override
  public String toString() {
    return "Withdrawal{"
        + "index="
        + index
        + ", validatorIndex="
        + validatorIndex
        + ", address="
        + address
        + ", amount="
        + amount
        + '}';
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final Withdrawal that = (Withdrawal) o;
    return Objects.equals(index, that.index)
        && Objects.equals(validatorIndex, that.validatorIndex)
        && Objects.equals(address, that.address)
        && Objects.equals(amount, that.amount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, validatorIndex, address, amount);
  }
}
