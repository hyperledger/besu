/*
 * Copyright contributors to Hyperledger Besu.
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
import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.datatypes.PublicKey;
import org.hyperledger.besu.datatypes.RequestType;

import java.util.Objects;

public class WithdrawalRequest extends Request
    implements org.hyperledger.besu.plugin.data.WithdrawalRequest {

  private final Address sourceAddress;
  private final BLSPublicKey validatorPubKey;
  private final GWei amount;

  public WithdrawalRequest(
      final Address sourceAddress, final BLSPublicKey validatorPubKey, final GWei amount) {
    this.sourceAddress = sourceAddress;
    this.validatorPubKey = validatorPubKey;
    this.amount = amount;
  }

  @Override
  public RequestType getType() {
    return RequestType.WITHDRAWAL;
  }

  @Override
  public Address getSourceAddress() {
    return sourceAddress;
  }

  @Override
  public PublicKey getValidatorPubKey() {
    return validatorPubKey;
  }

  @Override
  public GWei getAmount() {
    return amount;
  }

  @Override
  public String toString() {
    return "WithdrawalRequest{"
        + "sourceAddress="
        + sourceAddress
        + " validatorPubKey="
        + validatorPubKey
        + " amount="
        + amount
        + '}';
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final WithdrawalRequest that = (WithdrawalRequest) o;
    return Objects.equals(sourceAddress, that.sourceAddress)
        && Objects.equals(validatorPubKey, that.validatorPubKey)
        && Objects.equals(amount, that.amount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceAddress, validatorPubKey, amount);
  }
}
