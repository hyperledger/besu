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
package org.hyperledger.besu.datatypes;

import java.math.BigInteger;

/**
 * An interface to mark objects that also represents a discrete quantity, such as an unsigned
 * integer value.
 */
public interface Quantity {

  /**
   * Returns the numeric value of the quantity.
   *
   * <p>The specific class returned may be the boxed Java primitives, however plugin authors should
   * not rely on the underlying number always being castable to that primitive in all cases and
   * should instead rely on APIs such as {@link Number#longValue()} to cast to primitive values.
   * Similarly the underlying object based values may evolve over time.
   *
   * @return The boxed or object based value of the quantity.
   */
  @Deprecated
  Number getValue();

  /**
   * Gets Quantity as BigInteger.
   *
   * @return the Quantity as BigInteger
   */
  BigInteger getAsBigInteger();

  /**
   * The value as a hexadecimal string.
   *
   * @return This value represented as hexadecimal, starting with "0x".
   */
  String toHexString();

  /**
   * The value as a hexadecimal string with leading zeros truncated.
   *
   * @return This value represented as hexadecimal, starting with "0x".
   */
  String toShortHexString();
}
