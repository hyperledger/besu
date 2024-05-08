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

import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.datatypes.BLSSignature;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.ethereum.core.Deposit;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.json.JsonObject;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;

/** The type Deposit parameter. */
public class DepositParameter {

  private final String pubkey;

  private final String withdrawalCredentials;
  private final String amount;

  private final String signature;
  private final String index;

  /**
   * Instantiates a new Deposit parameter.
   *
   * @param pubkey the pubkey
   * @param withdrawalCredentials the withdrawal credentials
   * @param amount the amount
   * @param signature the signature
   * @param index the index
   */
  @JsonCreator
  public DepositParameter(
      @JsonProperty("pubkey") final String pubkey,
      @JsonProperty("withdrawalCredentials") final String withdrawalCredentials,
      @JsonProperty("amount") final String amount,
      @JsonProperty("signature") final String signature,
      @JsonProperty("index") final String index) {
    this.pubkey = pubkey;
    this.withdrawalCredentials = withdrawalCredentials;
    this.amount = amount;
    this.signature = signature;
    this.index = index;
  }

  /**
   * From deposit deposit parameter.
   *
   * @param deposit the deposit
   * @return the deposit parameter
   */
  public static DepositParameter fromDeposit(final Deposit deposit) {
    return new DepositParameter(
        deposit.getPubkey().toString(),
        deposit.getWithdrawalCredentials().toString(),
        deposit.getAmount().toShortHexString(),
        deposit.getSignature().toString(),
        deposit.getIndex().toBytes().toQuantityHexString());
  }

  /**
   * To deposit deposit.
   *
   * @return the deposit
   */
  public Deposit toDeposit() {
    return new Deposit(
        BLSPublicKey.fromHexString(pubkey),
        Bytes32.fromHexString(withdrawalCredentials),
        GWei.fromHexString(amount),
        BLSSignature.fromHexString(signature),
        UInt64.fromHexString(index));
  }

  /**
   * As json object json object.
   *
   * @return the json object
   */
  public JsonObject asJsonObject() {
    return new JsonObject()
        .put("pubkey", pubkey)
        .put("withdrawalCredentials", withdrawalCredentials)
        .put("amount", amount)
        .put("signature", signature)
        .put("index", index);
  }

  /**
   * Gets pubkey.
   *
   * @return the pubkey
   */
  @JsonGetter
  public String getPubkey() {
    return pubkey;
  }

  /**
   * Gets withdrawal credentials.
   *
   * @return the withdrawal credentials
   */
  @JsonGetter
  public String getWithdrawalCredentials() {
    return withdrawalCredentials;
  }

  /**
   * Gets amount.
   *
   * @return the amount
   */
  @JsonGetter
  public String getAmount() {
    return amount;
  }

  /**
   * Gets signature.
   *
   * @return the signature
   */
  @JsonGetter
  public String getSignature() {
    return signature;
  }

  /**
   * Gets index.
   *
   * @return the index
   */
  @JsonGetter
  public String getIndex() {
    return index;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final DepositParameter that = (DepositParameter) o;
    return Objects.equals(pubkey, that.pubkey)
        && Objects.equals(withdrawalCredentials, that.withdrawalCredentials)
        && Objects.equals(amount, that.amount)
        && Objects.equals(signature, that.signature)
        && Objects.equals(index, that.index);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pubkey, withdrawalCredentials, amount, signature, index);
  }

  @Override
  public String toString() {
    return "DepositParameter{"
        + "pubKey='"
        + pubkey
        + '\''
        + ", withdrawalCredentials='"
        + withdrawalCredentials
        + '\''
        + ", amount='"
        + amount
        + '\''
        + ", signature='"
        + signature
        + '\''
        + ", index='"
        + index
        + '\''
        + '}';
  }
}
