/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.core;

import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.math.BigInteger;
import javax.annotation.concurrent.Immutable;

import com.google.common.primitives.Longs;

/** A particular quantity of Gas as used by the Ethereum VM. */
@Immutable
public final class Gas {

  private final long value;

  public static final Gas ZERO = of(0);

  public static final Gas MAX_VALUE = Gas.of(Long.MAX_VALUE);

  private static final BigInteger MAX_VALUE_BIGINT = BigInteger.valueOf(Long.MAX_VALUE);

  protected Gas(final long value) {
    this.value = value;
  }

  public static Gas of(final long value) {
    return new Gas(value);
  }

  public static Gas of(final BigInteger value) {
    if (value.compareTo(MAX_VALUE_BIGINT) > 0) {
      return MAX_VALUE;
    }

    return of(value.longValue());
  }

  public static Gas of(final UInt256 value) {
    if (value.fitsLong()) {
      return of(value.toLong());
    } else {
      return MAX_VALUE;
    }
  }

  public static Gas of(final Bytes32 value) {
    return Gas.of(UInt256.wrap(value));
  }

  public static Gas fromHexString(final String str) {
    try {
      final long value = Long.decode(str);
      return Gas.of(value);
    } catch (final NumberFormatException e) {
      return MAX_VALUE;
    }
  }

  /**
   * The price of this amount of gas given the provided price per unit of gas.
   *
   * @param gasPrice The price per unit of gas.
   * @return The price of this amount of gas for a per unit of gas price of {@code gasPrice}.
   */
  public Wei priceFor(final Wei gasPrice) {
    return gasPrice.times(Wei.of(value));
  }

  public Gas max(final Gas other) {
    return of(Long.max(value, other.value));
  }

  public Gas min(final Gas other) {
    return of(Long.min(value, other.value));
  }

  public Gas dividedBy(final long other) {
    return Gas.of(value / other);
  }

  public Gas plus(final Gas amount) {
    try {
      return of(Math.addExact(value, amount.value));
    } catch (final ArithmeticException e) {
      return MAX_VALUE;
    }
  }

  public Gas minus(final Gas amount) {
    return of(value - amount.value);
  }

  public Gas times(final Gas amount) {
    try {
      return of(Math.multiplyExact(value, amount.value));
    } catch (final ArithmeticException e) {
      return MAX_VALUE;
    }
  }

  public Gas times(final long amount) {
    return times(Gas.of(amount));
  }

  public UInt256 asUInt256() {
    return UInt256.of(value);
  }

  public int compareTo(final Gas other) {
    return Long.compare(value, other.value);
  }

  public byte[] getBytes() {
    return Longs.toByteArray(value);
  }

  public long toLong() {
    return value;
  }

  @Override
  public int hashCode() {
    return Longs.hashCode(value);
  }

  @Override
  public boolean equals(final Object obj) {
    if (!(obj instanceof Gas)) {
      return false;
    }
    final Gas other = (Gas) obj;
    return value == other.value;
  }

  @Override
  public String toString() {
    return Long.toString(value);
  }

  public String toHexString() {
    return String.format("0x%s", Long.toHexString(value));
  }
}
