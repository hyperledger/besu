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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.datatypes.PublicKey;
import org.hyperledger.besu.ethereum.core.encoding.ValidatorExitDecoder;
import org.hyperledger.besu.ethereum.core.encoding.ValidatorExitEncoder;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;

public class ValidatorExit implements org.hyperledger.besu.plugin.data.ValidatorExit {

  private final Address sourceAddress;
  private final BLSPublicKey validatorPubKey;

  public ValidatorExit(final Address sourceAddress, final BLSPublicKey validatorPubKey) {
    this.sourceAddress = sourceAddress;
    this.validatorPubKey = validatorPubKey;
  }

  public static ValidatorExit readFrom(final Bytes rlpBytes) {
    return readFrom(RLP.input(rlpBytes));
  }

  public static ValidatorExit readFrom(final RLPInput rlpInput) {
    return ValidatorExitDecoder.decode(rlpInput);
  }

  public void writeTo(final RLPOutput out) {
    ValidatorExitEncoder.encode(this, out);
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
  public String toString() {
    return "ValidatorExit{"
        + "sourceAddress="
        + sourceAddress
        + "validatorPubKey="
        + validatorPubKey
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
    final ValidatorExit that = (ValidatorExit) o;
    return Objects.equals(sourceAddress, that.sourceAddress)
        && Objects.equals(validatorPubKey, that.validatorPubKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceAddress, validatorPubKey);
  }
}
