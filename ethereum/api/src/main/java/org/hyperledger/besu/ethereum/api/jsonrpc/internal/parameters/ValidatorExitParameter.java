/*
 * Copyright contributors to Hyperledger Besu
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
import org.hyperledger.besu.ethereum.core.ValidatorExit;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.json.JsonObject;

public class ValidatorExitParameter {

  private final String sourceAddress;
  private final String validatorPubKey;

  @JsonCreator
  public ValidatorExitParameter(
      @JsonProperty("sourceAddress") final String sourceAddress,
      @JsonProperty("pubkey") final String validatorPubKey) {
    this.sourceAddress = sourceAddress;
    this.validatorPubKey = validatorPubKey;
  }

  public static ValidatorExitParameter fromValidatorExit(final ValidatorExit exit) {
    return new ValidatorExitParameter(
        exit.getSourceAddress().toHexString(), exit.getValidatorPubKey().toHexString());
  }

  public ValidatorExit toValidatorExit() {
    return new ValidatorExit(
        Address.fromHexString(sourceAddress), BLSPublicKey.fromHexString(validatorPubKey));
  }

  public JsonObject asJsonObject() {
    return new JsonObject()
        .put("sourceAddress", sourceAddress)
        .put("validatorPubKey", validatorPubKey);
  }

  @JsonGetter
  public String getSourceAddress() {
    return sourceAddress;
  }

  @JsonGetter
  public String getValidatorPubKey() {
    return validatorPubKey;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final ValidatorExitParameter that = (ValidatorExitParameter) o;
    return Objects.equals(sourceAddress, that.sourceAddress)
        && Objects.equals(validatorPubKey, that.validatorPubKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceAddress, validatorPubKey);
  }

  @Override
  public String toString() {
    return "DepositParameter{"
        + "sourceAddress='"
        + sourceAddress
        + '\''
        + ", validatorPubKey='"
        + validatorPubKey
        + '\''
        + '}';
  }
}
