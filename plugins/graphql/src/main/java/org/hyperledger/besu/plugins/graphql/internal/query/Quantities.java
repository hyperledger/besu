/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.plugins.graphql.internal.query;

import org.hyperledger.besu.datatypes.Quantity;
import org.hyperledger.besu.datatypes.Wei;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Converts the {@link Quantity} values of the plugin API to the concrete types that the GraphQL
 * scalars serialize. Objects obtained from Besu are already of those types, so they are returned as
 * they are.
 */
public final class Quantities {

  private Quantities() {}

  /**
   * Converts a quantity to Wei.
   *
   * @param quantity the quantity
   * @return the quantity as Wei
   */
  public static Wei toWei(final Quantity quantity) {
    if (quantity instanceof Wei wei) {
      return wei;
    }
    return Wei.of(quantity.getAsBigInteger());
  }

  /**
   * Converts an optional quantity to optional Wei.
   *
   * @param quantity the optional quantity
   * @return the optional quantity as Wei
   */
  public static Optional<Wei> toWei(final Optional<? extends Quantity> quantity) {
    return quantity.map(Quantities::toWei);
  }

  /**
   * Converts a quantity to bytes, which the BigInt scalar serializes as a short hex string.
   *
   * @param quantity the quantity
   * @return the quantity as bytes
   */
  public static Bytes toBytes(final Quantity quantity) {
    if (quantity instanceof Bytes bytes) {
      return bytes;
    }
    return UInt256.valueOf(quantity.getAsBigInteger());
  }
}
