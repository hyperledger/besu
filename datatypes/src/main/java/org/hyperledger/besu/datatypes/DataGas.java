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

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.BaseUInt64Value;
import org.apache.tuweni.units.bigints.UInt64;

/** A particular quantity of DataGas */
public final class DataGas extends BaseUInt64Value<DataGas> implements Quantity {

  /** The constant ZERO. */
  public static final DataGas ZERO = of(0);

  /** The constant ONE. */
  public static final DataGas ONE = of(1);

  /** The constant MAX_DATA_GAS. */
  public static final DataGas MAX_DATA_GAS = of(UInt64.MAX_VALUE);

  /**
   * Instantiates a new DataGas.
   *
   * @param value the value
   */
  DataGas(final UInt64 value) {
    super(value, DataGas::new);
  }

  private DataGas(final long v) {
    this(UInt64.valueOf(v));
  }

  private DataGas(final BigInteger v) {
    this(UInt64.valueOf(v));
  }

  private DataGas(final String hexString) {
    this(UInt64.fromHexString(hexString));
  }

  /**
   * data gas of value.
   *
   * @param value the value
   * @return the data gas
   */
  public static DataGas of(final long value) {
    return new DataGas(value);
  }

  /**
   * data gas of value.
   *
   * @param value the value
   * @return the data gas
   */
  public static DataGas of(final BigInteger value) {
    return new DataGas(value);
  }

  /**
   * data gas of value.
   *
   * @param value the value
   * @return the data gas
   */
  public static DataGas of(final UInt64 value) {
    return new DataGas(value);
  }

  /**
   * data gas of value.
   *
   * @param value the value
   * @return the data gas
   */
  public static DataGas ofNumber(final Number value) {
    return new DataGas((BigInteger) value);
  }

  /**
   * Wrap data gas.
   *
   * @param value the value
   * @return the data gas
   */
  public static DataGas wrap(final Bytes value) {
    return new DataGas(UInt64.fromBytes(value));
  }

  /**
   * From hex string to data gas.
   *
   * @param str the str
   * @return the data gas
   */
  public static DataGas fromHexString(final String str) {
    return new DataGas(str);
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

  /**
   * From quantity to data gas.
   *
   * @param quantity the quantity
   * @return the data gas
   */
  public static DataGas fromQuantity(final Quantity quantity) {
    return DataGas.wrap((Bytes) quantity);
  }
}
