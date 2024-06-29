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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.ethereum.core.WithdrawalRequest;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

public class WithdrawalRequestParameter {

  private final String sourceAddress;
  private final String validatorPubkey;
  private final String amount;

  @JsonCreator
  public WithdrawalRequestParameter(
      @JsonProperty("sourceAddress") final String sourceAddress,
      @JsonProperty("validatorPubkey") final String validatorPubkey,
      @JsonProperty("amount") final String amount) {
    this.sourceAddress = sourceAddress;
    this.validatorPubkey = validatorPubkey;
    this.amount = amount;
  }

  public static WithdrawalRequestParameter fromWithdrawalRequest(
      final WithdrawalRequest withdrawalRequest) {
    return new WithdrawalRequestParameter(
        withdrawalRequest.getSourceAddress().toHexString(),
        withdrawalRequest.getValidatorPubkey().toHexString(),
        withdrawalRequest.getAmount().toShortHexString());
  }

  public WithdrawalRequest toWithdrawalRequest() {
    return new WithdrawalRequest(
        Address.fromHexString(sourceAddress),
        BLSPublicKey.fromHexString(validatorPubkey),
        GWei.fromHexString(amount));
  }

  @JsonGetter
  public String getSourceAddress() {
    return sourceAddress;
  }

  @JsonGetter
  public String getValidatorPubkey() {
    return validatorPubkey;
  }

  @JsonGetter
  public String getAmount() {
    return amount;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final WithdrawalRequestParameter that = (WithdrawalRequestParameter) o;
    return Objects.equals(sourceAddress, that.sourceAddress)
        && Objects.equals(validatorPubkey, that.validatorPubkey)
        && Objects.equals(amount, that.amount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceAddress, validatorPubkey, amount);
  }

  @Override
  public String toString() {
    return "WithdrawalRequestParameter{"
        + "sourceAddress='"
        + sourceAddress
        + '\''
        + ", validatorPubkey='"
        + validatorPubkey
        + '\''
        + ", amount='"
        + amount
        + '\''
        + '}';
  }
}
