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
import com.fasterxml.jackson.annotation.JsonProperty;

public class WithdrawalParameter {

  private final String index;
  private final String validatorIndex;
  private final Address address;
  private final String amount;

  @JsonCreator
  public WithdrawalParameter(
      @JsonProperty("index") final String index,
      @JsonProperty("validatorIndex") final String validatorIndex,
      @JsonProperty("address") final Address address,
      @JsonProperty("amount") final String amount) {
    this.index = index;
    this.validatorIndex = validatorIndex;
    this.address = address;
    this.amount = amount;
  }

  public Withdrawal toWithdrawal() {
    return new Withdrawal(
        Long.decode(index), Long.decode(validatorIndex), address, Wei.fromHexString(amount));
  }

  public static WithdrawalParameter fromWithdrawal(final Withdrawal withdrawal) {
    return new WithdrawalParameter(
        Long.toHexString(withdrawal.getIndex()),
        Long.toHexString(withdrawal.getValidatorIndex()),
        withdrawal.getAddress(),
        withdrawal.getAmount().toHexString());
  }
}
