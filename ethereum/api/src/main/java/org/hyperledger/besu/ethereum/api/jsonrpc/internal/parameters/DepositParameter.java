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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.json.JsonObject;
import org.apache.tuweni.units.bigints.UInt64;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.core.Deposit;

import java.util.Objects;

public class DepositParameter {

  private final String pubKey;

  private final String withdrawalCredentials;
  private final String amount;

  private final String signature;
  private final String index;

  @JsonCreator
  public DepositParameter(
      @JsonProperty("pubKey") final String pubKey,
      @JsonProperty("withdrawalCredentials") final String withdrawalCredentials,
      @JsonProperty("amount") final String amount,
      @JsonProperty("signature") final String signature,
      @JsonProperty("index") final String index) {
        this.pubKey = pubKey;
        this.withdrawalCredentials = withdrawalCredentials;
        this.amount = amount;
        this.signature = signature;
        this.index = index;
  }

  public static DepositParameter fromDeposit(final Deposit deposit) {
    return new DepositParameter(
        deposit.getPubKey().toString(), //TODO
        deposit.getWithdrawalCredentials().toString(),
        deposit.getAmount().toShortHexString(),
        deposit.getSignature().toString(),
        deposit.getIndex().toBytes().toQuantityHexString()
    );
  }

  //TODO
  public Deposit toDeposit() {
    return new Deposit(
        pubKey,
        withdrawalCredentials,
        GWei.fromHexString(amount),
        signature,
        UInt64.fromHexString(index));
  }

  public JsonObject asJsonObject() {
    return new JsonObject()
        .put("pubKey", pubKey)
        .put("withdrawalCredentials", withdrawalCredentials)
        .put("amount", amount)
        .put("signature", signature)
        .put("index", index);
  }

  @JsonGetter
  public String getPubKey() {
    return pubKey;
  }

  @JsonGetter
  public String getWithdrawalCredentials() {
    return withdrawalCredentials;
  }

  @JsonGetter
  public String getAmount() {
    return amount;
  }

  @JsonGetter
  public String getSignature() {
    return signature;
  }

  @JsonGetter
  public String getIndex() {
    return index;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final DepositParameter that = (DepositParameter) o;
    return Objects.equals(pubKey, that.pubKey)
        && Objects.equals(withdrawalCredentials, that.withdrawalCredentials)
        && Objects.equals(amount, that.amount)
        && Objects.equals(signature, that.signature)
        && Objects.equals(index, that.index);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pubKey, withdrawalCredentials, amount, signature, index);
  }

  @Override
  public String toString() {
    return "DepositParameter{" +
            "pubKey='" + pubKey + '\'' +
            ", withdrawalCredentials='" + withdrawalCredentials + '\'' +
            ", amount='" + amount + '\'' +
            ", signature='" + signature + '\'' +
            ", index='" + index + '\'' +
            '}';
  }

}
