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
import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.BaseUInt256Value;
import org.apache.tuweni.units.bigints.UInt256;

/** A particular quantity of Wei, the Ethereum currency. */
public final class Wei extends BaseUInt256Value<Wei> implements Quantity {

  public static final Wei ZERO = of(0);

  public static final Wei ONE = of(1);

  public static final Wei MAX_WEI = of(UInt256.MAX_VALUE);

  Wei(final UInt256 value) {
    super(value, Wei::new);
  }

  private Wei(final long v) {
    this(UInt256.valueOf(v));
  }

  private Wei(final BigInteger v) {
    this(UInt256.valueOf(v));
  }

  private Wei(final String hexString) {
    this(UInt256.fromHexString(hexString));
  }

  public static Wei of(final long value) {
    return new Wei(value);
  }

  public static Wei of(final BigInteger value) {
    return new Wei(value);
  }

  public static Wei of(final UInt256 value) {
    return new Wei(value);
  }

  public static Wei ofNumber(final Number value) {
    return new Wei((BigInteger) value);
  }

  public static Wei wrap(final Bytes value) {
    return new Wei(UInt256.fromBytes(value));
  }

  public static Wei fromHexString(final String str) {
    return new Wei(str);
  }

  public static Wei fromEth(final long eth) {
    return Wei.of(BigInteger.valueOf(eth).multiply(BigInteger.TEN.pow(18)));
  }

  @Override
  public Number getValue() {
    return getAsBigInteger();
  }

  @Override
  public BigInteger getAsBigInteger() {
    return toBigInteger();
  }

  @Override
  public String toHexString() {
    return super.toHexString();
  }

  @Override
  public String toShortHexString() {
    return super.isZero() ? "0x0" : super.toShortHexString();
  }

  public static Wei fromQuantity(final Quantity quantity) {
    return Wei.wrap((Bytes) quantity);
  }

  public String toHumanReadableString() {
    final BigInteger amount = toBigInteger();
    final int numOfDigits = amount.toString().length();
    final Unit preferredUnit = Unit.getPreferred(numOfDigits);
    final double res = amount.doubleValue() / preferredUnit.divisor;
    return String.format("%1." + preferredUnit.decimals + "f %s", res, preferredUnit);
  }

  enum Unit {
    Wei(0, 0),
    KWei(3),
    MWei(6),
    GWei(9),
    Szabo(12),
    Finney(15),
    Ether(18),
    KEther(21),
    MEther(24),
    GEther(27),
    TEther(30);

    final int pow;
    final double divisor;
    final int decimals;

    Unit(final int pow) {
      this(pow, 2);
    }

    Unit(final int pow, final int decimals) {
      this.pow = pow;
      this.decimals = decimals;
      this.divisor = Math.pow(10, pow);
    }

    static Unit getPreferred(final int numOfDigits) {
      return Arrays.stream(values())
          .filter(u -> numOfDigits <= u.pow + 3)
          .findFirst()
          .orElse(TEther);
    }

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }
}
