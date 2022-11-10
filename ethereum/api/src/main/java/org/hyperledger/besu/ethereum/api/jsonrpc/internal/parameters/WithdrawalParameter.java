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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Withdrawal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.json.JsonObject;

public class WithdrawalParameter {

  private final String index;
  private final String validatorIndex;
  private final String address;
  private final String amount;

  @JsonCreator
  public WithdrawalParameter(
      @JsonProperty("index") final String index,
      @JsonProperty("validatorIndex") final String validatorIndex,
      @JsonProperty("address") final String address,
      @JsonProperty("amount") final String amount) {
    this.index = index;
    this.validatorIndex = validatorIndex;
    this.address = address;
    this.amount = amount;
  }

  public Withdrawal toWithdrawal() {
    return new Withdrawal(
        Long.decode(index),
        Long.decode(validatorIndex),
        Address.fromHexString(address),
        Wei.fromHexString(amount));
  }

  public static WithdrawalParameter fromWithdrawal(final Withdrawal withdrawal) {
    return new WithdrawalParameter(
        Long.toHexString(withdrawal.getIndex()),
        Long.toHexString(withdrawal.getValidatorIndex()),
        withdrawal.getAddress().toHexString(),
        withdrawal.getAmount().toHexString());
  }

  public String serialize() {
    return new JsonObject()
        .put("index", index)
        .put("validatorIndex", validatorIndex)
        .put("address", address)
        .put("amount", amount)
        .encode();
  }

  @JsonGetter
  public String getIndex() {
    return index;
  }

  @JsonGetter
  public String getValidatorIndex() {
    return validatorIndex;
  }

  @JsonGetter
  public String getAddress() {
    return address;
  }

  @JsonGetter
  public String getAmount() {
    return amount;
  }
}
