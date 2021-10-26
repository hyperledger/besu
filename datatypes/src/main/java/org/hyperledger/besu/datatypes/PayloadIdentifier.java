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
package org.hyperledger.besu.datatypes;

import org.hyperledger.besu.plugin.data.Quantity;

import java.math.BigInteger;
import java.util.Random;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.tuweni.units.bigints.UInt64;

public class PayloadIdentifier implements Quantity {

  private final UInt64 val;
  static final Random rand = new Random();

  @JsonCreator
  public PayloadIdentifier(final String payloadId) {
    this(Long.decode(payloadId));
  }

  public PayloadIdentifier(final Long payloadId) {
    this.val = UInt64.valueOf(payloadId);
  }

  public static PayloadIdentifier random() {
    long lng = rand.nextLong();
    // address corner case of nextLong returning a negative value
    lng = (lng == Long.MIN_VALUE) ? 0 : Math.abs(lng);
    return new PayloadIdentifier(lng);
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
    return val.toShortHexString();
  }

  @JsonValue
  public String serialize() {
    return val.toShortHexString();
  }

  @Override
  public boolean equals(final Object o) {
    if (o instanceof PayloadIdentifier) {
      return getValue().equals(((PayloadIdentifier) o).getValue());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return val.hashCode();
  }
}
