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
package org.hyperledger.besu.consensus.merge.blockcreation;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.data.Quantity;

import java.math.BigInteger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.tuweni.units.bigints.UInt64;

public class PayloadIdentifier implements Quantity {

  private final UInt64 val;

  @JsonCreator
  public PayloadIdentifier(final String payloadId) {
    this(Long.decode(payloadId));
  }

  public PayloadIdentifier(final Long payloadId) {
    this.val = UInt64.valueOf(Math.abs(payloadId));
  }

  public static PayloadIdentifier forPayloadParams(final Hash parentHash, final Long timestamp) {
    return new PayloadIdentifier(((long) parentHash.toHexString().hashCode()) ^ timestamp);
  }

  @Override
  public Number getValue() {
    return getAsBigInteger();
  }

  @Override
  public BigInteger getAsBigInteger() {
    return val.toBigInteger();
  }

  @Override
  public String toHexString() {
    return val.toHexString();
  }

  @Override
  public String toShortHexString() {
    var shortHex = val.toShortHexString();
    if (shortHex.length() % 2 != 0) {
      shortHex = "0x0" + shortHex.substring(2);
    }
    return shortHex;
  }

  @JsonValue
  public String serialize() {
    return toShortHexString();
  }

  @Override
  public boolean equals(final Object o) {
    if (o instanceof PayloadIdentifier) {
      return getAsBigInteger().equals(((PayloadIdentifier) o).getAsBigInteger());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return val.hashCode();
  }
}
