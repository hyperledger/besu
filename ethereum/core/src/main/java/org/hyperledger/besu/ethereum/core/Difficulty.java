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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.datatypes.Quantity;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.BaseUInt256Value;
import org.apache.tuweni.units.bigints.UInt256;

/** A particular quantity of difficulty, the block difficulty to create new blocks. */
public final class Difficulty extends BaseUInt256Value<Difficulty> implements Quantity {

  public static final Difficulty ZERO = of(0);

  public static final Difficulty ONE = of(1);

  public static final Difficulty MAX_VALUE = wrap(Bytes32.ZERO.not());

  Difficulty(final UInt256 value) {
    super(value, Difficulty::new);
  }

  private Difficulty(final long v) {
    this(UInt256.valueOf(v));
  }

  private Difficulty(final BigInteger v) {
    this(UInt256.valueOf(v));
  }

  private Difficulty(final String hexString) {
    this(UInt256.fromHexString(hexString));
  }

  public static Difficulty of(final long value) {
    return new Difficulty(value);
  }

  public static Difficulty of(final BigInteger value) {
    return new Difficulty(value);
  }

  public static Difficulty of(final UInt256 value) {
    return new Difficulty(value);
  }

  public static Difficulty wrap(final Bytes32 value) {
    return new Difficulty(UInt256.fromBytes(value));
  }

  public static Difficulty fromHexString(final String str) {
    return new Difficulty(str);
  }

  public static Difficulty fromHexOrDecimalString(final String str) {
    return str.startsWith("0x") ? new Difficulty(str) : new Difficulty(new BigInteger(str));
  }

  @Override
  public Number getValue() {
    return getAsBigInteger();
  }

  @Override
  public BigInteger getAsBigInteger() {
    return toBigInteger();
  }

  public Bytes32 getAsBytes32() {
    return this;
  }

  @Override
  public String toHexString() {
    return super.toHexString();
  }

  @Override
  public String toShortHexString() {
    return super.isZero() ? "0x0" : super.toShortHexString();
  }

  @Override
  public Difficulty copy() {
    return super.copy();
  }
}
